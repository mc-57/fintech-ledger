package com.example.transfer.model;

import java.math.BigDecimal;
import java.time.Instant;

// TODO: Persist transfer records in the database.
public record Transfer(
        Long id,
        Long fromAccountId,
        Long toAccountId,
        BigDecimal amount,
        Instant createdAt
) {}
