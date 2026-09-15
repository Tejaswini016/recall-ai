package com.recallai.repository;

import java.time.Instant;

/** Flashcard review performance aggregated per topic (cards without a topic count under their deck). */
public interface TopicReviewStats {

    String getTopic();

    long getCardCount();

    long getReviews();

    long getSuccessful();

    Instant getLastReviewedAt();
}
