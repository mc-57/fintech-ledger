package com.example.account.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record DepositRequest(

        @Digits(integer = 17, fraction = 2,
                message = "Amount must have at most 17 integer digits and 2 decimal places")
        @NotNull(message = "Deposit amount is required")
        @Positive(message = "Deposit amount must be greater than zero")
        BigDecimal amount
) {}
