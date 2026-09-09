package com.recallai.exception;

/**
 * Base class for expected, user-facing failures. The message is safe to return to the
 * client; anything not derived from this class is reported as an opaque 500.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;

    public ApiException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ApiException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }
}
