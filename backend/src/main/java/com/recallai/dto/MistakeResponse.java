package com.recallai.dto;

import com.recallai.entity.Mistake;
import com.recallai.entity.MistakeSource;
import com.recallai.entity.MistakeStatus;
import java.time.Instant;

public record MistakeResponse(
        Long id,
        MistakeSource source,
        MistakeStatus status,
        String question,
        String givenAnswer,
        String correctAnswer,
        String explanation,
        String topic,
        Long deckId,
        String deckName,
        Long quizQuestionId,
        Long mockExamQuestionId,
        int occurrences,
        Long cardId,
        Instant firstMissedAt,
        Instant lastMissedAt,
        Instant resolvedAt) {

    public static MistakeResponse from(Mistake mistake) {
        return new MistakeResponse(
                mistake.getId(),
                mistake.getSource(),
                mistake.getStatus(),
                mistake.getQuestion(),
                mistake.getGivenAnswer(),
                mistake.getCorrectAnswer(),
                mistake.getExplanation(),
                mistake.getTopic(),
                mistake.getDeck() == null ? null : mistake.getDeck().getId(),
                mistake.getDeck() == null ? null : mistake.getDeck().getName(),
                mistake.getQuizQuestionId(),
                mistake.getMockExamQuestionId(),
                mistake.getOccurrences(),
                mistake.getCardId(),
                mistake.getFirstMissedAt(),
                mistake.getLastMissedAt(),
                mistake.getResolvedAt());
    }
}
