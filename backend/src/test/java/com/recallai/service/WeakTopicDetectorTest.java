package com.recallai.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.recallai.config.AnalyticsProperties;
import org.junit.jupiter.api.Test;

class WeakTopicDetectorTest {

    private final WeakTopicDetector detector = new WeakTopicDetector(new AnalyticsProperties(3, 3.0, 10));

    @Test
    void weakWhenEnoughReviewsAndRecentAverageBelowThreshold() {
        assertThat(detector.isWeak(3, 2.99)).isTrue();
        assertThat(detector.isWeak(50, 0.0)).isTrue();
    }

    @Test
    void notWeakAtOrAboveThreshold() {
        assertThat(detector.isWeak(3, 3.0)).isFalse();
        assertThat(detector.isWeak(3, 4.5)).isFalse();
    }

    @Test
    void notWeakWithoutEnoughHistory() {
        assertThat(detector.isWeak(2, 0.0)).isFalse();
        assertThat(detector.isWeak(0, 0.0)).isFalse();
    }

    @Test
    void notWeakWithoutAnAverage() {
        assertThat(detector.isWeak(10, null)).isFalse();
    }

    @Test
    void thresholdsAreConfigurable() {
        WeakTopicDetector strict = new WeakTopicDetector(new AnalyticsProperties(1, 4.0, 5));

        assertThat(strict.isWeak(1, 3.9)).isTrue();
        assertThat(strict.recentWindow()).isEqualTo(5);
    }
}
