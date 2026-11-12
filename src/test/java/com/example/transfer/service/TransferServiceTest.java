package com.example.transfer.service;

import com.example.account.model.Account;
import com.example.account.service.AccountService;
import com.example.transfer.dto.TransferRequest;
import com.example.transfer.entity.TransferEntity;
import com.example.transfer.entity.TransferIdempotencyEntity;
import com.example.transfer.exception.IdempotencyConflictException;
import com.example.transfer.exception.InsufficientFundsException;
import com.example.transfer.exception.InvalidAccountTransferException;
import com.example.transfer.model.Transfer;
import com.example.transfer.repository.TransferIdempotencyRepository;
import com.example.transfer.repository.TransferRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    // Dependencies are mocked; TransferService itself runs its real business logic.
    @Mock
    private AccountService accountService;

    @Mock
    private TransferRepository transferRepository;

    @Mock
    private TransferIdempotencyRepository idempotencyRepository;

    @InjectMocks
    private TransferService transferService;

    @Test
    void shouldRejectTransferWhenFundsAreInsufficient() {

        // ARRANGE: Prepare the test scenario and mock dependencies.
        // Sender has RM100, but attempts to transfer RM200.
        Account sender = new Account(3L, "Sender", new BigDecimal("100.00"));
        Account recipient = new Account(4L, "Recipient", BigDecimal.ZERO);
        TransferRequest request = new TransferRequest(3L, 4L, new BigDecimal("200.00"));

        // Simulate a new idempotency key so the service proceeds with the transfer.
        when(idempotencyRepository.reserveKey(
                anyString(), anyString(), any(Instant.class), any(Instant.class)
        )).thenReturn(1);

        // Return controlled account data without accessing PostgreSQL.
        when(accountService.getAccountByIdForUpdate(3L)).thenReturn(sender);
        when(accountService.getAccountByIdForUpdate(4L)).thenReturn(recipient);

        // ACT + ASSERT: assertThrows invokes transfer() and checks the exception type.
        assertThrows(
                InsufficientFundsException.class,
                () -> transferService.transfer("test-key-001", request)
        );

        // ASSERT: Rejection must occur before any balance updates or transfer saves.
        // These verify calls, not actual database rollback (tested later).
        verify(accountService, never()).updateAccount(any(Account.class));
        verify(transferRepository, never()).save(any(TransferEntity.class));

    }

    @Test
    void shouldTransferFundsSuccessfully() {

        // ARRANGE: Prepare accounts and the transfer request.
        Account sender = new Account(3L, "Sender", new BigDecimal("500.00"));
        Account recipient = new Account(4L, "Recipient", new BigDecimal("100.00"));
        TransferRequest request = new TransferRequest(3L, 4L, new BigDecimal("200.00"));

        when(idempotencyRepository.reserveKey(
                anyString(), anyString(), any(Instant.class), any(Instant.class)
        )).thenReturn(1);

        when(accountService.getAccountByIdForUpdate(3L)).thenReturn(sender);
        when(accountService.getAccountByIdForUpdate(4L)).thenReturn(recipient);

        // Mock JPA save: assign a fake database ID so the service can continue.
        // thenAnswer returns the same entity that TransferService passed to save().
        when(transferRepository.save(any(TransferEntity.class)))
                .thenAnswer(invocation -> {
                    TransferEntity entity = invocation.getArgument(0);
                    ReflectionTestUtils.setField(entity, "id", 99L);
                    return entity;
                });

        // The reservation was created with a null transfer ID; completion should fill it.
        TransferIdempotencyEntity reservation =
                new TransferIdempotencyEntity(
                        "test-key-002",
                        "test-fingerprint",
                        null,
                        Instant.now(),
                        Instant.now().plusSeconds(86400)
                );

        when(idempotencyRepository.findByIdempotencyKey("test-key-002"))
                .thenReturn(Optional.of(reservation));

        // ACT: Execute transfer().
        Transfer result = transferService.transfer("test-key-002", request);

        // ASSERT: Verify the exact balances the service passes to AccountService.
        // argThat checks matching account ID AND calculated balance.
        // Sender: RM500 - RM200 = RM300; recipient: RM100 + RM200 = RM300.
        verify(accountService).updateAccount(argThat(account ->
                account.id().equals(3L)
                && account.balance().compareTo(new BigDecimal("300.00")) == 0
        ));

        verify(accountService).updateAccount(argThat(account ->
                account.id().equals(4L)
                && account.balance().compareTo(new BigDecimal("300.00")) == 0
        ));

        // A single transfer record was saved.
        verify(accountService, times(2)).updateAccount(any(Account.class));
        verify(transferRepository, times(1)).save(any(TransferEntity.class));

        // Verify the response ID AND the reserved key now reference transfer 99; link to a complete transfer.
        assertEquals(99L, result.id());
        assertEquals(99L, reservation.getTransferId());
    }

    @Test
    void shouldRejectTransferToSameAccount() {

        // ARRANGE: Sender and recipient have the same account ID.
        TransferRequest request = new TransferRequest(
                3L, 3L, new BigDecimal("100.00")
        );

        // ACT & ASSERT: Reject before attempting to reserve a key or lock accounts.
        assertThrows(
                InvalidAccountTransferException.class,
                () -> transferService.transfer("test-self-transfer", request)
        );

        // ASSERT: No reservation, balance update, or transfer creation.
        verifyNoInteractions(
                idempotencyRepository,
                accountService,
                transferRepository
        );
    }

    @Test
    void shouldRejectSameKeyWithDifferentRequest() {

        // ARRANGE: A completed reservation belongs to a different request.
        // Its stored fingerprint differs from the fingerprint calculated for this incoming request.
        String key = "test-key-conflict";

        TransferRequest request = new TransferRequest(
                3L, 4L, new BigDecimal("200.00")
        );

        TransferIdempotencyEntity existingRecord = new TransferIdempotencyEntity(
                key,
                "fingerprint-from-original-request",
                99L,
                Instant.now(),
                Instant.now().plusSeconds(86400)
        );

        // Simulate a conflict: the key already exists.
        // reserveKey() returns 0 when PostgreSQL skips an insert due to a conflict.
        when(idempotencyRepository.reserveKey(
                anyString(), anyString(),
                any(Instant.class), any(Instant.class)
        )).thenReturn(0);

        when(idempotencyRepository.findByIdempotencyKey(key))
                .thenReturn(Optional.of(existingRecord));

        // ACT & ASSERT: Different request data must be rejected.
        assertThrows(
                IdempotencyConflictException.class,
                () -> transferService.transfer(key, request)
        );

        // ASSERT: Reject before reading/updating accounts or saving a new transfer.
        verifyNoInteractions(accountService, transferRepository);
    }

    @Test
    void shouldReturnOriginalTransferForIdenticalRetry() throws NoSuchAlgorithmException {

        // ARRANGE: Simulate a previously completed transfer.
        String key = "test-key-replay";

        TransferRequest request = new TransferRequest(
                3L, 4L, new BigDecimal("200.00")
        );

        TransferEntity originalTransfer = new TransferEntity(
                3L, 4L, new BigDecimal("200.00"), Instant.now()
        );

        // Simulate the database-generated transfer ID.
        ReflectionTestUtils.setField(originalTransfer, "id", 99L);

        // Build the expected fingerprint independently from the canonical fields.
        // do not call the private production helper.
        String normalized = "3|4|200";
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        String fingerprint = HexFormat.of()
                .formatHex(digest.digest(normalized.getBytes(StandardCharsets.UTF_8)));

        TransferIdempotencyEntity existingRecord = new TransferIdempotencyEntity(
                key,
                fingerprint,
                99L,
                Instant.now(),
                Instant.now().plusSeconds(86400)
        );

        when(idempotencyRepository.reserveKey(
                anyString(), anyString(),
                any(Instant.class), any(Instant.class)
        )).thenReturn(0);

        when(idempotencyRepository.findByIdempotencyKey(key))
                .thenReturn(Optional.of(existingRecord));

        when(transferRepository.findById(99L))
                .thenReturn(Optional.of(originalTransfer));

        // ACT: Retry with the same key and payload.
        Transfer result = transferService.transfer(key, request);

        // ASSERT: The retry returns the original ID without touching balances
        // or creating a second transfer record.
        assertEquals(99L, result.id());
        verify(accountService, never()).updateAccount(any(Account.class));
        verify(transferRepository, never()).save(any(TransferEntity.class));
    }

}
