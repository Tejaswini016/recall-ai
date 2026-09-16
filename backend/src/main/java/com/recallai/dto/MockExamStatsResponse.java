package com.recallai.dto;

import java.util.List;

/** Headline mock-exam numbers for the dashboard. */
public record MockExamStatsResponse(
        long exams,
        long submitted,
        Integer averagePercent,
        Integer bestPercent,
        Integer latestPercent,
        List<MockExamSummaryResponse> recent) {
}
