package com.example.transfer.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record TransferRequest(
        @NotNull(message = "Sender account ID is required")
        Long fromAccountId,
        @NotNull(message = "Recipient account ID is required")
        Long toAccountId,
        @Digits(integer = 17, fraction = 2,
                message = "Amount must have at most 17 integer digits and 2 decimal places")
        @NotNull(message = "Transfer amount is required")
        @Positive(message = "Transfer amount must be greater than zero")
        BigDecimal amount
) {}
