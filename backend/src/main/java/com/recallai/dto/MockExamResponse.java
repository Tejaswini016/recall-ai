package com.recallai.dto;

import com.recallai.entity.ExamDifficulty;
import com.recallai.entity.ExamQuestionType;
import com.recallai.entity.MockExamStatus;
import java.time.Instant;
import java.util.List;

/** An exam being taken: questions without answers, plus the clock. */
public record MockExamResponse(
        Long id,
        String title,
        String topic,
        Long deckId,
        ExamDifficulty difficulty,
        int durationMinutes,
        MockExamStatus status,
        Instant startedAt,
        Instant expiresAt,
        long remainingSeconds,
        int questionCount,
        List<Question> questions) {

    public record Question(Long id, int order, ExamQuestionType type, String question, List<String> options,
                           String topic) {
    }
}
