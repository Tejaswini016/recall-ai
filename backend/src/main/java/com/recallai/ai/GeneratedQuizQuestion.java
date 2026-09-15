package com.recallai.ai;

import java.util.List;

/**
 * A validated multiple-choice question: exactly four options and a correct index 0-3.
 *
 * @param topic short sub-topic label, or null when the model gave none
 */
public record GeneratedQuizQuestion(
        String question,
        List<String> options,
        int correctAnswer,
        String explanation,
        String topic) {
}
