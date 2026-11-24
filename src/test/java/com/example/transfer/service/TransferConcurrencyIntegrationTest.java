package com.example.transfer.service;

import com.example.account.entity.AccountEntity;
import com.example.account.repository.AccountRepository;
import com.example.transfer.dto.TransferRequest;
import com.example.transfer.entity.TransferIdempotencyEntity;
import com.example.transfer.exception.IdempotencyConflictException;
import com.example.transfer.model.Transfer;
import com.example.transfer.repository.TransferIdempotencyRepository;
import com.example.transfer.repository.TransferRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

// Test-only credentials allow the full Spring context to initialize.
@SpringBootTest(properties = {
                "app.security.username=test-admin",
                "app.security.password=test-password"
})
@Testcontainers
class TransferConcurrencyIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16");

    @Autowired
    private TransferService transferService;

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransferIdempotencyRepository idempotencyRepository;

    // Same key, identical payloads — one transfer
    @Test
    void concurrentTransfersWithSameIdempotencyKey_ShouldOnlyTransferOnce() throws Exception {

        // ARRANGE
        AccountEntity sender = accountRepository.save(
                new AccountEntity("Sender", new BigDecimal("500.00"))
        );

        AccountEntity recipient = accountRepository.save(
                new AccountEntity("Recipient", BigDecimal.ZERO)
        );

        Long senderId = sender.getId();
        Long recipientId = recipient.getId();

        String idempotencyKey = "concurrent-transfer-" + UUID.randomUUID();

        TransferRequest request = new TransferRequest(
                senderId,
                recipientId,
                new BigDecimal("100.00")
        );

        // Record the baseline to verify how many new transfers are persisted.
        long transfersBefore = transferRepository.count();

        // Create two workers and coordinate their starting point.
        ExecutorService executor = Executors.newFixedThreadPool(2);

        CountDownLatch readyGate = new CountDownLatch(2);
        CountDownLatch startGate = new CountDownLatch(1);

        try {
            // Both workers submit the same key and payload.
            Callable<Transfer> transferTask = () -> {
                readyGate.countDown();
                startGate.await();

                return transferService.transfer(
                        idempotencyKey,
                        request
                );
            };

            // Submit both tasks.
            Future<Transfer> first = executor.submit(transferTask);
            Future<Transfer> second = executor.submit(transferTask);

            // Wait until both workers are ready.
            assertTrue(readyGate.await(5, TimeUnit.SECONDS));

            // ACT: Release both workers to attempt their transfers concurrently.
            startGate.countDown();

            // Wait for both transfer requests to finish.
            Transfer firstResult = first.get(10, TimeUnit.SECONDS);
            Transfer secondResult = second.get(10, TimeUnit.SECONDS);

            // ASSERT 1: Both return the same transfer.
            assertEquals(firstResult.id(), secondResult.id());

            // ASSERT 2: Only one new transfer was created.
            assertEquals(transfersBefore + 1,
                    transferRepository.count());

            // ASSERT 3: Sender was debited only once.
            BigDecimal senderBalance = accountRepository
                    .findById(senderId)
                    .orElseThrow()
                    .getBalance();

            assertEquals(
                    0,
                    senderBalance.compareTo(new BigDecimal("400.00"))
            );

            // ASSERT 4: Recipient was credited only once.
            BigDecimal recipientBalance = accountRepository
                    .findById(recipientId)
                    .orElseThrow()
                    .getBalance();

            assertEquals(
                    0,
                    recipientBalance.compareTo(new BigDecimal("100.00"))
            );

            // ASSERT 5: Idempotency reservation is completed.
            var record = idempotencyRepository
                    .findByIdempotencyKey(idempotencyKey)
                    .orElseThrow();

            assertEquals(firstResult.id(), record.getTransferId());

        } finally {

            // Release any waiting workers and shut down the executor.
            startGate.countDown();
            executor.shutdownNow();
        }
    }

    // Concurrent transfers — no lost updates
    @Test
    void shouldPreventLostUpdatesDuringConcurrentTransfers() throws Exception {

        // ARRANGE
        AccountEntity sender = accountRepository.save(
                new AccountEntity("Sender", new BigDecimal("500.00"))
        );

        AccountEntity recipient = accountRepository.save(
                new AccountEntity("Recipient", BigDecimal.ZERO)
        );

        Long senderId = sender.getId();
        Long recipientId = recipient.getId();

        String firstKey = UUID.randomUUID().toString();
        String secondKey = UUID.randomUUID().toString();

        TransferRequest firstRequest = new TransferRequest(
                senderId, recipientId, new BigDecimal("100.00")
        );

        TransferRequest secondRequest = new TransferRequest(
                senderId, recipientId, new BigDecimal("200.00")
        );

        // Record the baseline to verify how many new transfers are persisted.
        long transfersBefore = transferRepository.count();

        // Create two workers and coordinate their starting point.
        ExecutorService executor = Executors.newFixedThreadPool(2);

        CountDownLatch readyGate = new CountDownLatch(2);
        CountDownLatch startGate = new CountDownLatch(1);

        try {
            // Each worker submits a distinct transfer using its own key.
            Callable<Transfer> transferTaskOne = () -> {
                readyGate.countDown();
                startGate.await();

                return transferService.transfer(
                        firstKey,
                        firstRequest
                );
            };

            Callable<Transfer> transferTaskTwo = () -> {
                readyGate.countDown();
                startGate.await();

                return transferService.transfer(
                        secondKey,
                        secondRequest
                );
            };

            // Submit both tasks.
            Future<Transfer> first = executor.submit(transferTaskOne);
            Future<Transfer> second = executor.submit(transferTaskTwo);

            // Wait until both workers are ready.
            assertTrue(readyGate.await(5, TimeUnit.SECONDS));

            // ACT: Release both workers to attempt their transfers concurrently.
            startGate.countDown();

            // Wait for both transfer requests to finish.
            Transfer firstResult = first.get(10, TimeUnit.SECONDS);
            Transfer secondResult = second.get(10, TimeUnit.SECONDS);

            // ASSERT 1: Both transfers are distinct operations.
            assertNotEquals(firstResult.id(), secondResult.id(), "Concurrent transfers must have different transfer IDs");

            // ASSERT 2: Exactly two new transfer records were created.
            assertEquals(transfersBefore + 2,
                    transferRepository.count(),
                    "Exactly two new transfers should be persisted");

            // ASSERT 3: Sender balance reflects both deductions.
            BigDecimal senderBalance = accountRepository
                    .findById(senderId)
                    .orElseThrow()
                    .getBalance();

            assertEquals(
                    0,
                    senderBalance.compareTo(new BigDecimal("200.00"))
            );

            // ASSERT 4: Recipient balance reflects both credits.
            BigDecimal recipientBalance = accountRepository
                    .findById(recipientId)
                    .orElseThrow()
                    .getBalance();

            assertEquals(
                    0,
                    recipientBalance.compareTo(new BigDecimal("300.00"))
            );

        } finally {
            // Release any waiting workers and shut down the executor.
            startGate.countDown();
            executor.shutdownNow();
        }
    }

    // Same key, different payloads — one succeeds, one conflicts
    @Test
    void shouldRejectConcurrencyRequestsWithSameKeyButDifferentPayloads() throws Exception {

        // ARRANGE
        AccountEntity sender = accountRepository.save(
                new AccountEntity("Sender", new BigDecimal("500.00"))
        );

        AccountEntity recipient = accountRepository.save(
                new AccountEntity("Recipient", BigDecimal.ZERO)
        );

        Long senderId = sender.getId();
        Long recipientId = recipient.getId();

        String idempotencyKey = UUID.randomUUID().toString();

        TransferRequest firstRequest = new TransferRequest(
                senderId, recipientId, new BigDecimal("100.00")
        );

        TransferRequest secondRequest = new TransferRequest(
                senderId, recipientId, new BigDecimal("200.00")
        );

        // Record the baseline to verify how many new transfers are persisted.
        long transfersBefore = transferRepository.count();

        // Create two workers and coordinate their starting point.
        ExecutorService executor = Executors.newFixedThreadPool(2);

        CountDownLatch readyGate = new CountDownLatch(2);
        CountDownLatch startGate = new CountDownLatch(1);

        try {
            // Both workers compete for the same key using different payloads.
            Callable<Transfer> transferTaskOne = () -> {
                readyGate.countDown();
                startGate.await();

                return transferService.transfer(
                        idempotencyKey,
                        firstRequest
                );
            };

            Callable<Transfer> transferTaskTwo = () -> {
                readyGate.countDown();
                startGate.await();

                return transferService.transfer(
                        idempotencyKey,
                        secondRequest
                );
            };

            // Submit both tasks.
            Future<Transfer> first = executor.submit(transferTaskOne);
            Future<Transfer> second = executor.submit(transferTaskTwo);

            // Wait until both workers are ready.
            assertTrue(readyGate.await(5, TimeUnit.SECONDS));

            // ACT: Release both workers to attempt their transfers concurrently.
            startGate.countDown();

            Transfer successfulTransfer = null;
            int successCount = 0;
            int conflictCount = 0;

            // Either worker may win; collect both outcomes without assuming execution order.
            for(Future<Transfer> future: List.of(first, second)) {
                
                try {
                    successfulTransfer = future.get(10, TimeUnit.SECONDS);
                    successCount++;

                // ExecutionException can wrap many different exceptions.
                // hence the worker should fail for the correct reason, not just any reason.
                } catch (ExecutionException e) {
                    // ASSERT: Verify the original worker exception is the expected conflict.
                    assertInstanceOf(
                            IdempotencyConflictException.class, // Expected type
                            e.getCause(),                       // Actual exception
                            "Worker should fail due to an idempotency conflict"
                    );

                    conflictCount++;
                }
            }

            // ASSERT 1: Exactly one request succeeds and one conflicts.
            assertEquals(1, successCount,
                    "Exactly one transfer should succeed");

            assertEquals(1, conflictCount,
                    "Exactly one request should encounter a conflict");

            assertNotNull(successfulTransfer,
                    "A successful transfer must exist");

            // ASSERT 2: Only one new transfer was created.
            assertEquals(
                    transfersBefore + 1,
                    transferRepository.count(),
                    "Only one transfer should be persisted"
            );

            // Derive expected balances from the winning transfer, not the worker order.
            BigDecimal expectedSenderBalance = new BigDecimal("500.00").subtract(successfulTransfer.amount());

            BigDecimal expectedRecipientBalance = successfulTransfer.amount();

            // Read the committed balances from PostgreSQL.
            BigDecimal senderBalance = accountRepository
                    .findById(senderId)
                    .orElseThrow()
                    .getBalance();

            BigDecimal recipientBalance = accountRepository
                    .findById(recipientId)
                    .orElseThrow()
                    .getBalance();

            // ASSERT 3: Sender was debited only once.
            assertEquals(
                    0,
                    senderBalance.compareTo(expectedSenderBalance),
                    "Sender should only be debited once"
            );

            // ASSERT 4: Recipient was credited only once.
            assertEquals(
                    0,
                    recipientBalance.compareTo(expectedRecipientBalance),
                    "Recipient should only be credited once"
            );

            TransferIdempotencyEntity record = idempotencyRepository
                    .findByIdempotencyKey(idempotencyKey)
                    .orElseThrow();

            // ASSERT 5: Idempotency reservation references the winning transfer.
            assertEquals(
                    successfulTransfer.id(),
                    record.getTransferId(),
                    "Idempotency record must reference the winning transfer"
            );

        } finally {
            // Release any waiting workers and shut down the executor.
            startGate.countDown();
            executor.shutdownNow();
        }
    }
}
