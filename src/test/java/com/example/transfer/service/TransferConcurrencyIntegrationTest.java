package com.example.transfer.service;

import com.example.account.entity.AccountEntity;
import com.example.account.repository.AccountRepository;
import com.example.transfer.dto.TransferRequest;
import com.example.transfer.entity.TransferIdempotencyEntity;
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
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
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

        long transfersBefore = transferRepository.count();

        ExecutorService executor = Executors.newFixedThreadPool(2);

        CountDownLatch readyGate = new CountDownLatch(2);
        CountDownLatch startGate = new CountDownLatch(1);

        try {
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

            // ACT: Release both workers.
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
            // Ensure waiting workers can exit if the test fails.
            startGate.countDown();
            executor.shutdownNow();
        }
    }
}
