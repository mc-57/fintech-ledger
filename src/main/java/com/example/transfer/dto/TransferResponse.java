package com.example.transfer.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record TransferResponse(
        Long id,
        Long fromAccountId,
        Long toAccountId,
        BigDecimal amount,
        Instant createdAt
) {}
