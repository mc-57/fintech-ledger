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

    // Concurrent deposits — no lost updates
    @Test
    void shouldPreventLostUpdatesDuringConcurrentDeposits() throws Exception {

        // ARRANGE: Create an account with an initial balance of 500.00.
        AccountEntity account = accountRepository.save(
                new AccountEntity("Test Account", new BigDecimal("500.00"))
        );

        Long accountId = account.getId();

        // Creates a thread pool with two worker threads.
        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Wait for both workers to signal readiness, then release them together.
        CountDownLatch readyGate = new CountDownLatch(2);
        CountDownLatch startGate = new CountDownLatch(1);

        try {
            // Worker 1: Deposit 200.00 into the shared account.
            Callable<Account> deposit200 = () -> {
                readyGate.countDown();
                startGate.await();
                return accountService.deposit(
                        accountId,
                        new DepositRequest(new BigDecimal("200.00"))
                );
            };

            // Worker 2: Deposit 300.00 into the same account.
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

            // ACT: Release both workers to perform concurrent deposits.
            startGate.countDown();

            // Wait for both deposits to finish; propagate any worker failures.
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);

            // ASSERT 1: The persisted balance reflects both deposits without lost updates.
            BigDecimal finalBalance = accountRepository.findById(accountId)
                    .orElseThrow()
                    .getBalance();

            assertEquals(
                    0,
                    finalBalance.compareTo(new BigDecimal("1000.00"))
            );

        } finally {
            // Release any waiting workers and shut down the executor.
            startGate.countDown();
            executor.shutdownNow();
        }
    }
}
