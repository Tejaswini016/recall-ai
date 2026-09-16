package com.recallai.service;

import com.recallai.dto.TopicCategory;
import com.recallai.dto.TopicInsightResponse;
import com.recallai.entity.KnowledgeLevel;
import com.recallai.entity.StudyTaskType;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic study-plan scheduling. Given the exam, the days available and what the app
 * already knows about each topic, produces dated tasks:
 *
 * <ul>
 *   <li>every study day starts with clearing the spaced-repetition queue (when the student has
 *       cards) and, every other day while mistakes are open, a mistake-review block;</li>
 *   <li>the rest of the day goes to one or two exam topics chosen by a weighted fair share, where
 *       critical and weak topics (and unknown ones for beginners) weigh most; a topic's first
 *       block is "learn", later blocks alternate practice quizzes and learning, and topics the
 *       student is already good at start with practice;</li>
 *   <li>the last study day of each week (when the plan spans at least five study days) ends
 *       with a mock exam; the final study day is a revision of the weakest topics.</li>
 * </ul>
 *
 * <p>Pure and repeatable so regeneration after new results gives an explainable diff.
 */
public final class StudyPlanAllocator {

    static final int MIN_BLOCK_MINUTES = 10;
    static final int MIN_STUDY_DAYS_FOR_MOCKS = 5;
    private static final double REVIEW_SHARE = 0.20;
    private static final int REVIEW_MAX_MINUTES = 20;
    private static final int MISTAKES_MINUTES = 10;
    private static final double MOCK_SHARE = 0.40;
    private static final int MOCK_MIN_MINUTES = 20;
    private static final int TWO_TOPIC_THRESHOLD = 40;

    private StudyPlanAllocator() {
    }

    /**
     * @param insightsByKey topic insight keyed by {@link TopicInsightService#key}
     */
    public record Input(String examName, LocalDate examDate, List<String> topics, KnowledgeLevel level,
                        int minutesPerDay, Set<DayOfWeek> preferredDays, Map<String, TopicInsightResponse> insightsByKey,
                        long totalCards, long openMistakes) {
    }

    public record PlannedTask(LocalDate date, int order, StudyTaskType type, String topic, String title,
                              String description, int minutes) {
    }

    /** Study days from {@code from} up to the day before the exam, on the preferred weekdays. */
    public static List<LocalDate> studyDays(LocalDate from, LocalDate examDate, Set<DayOfWeek> preferredDays) {
        List<LocalDate> days = new ArrayList<>();
        for (LocalDate day = from; day.isBefore(examDate); day = day.plusDays(1)) {
            if (preferredDays.isEmpty() || preferredDays.contains(day.getDayOfWeek())) {
                days.add(day);
            }
        }
        if (days.isEmpty()) {
            // Too close to the exam for the preferred weekdays: use every remaining day instead.
            for (LocalDate day = from; day.isBefore(examDate); day = day.plusDays(1)) {
                days.add(day);
            }
        }
        return days;
    }

    public static List<PlannedTask> allocate(Input in, LocalDate from) {
        List<LocalDate> days = studyDays(from, in.examDate(), in.preferredDays());
        List<PlannedTask> out = new ArrayList<>();
        if (days.isEmpty()) {
            return out;
        }
        List<TopicState> topics = new ArrayList<>();
        for (String name : in.topics()) {
            TopicInsightResponse insight = in.insightsByKey().get(TopicInsightService.key(name));
            topics.add(new TopicState(name.strip(), insight, weight(insight, in.level())));
        }
        double totalWeight = topics.stream().mapToDouble(t -> t.weight).sum();

        for (int d = 0; d < days.size(); d++) {
            LocalDate day = days.get(d);
            boolean last = d == days.size() - 1;
            int budget = in.minutesPerDay();
            int order = 0;

            if (in.totalCards() > 0) {
                int minutes = clamp(round5(budget * REVIEW_SHARE), MIN_BLOCK_MINUTES, Math.min(REVIEW_MAX_MINUTES, budget / 2));
                out.add(new PlannedTask(day, order++, StudyTaskType.REVIEW_DUE, null, "Review due cards",
                        "Clear today's spaced-repetition queue. Rate honestly: cards you rate low come back sooner.",
                        minutes));
                budget -= minutes;
            }
            if (in.openMistakes() > 0 && d % 2 == 0 && budget >= MISTAKES_MINUTES * 2) {
                out.add(new PlannedTask(day, order++, StudyTaskType.REVIEW_MISTAKES, null, "Review mistakes",
                        "Turn open mistakes into flashcards so the questions you got wrong enter the review schedule.",
                        MISTAKES_MINUTES));
                budget -= MISTAKES_MINUTES;
            }
            if (last) {
                out.add(new PlannedTask(day, order, StudyTaskType.FINAL_REVISION, null,
                        "Final revision before " + in.examName(),
                        "Exam tomorrow. Skim every topic, weakest first: " + weakestFirst(topics)
                                + ". Then re-read the explanations of your mistakes and stop early.",
                        Math.max(MIN_BLOCK_MINUTES, budget)));
                continue;
            }
            boolean weekEnds = d + 1 < days.size()
                    && days.get(d + 1).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) != day.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            boolean mockDay = weekEnds && days.size() >= MIN_STUDY_DAYS_FOR_MOCKS && budget >= MOCK_MIN_MINUTES + MIN_BLOCK_MINUTES;
            int mockMinutes = mockDay ? Math.max(MOCK_MIN_MINUTES, round5(budget * MOCK_SHARE)) : 0;
            int topicBudget = budget - mockMinutes;

            // Weighted fair share: every day each topic earns its share, spending it when scheduled.
            for (TopicState t : topics) {
                t.deficit += t.weight / totalWeight;
            }
            List<TopicState> chosen = pick(topics, topicBudget >= TWO_TOPIC_THRESHOLD ? 2 : 1);
            int[] split = chosen.size() == 2
                    ? new int[] {round5(topicBudget * 0.6), topicBudget - round5(topicBudget * 0.6)}
                    : new int[] {topicBudget};
            for (int i = 0; i < chosen.size(); i++) {
                TopicState t = chosen.get(i);
                int minutes = Math.max(MIN_BLOCK_MINUTES, split[i]);
                t.deficit -= (double) minutes / topicBudget;
                StudyTaskType type = t.nextType();
                out.add(new PlannedTask(day, order++, type, t.name,
                        (type == StudyTaskType.LEARN_TOPIC ? "Learn: " : "Practice quiz: ") + t.name,
                        description(type, t), minutes));
                t.scheduled++;
            }
            if (mockDay) {
                out.add(new PlannedTask(day, order, StudyTaskType.MOCK_EXAM, null, "Mock exam: " + in.examName(),
                        "Take a timed mock exam on this week's topics under exam conditions, then review every "
                                + "wrong answer. Weak topics it reveals get more time next week.",
                        mockMinutes));
            }
        }
        return out;
    }

    /** Critical and weak topics weigh most; unknown topics weigh more the less the student knows. */
    static double weight(TopicInsightResponse insight, KnowledgeLevel level) {
        TopicCategory category = insight == null ? TopicCategory.UNRATED : insight.category();
        return switch (category) {
            case CRITICAL -> 4.0;
            case WEAK -> 3.0;
            case UNRATED -> switch (level) {
                case BEGINNER -> 3.0;
                case INTERMEDIATE -> 2.0;
                case ADVANCED -> 1.5;
            };
            case GOOD -> 1.5;
            case STRONG -> 1.0;
        };
    }

    private static List<TopicState> pick(List<TopicState> topics, int count) {
        return topics.stream()
                .sorted(Comparator.comparingDouble((TopicState t) -> t.deficit).reversed())
                .limit(Math.min(count, topics.size()))
                .toList();
    }

    private static String description(StudyTaskType type, TopicState t) {
        String standing = t.standing();
        return type == StudyTaskType.LEARN_TOPIC
                ? "Study " + t.name + " from your cards and notes; generate cards for anything not covered yet. " + standing
                : "Generate a targeted quiz on " + t.name + ", then review every mistake it surfaces. " + standing;
    }

    private static String weakestFirst(List<TopicState> topics) {
        return String.join(", ", topics.stream()
                .sorted(Comparator.comparingDouble((TopicState t) -> t.weight).reversed())
                .map(t -> t.name)
                .toList());
    }

    static int round5(double minutes) {
        return (int) (Math.round(minutes / 5.0) * 5);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class TopicState {
        final String name;
        final TopicInsightResponse insight;
        final double weight;
        double deficit;
        int scheduled;

        TopicState(String name, TopicInsightResponse insight, double weight) {
            this.name = name;
            this.insight = insight;
            this.weight = weight;
        }

        /** Learn first, then alternate; topics already good or strong start with practice. */
        StudyTaskType nextType() {
            boolean knowsIt = insight != null
                    && (insight.category() == TopicCategory.GOOD || insight.category() == TopicCategory.STRONG);
            boolean practice = knowsIt ? scheduled % 2 == 0 : scheduled % 2 == 1;
            return practice ? StudyTaskType.PRACTICE_QUIZ : StudyTaskType.LEARN_TOPIC;
        }

        String standing() {
            if (insight == null || insight.attempts() == 0) {
                return "No attempts recorded yet, so start by building a first set of cards.";
            }
            String category = insight.category().name().toLowerCase(Locale.ROOT);
            return "Current accuracy " + insight.accuracyPercent() + "% over " + insight.attempts()
                    + " attempts (" + category + "): " + hint(insight.category());
        }

        private static String hint(TopicCategory category) {
            return switch (category) {
                case CRITICAL -> "relearn the basics before testing yourself.";
                case WEAK -> "short study, then quiz until it sticks.";
                case GOOD -> "keep it fresh with quick practice.";
                case STRONG -> "light maintenance is enough.";
                case UNRATED -> "a few more attempts will show where you stand.";
            };
        }
    }
}
