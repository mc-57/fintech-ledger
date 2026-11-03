package com.example.transfer.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record TransferRequest(
        @NotNull(message = "Sender account ID is required")
        Long fromAccountId,
        @NotNull(message = "Recipient account ID is required")
        Long toAccountId,
        @NotNull(message = "Transfer amount is required")
        @Positive(message = "Transfer amount must be greater than zero")
        BigDecimal amount
) {}
