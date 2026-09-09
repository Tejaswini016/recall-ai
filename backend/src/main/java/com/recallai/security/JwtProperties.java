package com.recallai.security;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Bound from {@code recallai.security.jwt.*}. The secret is mandatory, so the application
 * refuses to start rather than silently signing tokens with an empty key.
 */
@Validated
@ConfigurationProperties(prefix = "recallai.security.jwt")
public record JwtProperties(
        @NotBlank(message = "JWT_SECRET must be set") String secret,
        @Min(1) long expirationMinutes) {
}
