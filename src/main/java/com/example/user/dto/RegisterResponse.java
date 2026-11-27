package com.example.user.dto;

import com.example.user.model.UserRole;

import java.time.Instant;

public record RegisterResponse(
        Long id,
        String fullName,
        String email,
        UserRole role,
        Instant createdAt
) {}
