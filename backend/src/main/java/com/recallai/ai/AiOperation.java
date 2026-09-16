package com.recallai.ai;

/** The kinds of structured generation the application asks Claude for. Part of every cache key. */
public enum AiOperation {
    FLASHCARDS,
    QUIZ,
    /** One corrective flashcard built from a mistake. */
    MISTAKE_CARD,
    /** Summary and per-topic advice for a study plan. */
    STUDY_PLAN
}
