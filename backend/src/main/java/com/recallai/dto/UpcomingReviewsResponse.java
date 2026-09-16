package com.recallai.dto;

import java.time.LocalDate;
import java.util.List;

/** Cards coming due per day, plus what is already overdue. */
public record UpcomingReviewsResponse(LocalDate today, long overdue, long dueToday, List<Day> days) {

    public record Day(LocalDate date, long cards) {
    }
}
