package com.example.transfer.repository;

import com.example.transfer.entity.TransferIdempotencyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TransferIdempotencyRepository
        extends JpaRepository<TransferIdempotencyEntity, Long> {

    Optional<TransferIdempotencyEntity> findByIdempotencyKey(String idempotencyKey);
}
