package com.recallai.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Bound from {@code recallai.review.*}.
 *
 * @param masteredIntervalDays a card whose SM-2 interval has reached this many days is
 *                             counted as "mastered" (21 days matches Anki's mature threshold)
 */
@Validated
@ConfigurationProperties(prefix = "recallai.review")
public record ReviewProperties(@Min(1) int masteredIntervalDays) {
}
