package com.example.transfer.service;

import com.example.account.model.Account;
import com.example.account.service.AccountService;
import com.example.transfer.dto.TransferRequest;
import com.example.transfer.entity.TransferEntity;
import com.example.transfer.entity.TransferIdempotencyEntity;
import com.example.transfer.exception.IdempotencyConflictException;
import com.example.transfer.exception.InsufficientFundsException;
import com.example.transfer.exception.InvalidAccountTransferException;
import com.example.transfer.exception.TransferNotFoundException;
import com.example.transfer.model.Transfer;
import com.example.transfer.repository.TransferIdempotencyRepository;
import com.example.transfer.repository.TransferRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/**
 * Handles money transfers between ledger accounts.
 * Uses AccountService for account access and balance updates,
 * and TransferRepository for persistent transfer records.
 */
@Service
public class TransferService {

    private final AccountService accountService;
    private final TransferRepository transferRepository;
    private final TransferIdempotencyRepository idempotencyRepository;

    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    public TransferService(
            AccountService accountService,
            TransferRepository transferRepository,
            TransferIdempotencyRepository idempotencyRepository) {

        this.accountService = accountService;
        this.transferRepository = transferRepository;
        this.idempotencyRepository = idempotencyRepository;
    }

    /**
     * Executes a transfer with persistent, concurrency-safe idempotency.
     *
     * Atomically reserves the idempotency key before transferring funds.
     * An identical retry returns the original transfer without changing balances.
     *
     * @Transactional ensures reservation, balance updates, transfer creation,
     * and idempotency recording commit or roll back together.
     *
     * LIMITATIONS:
     * - Expired keys are rejected until scheduled cleanup removes them.
     * - A retry after cleanup may create a new transfer.
     * - Competing requests may wait for the database transaction to finish.
     *
     * TODO: Consider a persistent business-level operation ID to detect duplicate transfers beyond the idempotency retention window.
     * TODO: Add automated concurrency integration tests.
     */
    @Transactional
    public Transfer transfer(String idempotencyKey, TransferRequest request) {

        String fingerprint = generateFingerprint(request);
        Instant now = Instant.now();

        // Atomically claim the key using PostgreSQL's UNIQUE constraint.
        // 1 = reserved successfully; 0 = key already exists.
        int reserved = idempotencyRepository.reserveKey(
                idempotencyKey,
                fingerprint,
                now,
                now.plus(IDEMPOTENCY_TTL)
        );

        if(reserved == 0) {
           return handleExistingIdempotencyKey(idempotencyKey, fingerprint);
        }

        List<Account> lockedAccounts = getLockedAccounts(
                request.fromAccountId(),
                request.toAccountId()
        );

        Account sender = lockedAccounts.get(0);
        Account recipient = lockedAccounts.get(1);

        validateTransfer(sender, recipient, request);

        updateBalances(sender, recipient, request.amount());

        Transfer transfer = createTransferRecord(request);

        // Complete the existing reservation instead of inserting another record.
        completeIdempotencyReservation(idempotencyKey, transfer.id());

        return transfer;
    }

    /**
     *
     * Handles retries when the idempotency key is already reserved.
     */
    private Transfer handleExistingIdempotencyKey(
            String idempotencyKey,
            String fingerprint) {

        TransferIdempotencyEntity record = idempotencyRepository
                .findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new IllegalStateException(
                        "Existing idempotency reservation could not be found"
                ));

        if (!Instant.now().isBefore(record.getExpiresAt())) {
            throw new IdempotencyConflictException(
                    "Idempotency key has expired and cannot currently be reused"
            );
        }

        if (!record.getRequestFingerprint().equals(fingerprint)) {
            throw new IdempotencyConflictException(
                    "Idempotency key already used with different request data"
            );
        }

        // A committed reservation should reference a completed transfer.
        if (record.getTransferId() == null) {
            throw new IllegalStateException(
                    "Idempotency record has no completed transfer"
            );
        }

        return getTransferById(record.getTransferId());
    }

    /**
     * Associates the completed transfer with its reserved idempotency key.
     *
     * The entity is managed by JPA, so dirty checking persists the
     * transfer ID when the transaction commits.
     */
    private void completeIdempotencyReservation(
            String idempotencyKey,
            Long transferId) {

        TransferIdempotencyEntity record = idempotencyRepository.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new IllegalStateException(
                        "Reserved idempotency key not found"
                )); // defensive check for unexpected state that breaks the reservation flow.

        record.complete(transferId);
        // JPA dirty checking persists this change at transaction commit.
    }

    /**
     * Locks both accounts in asc ID order to reduce deadlock risk.
     *
     * NOTE: Locks are held until the enclosing transaction finishes.
     * TODO(TRF): Add lock timeouts and concurrency integration tests.
     */
    private List<Account> getLockedAccounts(
            Long fromAccountId,
            Long toAccountId) {

        Long firstId = Math.min(fromAccountId, toAccountId);
        Long secondId = Math.max(fromAccountId, toAccountId);

        Account first = accountService.getAccountByIdForUpdate(firstId);
        Account second = accountService.getAccountByIdForUpdate(secondId);

        // Return accounts in their original sender/recipient order.
        return fromAccountId.equals(firstId)
                ? List.of(first, second)
                : List.of(second, first);
    }

    private void validateTransfer(Account sender, Account recipient, TransferRequest request) {

        if (sender.id().equals(recipient.id())) {
            throw new InvalidAccountTransferException(
                    "Sender and recipient accounts must be different"
            );
        }

        if (sender.balance().compareTo(request.amount()) < 0) {
            throw new InsufficientFundsException(
                    "Account " + sender.id() + " has insufficient funds"
            );
        }
    }

    private void updateBalances(Account sender, Account recipient, BigDecimal amount){

        BigDecimal senderBalance = sender.balance().subtract(amount);
        BigDecimal recipientBalance = recipient.balance().add(amount);

        Account updatedSender = new Account(
                sender.id(),
                sender.ownerName(),
                senderBalance
        );

        Account updatedRecipient = new Account(
                recipient.id(),
                recipient.ownerName(),
                recipientBalance
        );

        accountService.updateAccount(updatedSender);
        accountService.updateAccount(updatedRecipient);
    }

    private Transfer createTransferRecord(TransferRequest request){

        TransferEntity entity = new TransferEntity(
                request.fromAccountId(),
                request.toAccountId(),
                request.amount(),
                Instant.now()
        );

        TransferEntity savedTransfer = transferRepository.save(entity);

        return toDomain(savedTransfer);
    }

    public Transfer getTransferById(Long id){

        TransferEntity entity = transferRepository.findById(id)
                .orElseThrow(() -> new TransferNotFoundException(id));

        return toDomain(entity);
    }

    public List<Transfer> getAllTransfers(){

        return transferRepository.findAll()
                .stream()
                .map(this::toDomain)
                .sorted(Comparator.comparing(Transfer::id))
                .toList();
    }

    private Transfer toDomain(TransferEntity entity) {
        return new Transfer(
                entity.getId(),
                entity.getFromAccountId(),
                entity.getToAccountId(),
                entity.getAmount(),
                entity.getCreatedAt()
        );
    }

    /**
     * Generates a SHA-256 fingerprint to identify equivalent transfer requests.
     *
     * Includes sender, recipient, and amount because changing any of these
     * fields changes the intended transfer operation.
     *
     * Normalizes the amount so values like 100.0 and 100.00
     * produce the same fingerprint.
     */
    private String generateFingerprint(TransferRequest request) {

        String amount = request.amount()
                .stripTrailingZeros()
                .toPlainString();

        String normalized = request.fromAccountId()
                + "|" + request.toAccountId()
                + "|" + amount;

        return sha256(normalized);
    }

    private String sha256(String input){

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    input.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

}
