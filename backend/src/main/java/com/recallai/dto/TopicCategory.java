package com.recallai.dto;

/** Ordered from most to least urgent so lists can sort by ordinal. */
public enum TopicCategory {
    CRITICAL,
    WEAK,
    GOOD,
    STRONG,
    /** Too few attempts to judge. */
    UNRATED
}
