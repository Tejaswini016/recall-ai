package com.recallai.dto;

import com.recallai.entity.ExamDifficulty;
import com.recallai.entity.ExamQuestionType;
import com.recallai.entity.MistakeStatus;
import java.time.Instant;
import java.util.List;

/** Everything shown after submission: score, timing, per-topic breakdown and every question. */
public record MockExamResultResponse(
        Long id,
        String title,
        String topic,
        ExamDifficulty difficulty,
        int score,
        int totalQuestions,
        int percent,
        int correctCount,
        int incorrectCount,
        int skippedCount,
        int durationMinutes,
        Integer timeTakenSeconds,
        boolean timedOut,
        Instant startedAt,
        Instant submittedAt,
        List<TopicResult> topics,
        List<String> strongTopics,
        List<String> weakTopics,
        List<QuestionResult> questions) {

    public record TopicResult(String topic, int correct, int total, int percent, boolean strong) {
    }

    public record QuestionResult(
            Long id,
            int order,
            ExamQuestionType type,
            String question,
            List<String> options,
            String topic,
            Integer selectedOption,
            String answerText,
            Integer correctOption,
            String correctAnswer,
            List<String> acceptableAnswers,
            boolean correct,
            boolean skipped,
            String explanation,
            Long mistakeId,
            MistakeStatus mistakeStatus,
            Long mistakeCardId) {
    }
}
