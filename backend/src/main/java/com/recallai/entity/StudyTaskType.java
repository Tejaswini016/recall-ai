package com.recallai.entity;

/** What a planned study block asks the student to do. */
public enum StudyTaskType {
    /** Clear the spaced-repetition queue. */
    REVIEW_DUE,
    /** Study one topic from cards and notes. */
    LEARN_TOPIC,
    /** Take a targeted quiz on one topic. */
    PRACTICE_QUIZ,
    /** Turn open mistakes into flashcards. */
    REVIEW_MISTAKES,
    /** Timed mock exam. */
    MOCK_EXAM,
    /** The day before the exam. */
    FINAL_REVISION
}
