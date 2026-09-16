package com.recallai.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method that may call the model, so the per-user AI rate limit applies to
 * it even though it does not live under {@code /api/ai}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AiRateLimited {
}
