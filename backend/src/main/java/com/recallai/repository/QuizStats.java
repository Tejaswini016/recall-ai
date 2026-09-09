package com.recallai.repository;

/** Attempt aggregates for one quiz and one user. */
public interface QuizStats {

    Long getQuizId();

    long getAttemptCount();

    Double getBestPercent();
}
