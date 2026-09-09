package com.recallai.security;

import com.recallai.exception.ApiException;
import com.recallai.exception.ErrorCode;

public class RateLimitExceededException extends ApiException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(int limit, long retryAfterSeconds) {
        super(ErrorCode.RATE_LIMITED, "You have used all " + limit + " AI generations for this hour; try again in "
                + humanize(retryAfterSeconds));
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    private static String humanize(long seconds) {
        long minutes = (seconds + 59) / 60;
        return minutes <= 1 ? "a minute" : minutes + " minutes";
    }
}
