package com.recallai.dto;

import java.time.LocalDate;

/** Cumulative number of cards that had reached the mastered interval by the end of {@code date}. */
public record MasteryPoint(LocalDate date, long masteredCards) {
}
