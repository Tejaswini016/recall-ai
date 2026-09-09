package com.recallai.repository;

/** Aggregated card counts for one deck, computed in a single SQL query. */
public interface DeckStats {

    Long getDeckId();

    long getCardCount();

    long getDueCount();

    long getMasteredCount();
}
