package com.example.exception;

import java.util.List;
import java.util.Map;

public record ValidationApiError(
        int status,
        String error,
        String message,
        Map<String, List<String>> fieldErrors
) {}
