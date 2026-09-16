package com.recallai.entity;

/** How a card came to exist. */
public enum CardOrigin {
    /** Written by hand. */
    MANUAL,
    /** Generated from study material by the model. */
    AI,
    /** Built from a mistake the student made in a quiz or mock exam. */
    MISTAKE
}
