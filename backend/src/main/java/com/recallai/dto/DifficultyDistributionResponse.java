package com.recallai.dto;

import com.recallai.scheduler.DifficultyTier;
import java.util.List;

/**
 * How the user's cards are spread across difficulty tiers.
 *
 * @param averageResponseMs mean of the cards' running response-time averages, when any were recorded
 */
public record DifficultyDistributionResponse(
        long totalCards,
        List<Bucket> tiers,
        Integer averageResponseMs,
        long cardsWithLapses) {

    public record Bucket(DifficultyTier tier, long cards, int percent) {
    }
}
