package com.recallai.ai;

/**
 * What the model is told about a mistake when asked for a corrective flashcard. Never includes
 * anything about the student beyond the answer they gave.
 */
public record MistakeContext(
        String question,
        String givenAnswer,
        String correctAnswer,
        String explanation,
        String topic) {
}
