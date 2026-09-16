package com.recallai.repository;

/** Cards per difficulty tier for one user. */
public interface DifficultyCount {

    String getDifficulty();

    long getCards();
}
