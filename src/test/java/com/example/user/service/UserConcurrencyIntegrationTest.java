package com.example.user.service;

import com.example.user.dto.RegisterRequest;
import com.example.user.dto.RegisterResponse;
import com.example.user.exception.EmailAlreadyExistsException;
import com.example.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "app.security.username=test-admin",
        "app.security.password=test-password"
})
@Testcontainers
class UserConcurrencyIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16");

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldPreventConcurrentDuplicateRegistrations() throws Exception {

        // ARRANGE
        String email = UUID.randomUUID() + "@example.com";

        RegisterRequest firstRequest = new RegisterRequest(
                "Steve Harrington",
                email,
                "example-passphrase-2026"
        );

        RegisterRequest secondRequest = new RegisterRequest(
                "Dustin Henderson",
                email,
                "example-passphrase-2026"
        );

        long usersBefore = userRepository.count();

        ExecutorService executor = Executors.newFixedThreadPool(2);

        CountDownLatch readyGate = new CountDownLatch(2);
        CountDownLatch startGate = new CountDownLatch(1);

        try {
            Callable<RegisterResponse> firstTask = () -> {
                readyGate.countDown();
                startGate.await();

                return userService.register(firstRequest);
            };

            Callable<RegisterResponse> secondTask = () -> {
                readyGate.countDown();
                startGate.await();

                return userService.register(secondRequest);
            };

            // Submit both registration tasks.
            Future<RegisterResponse> first = executor.submit(firstTask);
            Future<RegisterResponse> second = executor.submit(secondTask);

            // Wait until both workers are ready.
            assertTrue(
                    readyGate.await(5, TimeUnit.SECONDS),
                    "Both workers should be reaedy before starting"
            );

            // ACT: Release both workers to register concurrently.
            startGate.countDown();

            RegisterResponse successfulRegistration = null;
            int successCount = 0;
            int conflictCount = 0;

            // Either worker may succeed.
            for (Future<RegisterResponse> future : List.of(first, second)) {
                try {
                    successfulRegistration =
                            future.get(10, TimeUnit.SECONDS);

                    successCount++;

                } catch (ExecutionException ex) {

                    // Verify the worker failed for the expected reason.
                    assertInstanceOf(
                            EmailAlreadyExistsException.class,
                            ex.getCause(),
                            "Worker should fail due to duplicate email"
                    );

                    conflictCount++;
                }
            }

            // ASSERT 1: Exactly one registration succeeds.
            assertEquals(1, successCount,
                    "Exactly one registration should succeed");

            // ASSERT 2: Exactly one registration is rejected.
            assertEquals(1, conflictCount,
                    "Exactly one registration should be rejected");

            assertNotNull(successfulRegistration);

            // ASSERT 3: Only one new user was persisted.
            assertEquals(
                    usersBefore + 1,
                    userRepository.count(),
                    "Only one new user should be persisted"
            );

            // ASSERT 4: Persisted user matches the successful response.
            var savedUser = userRepository.findByEmail(email)
                    .orElseThrow();

            assertEquals(
                    successfulRegistration.id(),
                    savedUser.getId(),
                    "Persisted user must match the winning registration"
            );

        } finally {
            // Release waiting workers and shut down the executor.
            startGate.countDown();
            executor.shutdownNow();
        }

    }

}
