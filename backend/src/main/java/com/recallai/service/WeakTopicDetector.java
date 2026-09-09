package com.recallai.service;

import com.recallai.config.AnalyticsProperties;
import org.springframework.stereotype.Component;

/**
 * The weak-topic rule, isolated so it can be read and tested in one place. A topic is weak
 * when the student has reviewed it enough times to judge and the average quality of their
 * most recent reviews is below the threshold. Using the recent window rather than the
 * all-time average means a topic stops being flagged once the student improves.
 *
 * <p>Deterministic by design: this is a business rule over review history, not a job for
 * a language model.
 */
@Component
public class WeakTopicDetector {

    private final AnalyticsProperties properties;

    public WeakTopicDetector(AnalyticsProperties properties) {
        this.properties = properties;
    }

    public boolean isWeak(long reviews, Double recentAverageQuality) {
        if (recentAverageQuality == null || reviews < properties.weakTopicMinReviews()) {
            return false;
        }
        return recentAverageQuality < properties.weakTopicQualityThreshold();
    }

    public int recentWindow() {
        return properties.weakTopicRecentWindow();
    }
}
