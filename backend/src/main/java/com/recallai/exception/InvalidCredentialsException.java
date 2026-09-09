package com.recallai.exception;

public class InvalidCredentialsException extends ApiException {

    public InvalidCredentialsException() {
        super(ErrorCode.UNAUTHORIZED, "Invalid email or password");
    }
}
