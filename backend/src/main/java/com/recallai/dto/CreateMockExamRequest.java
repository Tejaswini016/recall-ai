package com.recallai.dto;

import com.recallai.entity.ExamDifficulty;
import com.recallai.entity.ExamQuestionType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

/**
 * @param topic         build the exam from the user's cards on this topic (any deck)
 * @param deckId        or from one deck; when both are absent, from the user's most recent cards
 * @param questionTypes allowed types; all three when omitted
 */
public record CreateMockExamRequest(
        @Size(max = 150) String topic,
        Long deckId,
        @Size(max = 200) String title,
        @NotNull ExamDifficulty difficulty,
        @NotNull @Min(3) @Max(MAX_QUESTIONS) Integer questionCount,
        @NotNull @Min(5) @Max(180) Integer durationMinutes,
        Set<ExamQuestionType> questionTypes) {

    public static final int MAX_QUESTIONS = 30;
}
