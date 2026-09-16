package com.recallai.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.recallai.dto.TopicCategory;
import com.recallai.dto.TopicInsightResponse;
import com.recallai.entity.KnowledgeLevel;
import com.recallai.entity.StudyTaskType;
import com.recallai.service.StudyPlanAllocator.Input;
import com.recallai.service.StudyPlanAllocator.PlannedTask;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class StudyPlanAllocatorTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 14);

    private static TopicInsightResponse insight(String topic, TopicCategory category, int accuracy, long attempts) {
        return new TopicInsightResponse(topic, category, accuracy, attempts, 0, 3, attempts, 0, 0, 0, null, "");
    }

    @Test
    void studyDaysFollowPreferredWeekdaysAndFallBackToEveryDayWhenNoneFit() {
        List<LocalDate> weekdays = StudyPlanAllocator.studyDays(MONDAY, MONDAY.plusDays(14),
                EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY));
        assertThat(weekdays).hasSize(6).allMatch(d -> Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
                .contains(d.getDayOfWeek()));

        // Exam on Wednesday, only Sundays preferred: nothing fits, so every remaining day is used.
        List<LocalDate> fallback = StudyPlanAllocator.studyDays(MONDAY, MONDAY.plusDays(2), EnumSet.of(DayOfWeek.SUNDAY));
        assertThat(fallback).containsExactly(MONDAY, MONDAY.plusDays(1));
        assertThat(StudyPlanAllocator.studyDays(MONDAY, MONDAY, EnumSet.allOf(DayOfWeek.class))).isEmpty();
    }

    @Test
    void weakTopicsGetMoreTimeAndEveryDayFitsTheBudget() {
        Map<String, TopicInsightResponse> insights = Map.of(
                "genetics", insight("Genetics", TopicCategory.CRITICAL, 30, 10),
                "cells", insight("Cells", TopicCategory.STRONG, 95, 20));
        Input input = new Input("Biology final", MONDAY.plusDays(21), List.of("Genetics", "Cells", "Ecology"),
                KnowledgeLevel.INTERMEDIATE, 60, EnumSet.allOf(DayOfWeek.class), insights, 40, 3);

        List<PlannedTask> tasks = StudyPlanAllocator.allocate(input, MONDAY);

        Map<LocalDate, Integer> minutesPerDay = tasks.stream()
                .collect(Collectors.groupingBy(PlannedTask::date, Collectors.summingInt(PlannedTask::minutes)));
        assertThat(minutesPerDay).hasSize(21).allSatisfy((day, minutes) -> assertThat(minutes).isEqualTo(60));

        Map<String, Integer> minutesPerTopic = tasks.stream().filter(t -> t.topic() != null)
                .collect(Collectors.groupingBy(PlannedTask::topic, Collectors.summingInt(PlannedTask::minutes)));
        assertThat(minutesPerTopic.get("Genetics")).isGreaterThan(minutesPerTopic.get("Ecology"));
        assertThat(minutesPerTopic.get("Ecology")).isGreaterThan(minutesPerTopic.get("Cells"));

        // Every day starts with the due-card review; mistake blocks alternate days.
        assertThat(tasks.stream().filter(t -> t.type() == StudyTaskType.REVIEW_DUE).count()).isEqualTo(21);
        assertThat(tasks.stream().filter(t -> t.type() == StudyTaskType.REVIEW_MISTAKES).count()).isEqualTo(11);
        assertThat(tasks.stream().filter(t -> t.date().equals(MONDAY)).map(PlannedTask::type).toList())
                .startsWith(StudyTaskType.REVIEW_DUE, StudyTaskType.REVIEW_MISTAKES);

        // Mocks close each full week; the last day is the final revision and names the weakest topic first.
        assertThat(tasks.stream().filter(t -> t.type() == StudyTaskType.MOCK_EXAM).map(PlannedTask::date).toList())
                .containsExactly(MONDAY.plusDays(6), MONDAY.plusDays(13));
        PlannedTask last = tasks.get(tasks.size() - 1);
        assertThat(last.type()).isEqualTo(StudyTaskType.FINAL_REVISION);
        assertThat(last.date()).isEqualTo(MONDAY.plusDays(20));
        assertThat(last.description()).contains("Genetics, Ecology, Cells");

        // Strong topics start with practice; unknown ones start by learning; orders are dense per day.
        PlannedTask firstCells = tasks.stream().filter(t -> "Cells".equals(t.topic())).findFirst().orElseThrow();
        PlannedTask firstEcology = tasks.stream().filter(t -> "Ecology".equals(t.topic())).findFirst().orElseThrow();
        assertThat(firstCells.type()).isEqualTo(StudyTaskType.PRACTICE_QUIZ);
        assertThat(firstEcology.type()).isEqualTo(StudyTaskType.LEARN_TOPIC);
        assertThat(firstEcology.description()).contains("No attempts recorded yet");
        assertThat(tasks.stream().filter(t -> "Genetics".equals(t.topic())).findFirst().orElseThrow().description())
                .contains("30% over 10 attempts (critical)");
        assertThat(tasks.stream().filter(t -> t.date().equals(MONDAY)).map(PlannedTask::order).toList())
                .isEqualTo(List.of(0, 1, 2, 3));
    }

    @Test
    void shortPlansSkipMocksAndTinyBudgetsStillProduceOneBlock() {
        Input input = new Input("Quiz", MONDAY.plusDays(3), List.of("Only"), KnowledgeLevel.BEGINNER, 15,
                EnumSet.allOf(DayOfWeek.class), Map.of(), 0, 0);
        List<PlannedTask> tasks = StudyPlanAllocator.allocate(input, MONDAY);

        assertThat(tasks).hasSize(3);
        assertThat(tasks.stream().map(PlannedTask::type)).containsExactly(
                StudyTaskType.LEARN_TOPIC, StudyTaskType.PRACTICE_QUIZ, StudyTaskType.FINAL_REVISION);
        assertThat(tasks).allMatch(t -> t.minutes() == 15);
        assertThat(StudyPlanAllocator.allocate(input, MONDAY.plusDays(3))).isEmpty();
    }

    @Test
    void weightsReflectStandingAndSelfAssessment() {
        assertThat(StudyPlanAllocator.weight(insight("t", TopicCategory.CRITICAL, 0, 5), KnowledgeLevel.ADVANCED)).isEqualTo(4.0);
        assertThat(StudyPlanAllocator.weight(null, KnowledgeLevel.BEGINNER)).isEqualTo(3.0);
        assertThat(StudyPlanAllocator.weight(null, KnowledgeLevel.ADVANCED)).isEqualTo(1.5);
        assertThat(StudyPlanAllocator.weight(insight("t", TopicCategory.STRONG, 99, 5), KnowledgeLevel.BEGINNER)).isEqualTo(1.0);
        assertThat(StudyPlanAllocator.round5(23)).isEqualTo(25);
        assertThat(StudyPlanAllocator.round5(22)).isEqualTo(20);
    }
}
