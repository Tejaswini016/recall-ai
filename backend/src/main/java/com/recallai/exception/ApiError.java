package com.recallai.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

/**
 * The single error body shape for the whole API. {@code fieldErrors} is present only for
 * validation failures so clients can highlight individual form fields.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors) {

    public static ApiError of(ErrorCode code, String message, String path) {
        return new ApiError(Instant.now(), code.status().value(), code.name(), message, path, null);
    }

    public static ApiError validation(String message, String path, Map<String, String> fieldErrors) {
        ErrorCode code = ErrorCode.VALIDATION_ERROR;
        return new ApiError(Instant.now(), code.status().value(), code.name(), message, path, fieldErrors);
    }
}
