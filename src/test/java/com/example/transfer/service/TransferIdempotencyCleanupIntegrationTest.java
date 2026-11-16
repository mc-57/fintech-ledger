package com.example.transfer.service;

import com.example.account.entity.AccountEntity;
import com.example.account.repository.AccountRepository;
import com.example.transfer.entity.TransferEntity;
import com.example.transfer.entity.TransferIdempotencyEntity;
import com.example.transfer.repository.TransferIdempotencyRepository;
import com.example.transfer.repository.TransferRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@SpringBootTest
@Testcontainers
class TransferIdempotencyCleanupIntegrationTest {

    @Autowired
    private TransferIdempotencyCleanupJob cleanupJob;

    @Autowired
    private TransferIdempotencyRepository idempotencyRepository;

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16");

    @Test
    void shouldDeleteOnlyExpiredIdempotencyRecords() {

        // ARRANGE: Create two accounts for valid transfer records.
        AccountEntity sender = accountRepository.save(
                new AccountEntity("Sender", new BigDecimal("500.00"))
        );

        AccountEntity recipient = accountRepository.save(
                new AccountEntity("Recipient", BigDecimal.ZERO)
        );

        // Create real transfer records so the foreign keys are valid.
        TransferEntity expiredTransfer = transferRepository.save(
                new TransferEntity(
                        sender.getId(),
                        recipient.getId(),
                        new BigDecimal("100.00"),
                        Instant.now()
                )
        );

        TransferEntity validTransfer = transferRepository.save(
                new TransferEntity(
                        sender.getId(),
                        recipient.getId(),
                        new BigDecimal("200.00"),
                        Instant.now()
                )
        );

        Instant now = Instant.now();

        String expiredKey = UUID.randomUUID().toString();
        String validKey = UUID.randomUUID().toString();

        // Record A: Expired yesterday.
        idempotencyRepository.save(
                new TransferIdempotencyEntity(
                        expiredKey,
                        "expired-fingerprint",
                        expiredTransfer.getId(),
                        now.minus(Duration.ofDays(2)),
                        now.minus(Duration.ofDays(1))
                )
        );
        // Record B: Still valid until tomorrow.
        idempotencyRepository.save(
                new TransferIdempotencyEntity(
                        validKey,
                        "valid-fingerprint",
                        validTransfer.getId(),
                        now,
                        now.plus(Duration.ofDays(1))
                )
        );

        // ACT: Run the real cleanup method without waiting for @Scheduled.
        cleanupJob.cleanup();

        // ASSERT: Only the expired reservation should be deleted.
        assertTrue(idempotencyRepository.findByIdempotencyKey(expiredKey).isEmpty());
        assertTrue(idempotencyRepository.findByIdempotencyKey(validKey).isPresent());

        // ASSERT: Cleanup must not delete the underlying transfer history.
        assertTrue(transferRepository.existsById(expiredTransfer.getId()));
        assertTrue(transferRepository.existsById(validTransfer.getId()));

    }
}
