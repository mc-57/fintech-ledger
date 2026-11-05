package com.example.transfer.service;

import com.example.account.exception.AccountNotFoundException;
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
     * @Transactional keeps balance updates, transfer creation,
     * and idempotency recording in one database transaction.
     *
     * LIMITATIONS:
     * - Only completed transfers are recorded; no IN_PROGRESS state.
     * - Expired keys are rejected until safe reuse is implemented.
     * - Concurrent requests need atomic key reservation.
     * - Concurrent balance updates need database locking.
     */
    @Transactional
    public Transfer transfer(String idempotencyKey, TransferRequest request) {

        String fingerprint = generateFingerprint(request);

        Optional<TransferIdempotencyEntity> existing =
                idempotencyRepository.findByIdempotencyKey(idempotencyKey);

        if (existing.isPresent()) {
            TransferIdempotencyEntity record = existing.get();

            // Expiration does not automatically remove the database row.
            if (!Instant.now().isBefore(record.getExpiresAt())) {
                // TODO(TRF): Implement safe expiration cleanup and key reuse.
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

        Account sender = accountService.getAccountById(request.fromAccountId());
        Account recipient = accountService.getAccountById(request.toAccountId());

        validateTransfer(sender, recipient, request);

        updateBalances(sender, recipient, request.amount());

        Transfer transfer = createTransferRecord(request);

        // Save the key in the same transaction as the money movement.
        saveIdempotencyRecord(idempotencyKey, fingerprint, transfer.id());

        return transfer;
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
