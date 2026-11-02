package com.example.account.model;

import java.math.BigDecimal;

public record Account(
        Long id,
        String ownerName,
        BigDecimal balance
) {}
