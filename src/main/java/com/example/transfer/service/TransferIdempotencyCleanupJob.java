package com.example.transfer.service;

import com.example.transfer.repository.TransferIdempotencyRepository;
import jakarta.transaction.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class TransferIdempotencyCleanupJob {

    private final TransferIdempotencyRepository repository;

    public TransferIdempotencyCleanupJob(
            TransferIdempotencyRepository repository) {
        this.repository = repository;
    }

    /**
     * Deletes expired idempotency records periodically.
     * Transfer history remains intact.
     *
     * NOTE: Fixed delay starts after the previous execution finshes.
     * TODO: Coordinate scheduling across multiple application instance.
     */
    @Transactional
    @Scheduled(fixedDelay = 3_600_000)
    public void cleanup() {
        int deleted = repository.deleteExpiredRecords(Instant.now());
        System.out.println("Expired idempotency records deleted: " + deleted);
    }
}
