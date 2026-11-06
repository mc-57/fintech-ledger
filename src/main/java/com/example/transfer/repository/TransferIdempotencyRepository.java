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
}
