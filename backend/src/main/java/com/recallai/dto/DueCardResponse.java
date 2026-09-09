package com.recallai.dto;

import com.recallai.entity.Card;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** A card in the review queue, with its deck name and how overdue it is. */
public record DueCardResponse(
        Long id,
        Long deckId,
        String deckName,
        String question,
        String answer,
        String explanation,
        String topic,
        List<String> tags,
        BigDecimal easeFactor,
        int interval,
        int repetitions,
        LocalDate dueDate,
        long daysOverdue) {

    /** Requires the card's deck to be loaded (the queue query fetch-joins it). */
    public static DueCardResponse from(Card card, LocalDate today) {
        long overdue = Math.max(0, ChronoUnit.DAYS.between(card.getDueDate(), today));
        return new DueCardResponse(
                card.getId(),
                card.getDeck().getId(),
                card.getDeck().getName(),
                card.getQuestion(),
                card.getAnswer(),
                card.getExplanation(),
                card.getTopic(),
                card.getTags(),
                card.getEaseFactor(),
                card.getIntervalDays(),
                card.getRepetitions(),
                card.getDueDate(),
                overdue);
    }
}
