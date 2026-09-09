package com.recallai.ai;

import java.util.List;

/** A validated multiple-choice question: exactly four options and a correct index 0-3. */
public record GeneratedQuizQuestion(
        String question,
        List<String> options,
        int correctAnswer,
        String explanation) {
}
