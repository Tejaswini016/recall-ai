package com.recallai.repository;

import java.time.Instant;

/** Review performance aggregated per topic for one user. */
public interface TopicStats {

    String getTopic();

    long getCardCount();

    long getReviews();

    Double getAverageQuality();

    /** Average quality of the most recent N reviews of the topic. */
    Double getRecentAverageQuality();

    /** Fraction of reviews with quality >= 3, in 0..1. */
    Double getSuccessRate();

    Instant getLastReviewedAt();
}
