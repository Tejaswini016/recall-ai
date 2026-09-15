package com.recallai.dto;

import java.time.Instant;
import java.util.List;

/** Full result of an attempt, including the correct answer and explanation for every question. */
public record QuizAttemptResponse(
        Long id,
        Long quizId,
        String quizTitle,
        int score,
        int totalQuestions,
        int percent,
        int correctCount,
        int incorrectCount,
        Instant completedAt,
        Integer durationSeconds,
        List<QuestionResult> results) {

    public record QuestionResult(
            Long questionId,
            String question,
            List<String> options,
            Integer selectedAnswer,
            int correctAnswer,
            boolean correct,
            String explanation,
            String topic) {
    }
}
