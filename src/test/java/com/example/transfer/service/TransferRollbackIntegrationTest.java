package com.example.transfer.service;

import com.example.account.entity.AccountEntity;
import com.example.account.repository.AccountRepository;
import com.example.transfer.dto.TransferRequest;
import com.example.transfer.entity.TransferEntity;
import com.example.transfer.repository.TransferIdempotencyRepository;
import com.example.transfer.repository.TransferRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
class TransferRollbackIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres
            = new PostgreSQLContainer("postgres:16");

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransferIdempotencyRepository idempotencyRepository;

    @MockitoBean
    private TransferRepository transferRepository;

    @Test
    void shouldRollbackBalancesWhenTransferRecordingFails() {

        // ARRANGE: Create two real accounts in PostgreSQL.
        AccountEntity sender = accountRepository.save(
                new AccountEntity("Sender", new BigDecimal("500.00"))
        );

        AccountEntity recipient = accountRepository.save(
                new AccountEntity("Recipient", new BigDecimal("100.00"))
        );

        String key = UUID.randomUUID().toString();

        TransferRequest request = new TransferRequest(
                sender.getId(),
                recipient.getId(),
                new BigDecimal("200.00")
        );

        // Simulate a database failure when the transfer record is saved.
        // AccountRepository remains real; only TransferRepository is mocked.
        when(transferRepository.save(any(TransferEntity.class)))
                .thenThrow(new RuntimeException("Simulated transfer recording failure"));

        // ACT & ASSERT: Execute transfer and expect failure.
        // A failure must propagate from the transfer operation.
        assertThrows(
                RuntimeException.class,
                () -> transferService.transfer(key, request)
        );

        // ASSERT: Read database after the failed transaction.
        BigDecimal senderBalance = accountRepository.findById(sender.getId())
                .orElseThrow()
                .getBalance();

        BigDecimal recipientBalance = accountRepository.findById(recipient.getId())
                .orElseThrow()
                .getBalance();

        // ASSERT: Verify neither account should retain the attempted RM200 transfer.
        assertEquals(0, senderBalance.compareTo(new BigDecimal("500.00")));
        assertEquals(0, recipientBalance.compareTo(new BigDecimal("100.00")));

        // ASSERT: Verify no committed idempotency reservation.
        assertTrue(idempotencyRepository.findByIdempotencyKey(key).isEmpty());
    }
}
