package com.recallai.ai;

import java.util.List;

/** Validated coaching text for a study plan. The schedule itself never comes from the model. */
public record StudyPlanAdvice(String summary, List<TopicAdvice> topicAdvice) {

    public record TopicAdvice(String topic, String advice) {
    }
}
