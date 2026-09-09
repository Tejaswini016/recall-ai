package com.recallai.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Bound from {@code recallai.analytics.*}. Tunes weak-topic detection.
 *
 * @param weakTopicMinReviews       reviews a topic needs before it can be judged at all
 * @param weakTopicQualityThreshold a topic is weak when its recent average quality is below this
 * @param weakTopicRecentWindow     how many most-recent reviews per topic form the "recent" average
 */
@Validated
@ConfigurationProperties(prefix = "recallai.analytics")
public record AnalyticsProperties(
        @Min(1) int weakTopicMinReviews,
        @DecimalMin("0.0") @DecimalMax("5.0") double weakTopicQualityThreshold,
        @Min(1) int weakTopicRecentWindow) {
}
