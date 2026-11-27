package com.example.user.service;

import com.example.user.dto.RegisterRequest;
import com.example.user.dto.RegisterResponse;
import com.example.user.entity.UserEntity;
import com.example.user.exception.EmailAlreadyExistsException;
import com.example.user.exception.InvalidPasswordException;
import com.example.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {

        // Normalize email to ensure consistent identity.
        // TODO: Normalized email enforcement at the database level
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        // Early duplicate check for user-friendly error handling.
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException(
                    "Email is already registered"
            );
        }

        // BCrypt supports a maximum password length of 72 bytes
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new InvalidPasswordException(
                    "Password must not exceed 72 bytes"
            );
        }

        String passwordHash = passwordEncoder.encode(request.password());

        UserEntity user = new UserEntity(
                request.fullName().trim(),
                email,
                passwordHash
        );

        // Handles concurrent duplicate registrations
        try {
            // Force database synchronization so constraint violations
            // can be handled within this method.
            UserEntity savedUser = userRepository.saveAndFlush(user);

            return toResponse(savedUser);

        } catch (DataIntegrityViolationException ex) {

            if(isDuplicateEmailViolation(ex)) {
                throw new EmailAlreadyExistsException(
                        "Email is already registered"
                );
            }

            // Propagate unrelated database integrity violation.
            throw ex;
        }
    }

    private boolean isDuplicateEmailViolation(Throwable ex) {

        Throwable cause = ex;

        while (cause != null) {

            if (cause instanceof ConstraintViolationException violation) {
                return "users_email_key".equals(
                        violation.getConstraintName()
                );
            }

            cause = cause.getCause();
        }

        return false;
    }

    private RegisterResponse toResponse(UserEntity user){
        return new RegisterResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt()
        );
    }

}
