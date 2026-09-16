package com.recallai.dto;

import java.util.List;

/** Counts for the dashboard plus the topics with the most open mistakes. */
public record MistakeSummaryResponse(
        long open,
        long converted,
        long dismissed,
        long total,
        List<TopicMistakes> openByTopic) {

    public record TopicMistakes(String topic, long count) {
    }
}
