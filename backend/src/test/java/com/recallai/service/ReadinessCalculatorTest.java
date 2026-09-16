package com.recallai.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.recallai.dto.ReadinessResponse;
import com.recallai.dto.TopicCategory;
import com.recallai.dto.TopicInsightResponse;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReadinessCalculatorTest {

    private static final Instant NOW = Instant.parse("2026-09-16T10:00:00Z");

    private static TopicInsightResponse topic(String name, TopicCategory category, long attempts, long mistakes) {
        int accuracy = TopicCategorizer.percent(attempts - mistakes, attempts);
        return new TopicInsightResponse(name, category, accuracy, attempts, mistakes, 1, attempts, attempts - mistakes,
                0, 0, null, "");
    }

    @Test
    void emptyAccountScoresZeroWithAnHonestRecommendation() {
        ReadinessResponse r = ReadinessCalculator.compute(
                new ReadinessCalculator.Input(List.of(), null, 0, 0, 0, null), NOW);

        assertThat(r.score()).isZero();
        assertThat(r.label()).isEqualTo("Needs work");
        assertThat(r.estimate()).isTrue();
        assertThat(r.confidence()).isEqualTo("LOW");
        assertThat(r.components()).hasSize(5).allMatch(c -> !c.available() && c.weight() == 0);
        assertThat(r.recommendation()).contains("needs results");
    }

    @Test
    void unavailableComponentsRedistributeTheirWeight() {
        // Accuracy 80 over 50 attempts, retention 90, one strong topic, studied 7/7 days, no mocks.
        ReadinessResponse r = ReadinessCalculator.compute(new ReadinessCalculator.Input(
                List.of(topic("Cells", TopicCategory.STRONG, 50, 10)), 90, 7, 0, 20, null), NOW);

        // Weights 30/25/20/15 out of 90 -> 33/28/22/17; score = 80*30/90 + 90*25/90 + 100*20/90 + 100*15/90 = 90.6
        assertThat(r.components().stream().mapToInt(ReadinessResponse.Component::weight).sum()).isBetween(99, 101);
        assertThat(r.components().get(4).available()).isFalse();
        assertThat(r.components().get(4).weight()).isZero();
        assertThat(r.score()).isEqualTo(91);
        assertThat(r.label()).isEqualTo("Exam ready");
        assertThat(r.confidence()).isEqualTo("MEDIUM");
        assertThat(r.recommendation()).contains("mock exam");
    }

    @Test
    void weakTopicsBacklogAndPoorMocksPullTheScoreDownAndDriveTheAdvice() {
        ReadinessResponse r = ReadinessCalculator.compute(new ReadinessCalculator.Input(
                List.of(topic("Genetics", TopicCategory.CRITICAL, 60, 40), topic("Cells", TopicCategory.GOOD, 60, 10),
                        topic("Ecology", TopicCategory.UNRATED, 1, 0)),
                55, 2, 30, 100, 40), NOW);

        ReadinessResponse.Component coverage = r.components().get(2);
        assertThat(coverage.score()).isEqualTo(50); // (1 good + 0.5 unrated) / 3
        assertThat(coverage.detail()).contains("weak: Genetics");
        ReadinessResponse.Component consistency = r.components().get(3);
        assertThat(consistency.score()).isEqualTo(0); // 29 - 30 backlog penalty, floored
        assertThat(consistency.detail()).contains("30 cards are overdue");
        assertThat(r.score()).isLessThan(60);
        assertThat(r.confidence()).isEqualTo("HIGH");
        assertThat(r.recommendation()).contains("overdue");
    }

    @Test
    void labelsFollowTheThresholds() {
        assertThat(ReadinessCalculator.label(85)).isEqualTo("Exam ready");
        assertThat(ReadinessCalculator.label(70)).isEqualTo("Nearly there");
        assertThat(ReadinessCalculator.label(50)).isEqualTo("Getting there");
        assertThat(ReadinessCalculator.label(49)).isEqualTo("Needs work");
    }
}
