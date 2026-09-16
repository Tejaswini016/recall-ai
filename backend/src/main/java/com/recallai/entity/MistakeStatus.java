package com.recallai.entity;

/** Lifecycle of a mistake: waiting for review, turned into a flashcard, or set aside. */
public enum MistakeStatus {
    OPEN,
    CONVERTED,
    DISMISSED
}
