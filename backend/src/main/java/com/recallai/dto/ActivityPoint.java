package com.recallai.dto;

import java.time.LocalDate;

/** One day on the activity and retention charts. Days without reviews carry zeros and nulls. */
public record ActivityPoint(
        LocalDate date,
        long reviews,
        long successful,
        Double averageQuality,
        Integer retentionPercent) {
}
