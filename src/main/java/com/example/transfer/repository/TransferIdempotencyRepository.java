package com.example.transfer.repository;

import com.example.transfer.entity.TransferIdempotencyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface TransferIdempotencyRepository
        extends JpaRepository<TransferIdempotencyEntity, Long> {

    Optional<TransferIdempotencyEntity> findByIdempotencyKey(String idempotencyKey);

    @Modifying
    @Query("""
    DELETE FROM TransferIdempotencyEntity r
    WHERE r.expiresAt <= :now
    """)
    int deleteExpiredRecords(@Param("now") Instant now);

    @Modifying
    @Query(value = """
        INSERT INTO transfer_idempotency
            (idempotency_key, request_fingerprint, created_at, expires_at)
        VALUES (:key, :fingerprint, :createdAt, :expiresAt)
        ON CONFLICT (idempotency_key) DO NOTHING
        """, nativeQuery = true) // the database checks uniqueness and inserts atomically.
    int reserveKey(
            @Param("key") String key,
            @Param("fingerprint") String fingerprint,
            @Param("createdAt") Instant createdAt,
            @Param("expiresAt") Instant expiresAt
    );
}
