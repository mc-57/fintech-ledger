package com.example.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank(message = "Full name is required")
        @Size(max = 100, message = "Full name must not exceed 100 characters")
        String fullName,

        @NotBlank(message = "Email address is required")
        @Email(message = "Please provide a valid email address")
        @Size(max = 255, message = "Email address must not exceed 255 characters")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 15, max = 64,
                message = "Password must be between 15 and 64 characters")
        String password
) {}
