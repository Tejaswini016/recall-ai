package com.recallai.exception;

public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(ErrorCode.CONFLICT, message);
    }
}
