package com.example.transfer.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Stores idempotency records for completed transfers.
 *
 * NOTE: This version does not represent IN_PROGRESS requests.
 * TODO: Introduce atomic key reservation for concurrent retries.
 */
@Entity
@Table(name = "transfer_idempotency")
public class TransferIdempotencyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false,
            unique = true, length = 128)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false,
            length = 64)
    private String requestFingerprint;

    @Column(name = "transfer_id")
    private Long transferId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected TransferIdempotencyEntity(){}

    public TransferIdempotencyEntity(
            String idempotencyKey,
            String requestFingerprint,
            Long transferId,
            Instant createdAt,
            Instant expiresAt
    ) {
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
        this.transferId = transferId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public Long getId() { return id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public Long getTransferId() { return transferId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void complete(Long transferId) { this.transferId = transferId; }

}
