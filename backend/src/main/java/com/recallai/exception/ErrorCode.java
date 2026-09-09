package com.recallai.exception;

import org.springframework.http.HttpStatus;

/** Stable, machine-readable error identifiers returned in every error response. */
public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    BAD_REQUEST(HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    CONFLICT(HttpStatus.CONFLICT),
    FILE_UPLOAD_ERROR(HttpStatus.UNPROCESSABLE_ENTITY),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    AI_ERROR(HttpStatus.BAD_GATEWAY),
    AI_INVALID_RESPONSE(HttpStatus.BAD_GATEWAY),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
