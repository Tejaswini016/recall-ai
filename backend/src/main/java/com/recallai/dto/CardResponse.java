package com.recallai.dto;

import com.recallai.entity.Card;
import com.recallai.entity.CardOrigin;
import com.recallai.scheduler.DifficultyTier;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record CardResponse(
        Long id,
        Long deckId,
        String question,
        String answer,
        String explanation,
        String topic,
        List<String> tags,
        BigDecimal easeFactor,
        int interval,
        int repetitions,
        LocalDate dueDate,
        DifficultyTier difficulty,
        int successStreak,
        int lapseCount,
        int totalReviews,
        CardOrigin origin,
        Long mistakeId,
        Instant createdAt,
        Instant updatedAt) {

    public static CardResponse from(Card card) {
        return new CardResponse(
                card.getId(),
                card.getDeck().getId(),
                card.getQuestion(),
                card.getAnswer(),
                card.getExplanation(),
                card.getTopic(),
                card.getTags(),
                card.getEaseFactor(),
                card.getIntervalDays(),
                card.getRepetitions(),
                card.getDueDate(),
                card.getDifficulty(),
                card.getSuccessStreak(),
                card.getLapseCount(),
                card.getTotalReviews(),
                card.getOrigin(),
                card.getMistakeId(),
                card.getCreatedAt(),
                card.getUpdatedAt());
    }
}
