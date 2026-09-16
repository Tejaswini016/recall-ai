package com.recallai.ai;

import java.util.List;

/**
 * What the model is told when asked to comment on a study plan. Only aggregate performance
 * numbers; never the student's identity or card contents.
 *
 * @param topics one entry per exam topic with its current standing
 */
public record StudyPlanContext(
        String examName,
        long daysUntilExam,
        long studyDays,
        int minutesPerDay,
        String knowledgeLevel,
        List<TopicStanding> topics,
        long openMistakes) {

    /**
     * @param category CRITICAL, WEAK, GOOD, STRONG or UNRATED
     * @param accuracy percentage, meaningful only when attempts is above zero
     */
    public record TopicStanding(String topic, String category, int accuracy, long attempts, int plannedMinutes) {
    }
}
