package com.recallai.repository;

import java.time.Instant;

/** Quiz-answer performance aggregated per topic for one user. */
public interface TopicQuizStats {

    String getTopic();

    long getAnswers();

    long getCorrect();

    Instant getLastAnsweredAt();
}
