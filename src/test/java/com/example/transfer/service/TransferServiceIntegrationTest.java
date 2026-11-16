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

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class TransferServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16");

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private TransferIdempotencyRepository idempotencyRepository;

    @Test
    void shouldLoadApplicationWithPostgreSQL() {}

    @Test
    void shouldPersistBothBalancesAfterSuccessfulTransfer() {

        // ARRANGE: Create two accounts in the test database.
        AccountEntity sender = accountRepository.save(
                new AccountEntity("Sender", new BigDecimal("500.00"))
        );

        AccountEntity recipient = accountRepository.save(
                new AccountEntity("Recipient", new BigDecimal("100.00"))
        );

        TransferRequest request = new TransferRequest(
                sender.getId(),
                recipient.getId(),
                new BigDecimal("200.00")
        );

        // ACT: Execute the real transfer service against PostgreSQL.
        Transfer result = transferService.transfer(
                UUID.randomUUID().toString(),
                request
        );

        // ASSERT: Read the persisted balance from PostgreSQL.
        BigDecimal senderBalance = accountRepository.findById(sender.getId())
                .orElseThrow()
                .getBalance();

        BigDecimal recipientBalance = accountRepository.findById(recipient.getId())
                .orElseThrow()
                .getBalance();

        assertEquals(0, senderBalance.compareTo(new BigDecimal("300.00")));
        assertEquals(0, recipientBalance.compareTo(new BigDecimal("300.00")));
        assertNotNull(result.id());
    }

    @Test
    void shouldPersistCompletedIdempotencyReservation() {

        // ARRANGE: Create two accounts in the test PostgreSQL database.
        AccountEntity sender = accountRepository.save(
                new AccountEntity("Sender", new BigDecimal("500.00"))
        );

        AccountEntity recipient = accountRepository.save(
                new AccountEntity("Recipient", BigDecimal.ZERO)
        );

        String key = UUID.randomUUID().toString();

        TransferRequest request = new TransferRequest(
                sender.getId(),
                recipient.getId(),
                new BigDecimal("200.00")
        );

        // ACT: Execute a real transfer, including idempotency reservation.
        Transfer result = transferService.transfer(key, request);

        // ASSERT: Read the completed reservation from PostgreSQL.
        TransferIdempotencyEntity reservation = idempotencyRepository
                .findByIdempotencyKey(key)
                .orElseThrow();

        // Verify the reservation references for the created transfer.
        assertNotNull(reservation.getTransferId());
        assertEquals(result.id(), reservation.getTransferId());

        // Verify that the referenced transfer actually exists in PostgreSQL.
        assertTrue(transferRepository.existsById(reservation.getTransferId()));
    }
}
