package com.recallai.service;

import com.recallai.dto.ReadinessResponse;
import com.recallai.dto.TopicCategory;
import com.recallai.dto.TopicInsightResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The exam-readiness heuristic, pure and documented in one place. Five components, each 0-100,
 * combined by fixed weights; a component with no evidence (no mock exams, no retention data)
 * drops out and its weight is shared among the rest, so a student is never punished for a
 * feature they have not used yet.
 *
 * <ul>
 *   <li>Accuracy (30): successful reviews and correct quiz/exam answers over all attempts.</li>
 *   <li>Retention (25): share of successful reviews in the last 30 days.</li>
 *   <li>Topic coverage (20): rated topics that are good or strong; unrated topics count half.</li>
 *   <li>Revision consistency (15): days studied in the last week, minus an overdue backlog penalty.</li>
 *   <li>Mock exams (10): average mock exam score.</li>
 * </ul>
 */
public final class ReadinessCalculator {

    static final int WEIGHT_ACCURACY = 30;
    static final int WEIGHT_RETENTION = 25;
    static final int WEIGHT_COVERAGE = 20;
    static final int WEIGHT_CONSISTENCY = 15;
    static final int WEIGHT_MOCKS = 10;
    static final int MAX_BACKLOG_PENALTY = 40;
    static final long MEDIUM_EVIDENCE = 20;
    static final long HIGH_EVIDENCE = 100;

    private ReadinessCalculator() {
    }

    /**
     * @param retentionPercent   null when there were no reviews in the window
     * @param activeDaysLastWeek distinct days with reviews in the last seven days (0-7)
     * @param overdueCards       cards whose due date is before today
     * @param mockExamAverage    null when no exam was submitted
     */
    public record Input(List<TopicInsightResponse> topics, Integer retentionPercent, int activeDaysLastWeek,
                        long overdueCards, long totalCards, Integer mockExamAverage) {
    }

    public static ReadinessResponse compute(Input in, Instant now) {
        long attempts = in.topics().stream().mapToLong(TopicInsightResponse::attempts).sum();
        long successes = attempts - in.topics().stream().mapToLong(TopicInsightResponse::mistakes).sum();
        int accuracy = TopicCategorizer.percent(successes, attempts);

        List<Raw> raw = new ArrayList<>();
        raw.add(new Raw("accuracy", "Accuracy", accuracy, WEIGHT_ACCURACY, attempts > 0,
                attempts == 0 ? "No reviews or quiz answers yet."
                        : accuracy + "% of " + attempts + " reviews and answers were right."));
        raw.add(new Raw("retention", "Retention", in.retentionPercent() == null ? 0 : in.retentionPercent(),
                WEIGHT_RETENTION, in.retentionPercent() != null,
                in.retentionPercent() == null ? "No reviews in the last 30 days."
                        : in.retentionPercent() + "% of reviews in the last 30 days were recalled."));

        List<TopicInsightResponse> rated = in.topics().stream()
                .filter(t -> t.category() != TopicCategory.UNRATED).toList();
        long good = rated.stream()
                .filter(t -> t.category() == TopicCategory.GOOD || t.category() == TopicCategory.STRONG).count();
        long unrated = in.topics().size() - rated.size();
        int coverage = in.topics().isEmpty() ? 0
                : (int) Math.round((good + 0.5 * unrated) * 100.0 / in.topics().size());
        List<String> weakNames = in.topics().stream()
                .filter(t -> t.category() == TopicCategory.CRITICAL || t.category() == TopicCategory.WEAK)
                .map(TopicInsightResponse::topic).toList();
        raw.add(new Raw("coverage", "Topic coverage", coverage, WEIGHT_COVERAGE, !in.topics().isEmpty(),
                in.topics().isEmpty() ? "No topics studied yet."
                        : good + " of " + in.topics().size() + " topics are good or strong"
                                + (weakNames.isEmpty() ? "." : "; weak: " + String.join(", ", weakNames) + ".")));

        int consistency = (int) Math.round(in.activeDaysLastWeek() * 100.0 / 7);
        int penalty = in.totalCards() == 0 ? 0
                : (int) Math.min(MAX_BACKLOG_PENALTY, Math.round(in.overdueCards() * 100.0 / in.totalCards()));
        raw.add(new Raw("consistency", "Revision consistency", Math.max(0, consistency - penalty), WEIGHT_CONSISTENCY,
                in.totalCards() > 0,
                in.totalCards() == 0 ? "Add cards to start a review habit."
                        : "Studied on " + in.activeDaysLastWeek() + " of the last 7 days"
                                + (in.overdueCards() > 0 ? "; " + in.overdueCards() + " cards are overdue." : ".")));

        raw.add(new Raw("mockExams", "Mock exams", in.mockExamAverage() == null ? 0 : in.mockExamAverage(), WEIGHT_MOCKS,
                in.mockExamAverage() != null,
                in.mockExamAverage() == null ? "No mock exam taken yet."
                        : "Average mock exam score " + in.mockExamAverage() + "%."));

        int availableWeight = raw.stream().filter(r -> r.available).mapToInt(r -> r.weight).sum();
        List<ReadinessResponse.Component> components = new ArrayList<>();
        double total = 0;
        for (Raw r : raw) {
            int weight = !r.available || availableWeight == 0 ? 0
                    : BigDecimal.valueOf(r.weight * 100.0 / availableWeight).setScale(0, RoundingMode.HALF_UP).intValue();
            if (r.available && availableWeight > 0) {
                total += r.score * (r.weight / (double) availableWeight);
            }
            components.add(new ReadinessResponse.Component(r.key, r.label, r.score, weight, r.detail, r.available));
        }
        int score = availableWeight == 0 ? 0 : (int) Math.round(total);
        String confidence = attempts >= HIGH_EVIDENCE ? "HIGH" : attempts >= MEDIUM_EVIDENCE ? "MEDIUM" : "LOW";
        return new ReadinessResponse(score, label(score), true, confidence, attempts, components,
                recommendation(raw, weakNames, in), now);
    }

    static String label(int score) {
        if (score >= 85) {
            return "Exam ready";
        }
        if (score >= 70) {
            return "Nearly there";
        }
        if (score >= 50) {
            return "Getting there";
        }
        return "Needs work";
    }

    private static String recommendation(List<Raw> raw, List<String> weakNames, Input in) {
        long attempts = in.topics().stream().mapToLong(TopicInsightResponse::attempts).sum();
        if (attempts == 0) {
            return "Start by reviewing your cards and taking a quiz; the estimate needs results to work from.";
        }
        Raw weakest = raw.stream().filter(r -> r.available)
                .min((a, b) -> Integer.compare(a.score, b.score)).orElse(raw.get(0));
        Raw mocks = raw.get(4);
        if (!mocks.available) {
            return switch (weakest.key) {
                case "coverage" -> "Practise your weak topics (" + String.join(", ", weakNames)
                        + "), then take a mock exam to test yourself under time pressure.";
                default -> "Take a mock exam to see how you perform under exam conditions; it also feeds this estimate.";
            };
        }
        return switch (weakest.key) {
            case "accuracy" -> "Accuracy is the weak spot: run targeted quizzes on "
                    + (weakNames.isEmpty() ? "your hardest topics" : String.join(", ", weakNames)) + " and review every mistake.";
            case "retention" -> "Retention is slipping: clear your due cards every day so intervals can grow.";
            case "coverage" -> weakNames.isEmpty() ? "Keep every topic rated good or better with regular practice."
                    : "Lift the weak topics: " + String.join(", ", weakNames) + ".";
            case "consistency" -> in.overdueCards() > 0
                    ? "Clear the " + in.overdueCards() + " overdue cards and study on more days this week."
                    : "Study on more days this week; short daily sessions beat one long one.";
            default -> "Mock exam scores lag your practice: sit another timed exam and review its mistakes.";
        };
    }

    private record Raw(String key, String label, int score, int weight, boolean available, String detail) {
    }

    static String joinTopics(List<TopicInsightResponse> topics) {
        return topics.stream().map(TopicInsightResponse::topic).collect(Collectors.joining(", "));
    }
}
