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
import java.util.Optional;

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
     * Executes a transfer with persistent idempotency.
     *
     * An identical retry returns the original transfer without
     * updating account balances again.
     *
     * @Transactional ensures balance updates, transfer creation,
     * and idempotency recording commit or roll back together.
     *
     * LIMITATIONS:
     * - Only completed transfers are recorded; no IN_PROGRESS state.
     * - Expired keys are rejected while their records still exist.
     * - Scheduled cleanup removes expired records every hour.
     * - After cleanup, a late retry may execute a new transfer because
     *   the original idempotency key is no longer stored.
     * - Concurrent requests need atomic idempotency key reservation.
     * - Concurrent balance updates still need database locking.
     *
     * TODO: Consider a persistent business-level operation ID to
     * detect duplicate transfers beyond the idempotency retention window.
     */
    @Transactional
    public Transfer transfer(String idempotencyKey, TransferRequest request) {

        String fingerprint = generateFingerprint(request);

        Optional<TransferIdempotencyEntity> existing =
                idempotencyRepository.findByIdempotencyKey(idempotencyKey);

        if (existing.isPresent()) {
            TransferIdempotencyEntity record = existing.get();

            // An expired record may remain until the scheduled cleanup runs.
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

            // Identical retry: no second transfer or balance update.
            return getTransferById(record.getTransferId());
        }

        List<Account> lockedAccounts = getLockedAccounts(
                request.fromAccountId(),
                request.toAccountId()
        );

        Account sender = lockedAccounts.get(0);
        Account recipient = lockedAccounts.get(1);

        validateTransfer(sender, recipient, request);

        /*
         * TEST ONLY — TRF-05: Reproduce concurrent transfer race conditions.
         * Uncomment only for local concurrency testing.
         *
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Transfer test interrupted", e);
        }
        */

        updateBalances(sender, recipient, request.amount());

        Transfer transfer = createTransferRecord(request);

        // Persist the idempotency record in the same database transaction.
        saveIdempotencyRecord(idempotencyKey, fingerprint, transfer.id());

        return transfer;
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

    /**
     * Stores an idempotency record linked to a completed transfer.
     * The record expires 24 hours after it is created.
     *
     * NOTE: Expiration does not automatically delete the database row.
     */
    private void saveIdempotencyRecord(
            String idempotencyKey,
            String fingerprint,
            Long transferId) {

        Instant now = Instant.now();
        Instant expiresAt = now.plus(IDEMPOTENCY_TTL);

        TransferIdempotencyEntity entity = new TransferIdempotencyEntity(
                idempotencyKey,
                fingerprint,
                transferId,
                now,
                expiresAt
        );

        idempotencyRepository.save(entity);
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
