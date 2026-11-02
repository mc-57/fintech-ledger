package com.example.account.dto;

import java.math.BigDecimal;

public record AccountResponse(
        Long id,
        String ownerName,
        BigDecimal balance
) {}
