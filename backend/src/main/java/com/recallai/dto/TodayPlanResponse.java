package com.recallai.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Today's tasks across every active plan, plus pending tasks from the last week that were not
 * done ("carried over").
 */
public record TodayPlanResponse(
        LocalDate date,
        int activePlans,
        List<StudyPlanTaskResponse> tasks,
        List<StudyPlanTaskResponse> carriedOver,
        int totalMinutes,
        int doneMinutes) {
}
