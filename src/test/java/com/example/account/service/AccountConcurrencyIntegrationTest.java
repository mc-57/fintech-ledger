package com.example.account.service;

import com.example.account.dto.DepositRequest;
import com.example.account.entity.AccountEntity;
import com.example.account.model.Account;
import com.example.account.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class AccountConcurrencyIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16");

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    @Test
    void shouldPreventLostUpdatesDuringConcurrentDeposits() throws Exception {

        // ARRANGE
        AccountEntity account = accountRepository.save(
                new AccountEntity("Test Account", new BigDecimal("500.00"))
        );

        Long accountId = account.getId();

        // Creates a thread pool with 2 worker threads.
        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Both workers wait at the gate before starting their deposits.
        CountDownLatch readyGate = new CountDownLatch(2);
        CountDownLatch startGate = new CountDownLatch(1);

        try {
            Callable<Account> deposit200 = () -> {
                readyGate.countDown();
                startGate.await();
                return accountService.deposit(
                        accountId,
                        new DepositRequest(new BigDecimal("200.00"))
                );
            };

            Callable<Account> deposit300 = () -> {
                readyGate.countDown();
                startGate.await();
                return accountService.deposit(
                        accountId,
                        new DepositRequest(new BigDecimal("300.00"))
                );
            };

            // Submit both tasks. They will wait for startGate.
            Future<Account> first = executor.submit(deposit200);
            Future<Account> second = executor.submit(deposit300);

            // Wait until both workers are ready before releasing them.
            assertTrue(readyGate.await(5, TimeUnit.SECONDS));

            // ACT: Allow both deposits to start.
            startGate.countDown();

            // Wait for both operations to finish; propagate any failures.
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);

            // ASSERT: Read the final balance from real PostgreSQL.
            BigDecimal finalBalance = accountRepository.findById(accountId)
                    .orElseThrow()
                    .getBalance();

            assertEquals(
                    0,
                    finalBalance.compareTo(new BigDecimal("1000.00"))
            );

        } finally {
            startGate.countDown(); // Release waiting workers if setup fails.
            executor.shutdownNow();
        }
    }
}
