package com.recallai.ai;

import com.recallai.exception.ApiException;
import com.recallai.exception.ErrorCode;

/** The model could not be called successfully. {@code retryable} guides the retry service. */
public class AiUnavailableException extends ApiException {

    private final boolean retryable;

    public AiUnavailableException(String message, boolean retryable) {
        super(ErrorCode.AI_ERROR, message);
        this.retryable = retryable;
    }

    public AiUnavailableException(String message, boolean retryable, Throwable cause) {
        super(ErrorCode.AI_ERROR, message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
