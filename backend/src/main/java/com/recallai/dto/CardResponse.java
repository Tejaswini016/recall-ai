package com.recallai.dto;

import com.recallai.entity.Card;
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
                card.getCreatedAt(),
                card.getUpdatedAt());
    }
}
