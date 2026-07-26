package com.biashara.common.web;

import java.time.LocalDateTime;
import java.util.Map;

/** Uniform error body, so the frontend has exactly one shape to handle. */
public record ApiError(
        boolean success,
        int status,
        String message,
        Map<String, String> fieldErrors,
        LocalDateTime timestamp) {

    public static ApiError of(int status, String message) {
        return new ApiError(false, status, message, null, LocalDateTime.now());
    }

    public static ApiError validation(int status, String message, Map<String, String> fieldErrors) {
        return new ApiError(false, status, message, fieldErrors, LocalDateTime.now());
    }
}
