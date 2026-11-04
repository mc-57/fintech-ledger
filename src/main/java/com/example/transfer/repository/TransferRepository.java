package com.example.transfer.repository;

import com.example.transfer.entity.TransferEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferRepository
        extends JpaRepository<TransferEntity, Long> {
}
