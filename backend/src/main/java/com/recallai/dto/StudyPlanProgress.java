package com.recallai.dto;

/**
 * @param onTrack   done share is at least the share of study days already past (minus a small grace)
 * @param dueToday  minutes planned for today that are still pending
 */
public record StudyPlanProgress(
        long totalTasks,
        long doneTasks,
        long skippedTasks,
        long pendingTasks,
        int percentComplete,
        int totalMinutes,
        int doneMinutes,
        long daysUntilExam,
        long studyDaysLeft,
        long studyDaysTotal,
        boolean onTrack,
        int dueToday) {
}
