package com.recallai.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Bound from {@code recallai.material.*}.
 *
 * @param maxChars  largest extracted study text accepted for one generation request
 * @param maxChunks how many model-sized chunks one request may fan out to; bounds cost
 */
@Validated
@ConfigurationProperties(prefix = "recallai.material")
public record MaterialProperties(@Min(1000) int maxChars, @Min(1) int maxChunks) {
}
