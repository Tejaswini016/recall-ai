package com.recallai.service;

import com.recallai.config.ReviewProperties;
import com.recallai.dto.ActivityPoint;
import com.recallai.dto.AnalyticsSummaryResponse;
import com.recallai.dto.DifficultyDistributionResponse;
import com.recallai.dto.MasteryPoint;
import com.recallai.dto.StreakResponse;
import com.recallai.dto.TopicPerformanceResponse;
import com.recallai.repository.CardRepository;
import com.recallai.repository.DailyActivity;
import com.recallai.repository.DeckRepository;
import com.recallai.repository.DifficultyCount;
import com.recallai.repository.QuizAttemptRepository;
import com.recallai.repository.ReviewHistoryRepository;
import com.recallai.repository.TopicStats;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import com.recallai.scheduler.DifficultyTier;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only aggregation over review history, cards and quiz attempts. All numbers come from
 * SQL aggregates; Java only fills calendar gaps and applies the weak-topic rule.
 */
@Service
public class AnalyticsService {

    static final int RETENTION_WINDOW_DAYS = 30;
    private static final int QUALITY_SCALE = 2;

    private final ReviewHistoryRepository reviewHistoryRepository;
    private final CardRepository cardRepository;
    private final DeckRepository deckRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final ReviewService reviewService;
    private final DeckService deckService;
    private final WeakTopicDetector weakTopicDetector;
    private final ReviewProperties reviewProperties;
    private final Clock clock;

    public AnalyticsService(ReviewHistoryRepository reviewHistoryRepository, CardRepository cardRepository,
                            DeckRepository deckRepository, QuizAttemptRepository quizAttemptRepository,
                            ReviewService reviewService, DeckService deckService, WeakTopicDetector weakTopicDetector,
                            ReviewProperties reviewProperties, Clock clock) {
        this.reviewHistoryRepository = reviewHistoryRepository;
        this.cardRepository = cardRepository;
        this.deckRepository = deckRepository;
        this.quizAttemptRepository = quizAttemptRepository;
        this.reviewService = reviewService;
        this.deckService = deckService;
        this.weakTopicDetector = weakTopicDetector;
        this.reviewProperties = reviewProperties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AnalyticsSummaryResponse summary(Long userId) {
        LocalDate today = LocalDate.now(clock);
        StreakResponse streak = reviewService.streak(userId);
        long totalCards = cardRepository.countByDeckUserId(userId);
        long mastered = cardRepository.countByDeckUserIdAndIntervalDaysGreaterThanEqual(userId,
                reviewProperties.masteredIntervalDays());
        Double retention = reviewHistoryRepository.retentionSince(userId,
                clock.instant().minus(Duration.ofDays(RETENTION_WINDOW_DAYS)));
        Double quizAverage = quizAttemptRepository.averagePercent(userId);
        return new AnalyticsSummaryResponse(
                cardRepository.countDue(userId, today),
                streak.reviewedToday(),
                totalCards,
                mastered,
                DeckService.progressPercent(mastered, totalCards),
                deckRepository.countByUserId(userId),
                reviewHistoryRepository.countByUserId(userId),
                round(reviewHistoryRepository.averageQuality(userId)),
                percent(retention),
                streak.currentStreak(),
                streak.longestStreak(),
                streak.lastActiveDate(),
                quizAttemptRepository.countByUserId(userId),
                quizAverage == null ? null : (int) Math.round(quizAverage));
    }

    /** One point per calendar day over the window, oldest first, gaps filled with zeros. */
    @Transactional(readOnly = true)
    public List<ActivityPoint> activity(Long userId, int days) {
        LocalDate today = LocalDate.now(clock);
        LocalDate start = today.minusDays(days - 1L);
        Instant since = start.atStartOfDay(clock.getZone()).toInstant();
        Map<LocalDate, DailyActivity> byDay = reviewHistoryRepository.dailyActivity(userId, since).stream()
                .collect(Collectors.toMap(d -> d.getDay().toLocalDate(), Function.identity()));

        List<ActivityPoint> points = new ArrayList<>(days);
        for (LocalDate day = start; !day.isAfter(today); day = day.plusDays(1)) {
            DailyActivity activity = byDay.get(day);
            if (activity == null) {
                points.add(new ActivityPoint(day, 0, 0, null, null));
            } else {
                Integer retention = activity.getReviews() == 0 ? null
                        : (int) Math.round(activity.getSuccessful() * 100.0 / activity.getReviews());
                points.add(new ActivityPoint(day, activity.getReviews(), activity.getSuccessful(),
                        round(activity.getAverageQuality()), retention));
            }
        }
        return points;
    }

    /** Cumulative cards that had reached the mastered interval by each day of the window. */
    @Transactional(readOnly = true)
    public List<MasteryPoint> mastery(Long userId, int days) {
        LocalDate today = LocalDate.now(clock);
        LocalDate start = today.minusDays(days - 1L);
        TreeMap<LocalDate, Long> firstMasteredByDay = new TreeMap<>();
        for (DailyActivity row : reviewHistoryRepository.masteryByDay(userId,
                reviewProperties.masteredIntervalDays())) {
            firstMasteredByDay.merge(row.getDay().toLocalDate(), row.getReviews(), Long::sum);
        }

        long cumulative = firstMasteredByDay.headMap(start, false).values().stream().mapToLong(Long::longValue).sum();
        List<MasteryPoint> points = new ArrayList<>(days);
        for (LocalDate day = start; !day.isAfter(today); day = day.plusDays(1)) {
            cumulative += firstMasteredByDay.getOrDefault(day, 0L);
            points.add(new MasteryPoint(day, cumulative));
        }
        return points;
    }

    /** Every reviewed topic: flagged topics first, then by recent average (weakest first), then by review count. */
    @Transactional(readOnly = true)
    public List<TopicPerformanceResponse> topics(Long userId, Long deckId) {
        if (deckId != null) {
            deckService.getOwnedDeck(userId, deckId);
        }
        return reviewHistoryRepository.topicStats(userId, deckId, weakTopicDetector.recentWindow()).stream()
                .map(this::toTopicResponse)
                .sorted(Comparator.comparing(TopicPerformanceResponse::weak).reversed()
                        .thenComparingDouble(TopicPerformanceResponse::recentAverageQuality)
                        .thenComparing(TopicPerformanceResponse::reviews, Comparator.reverseOrder())
                        .thenComparing(TopicPerformanceResponse::topic))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TopicPerformanceResponse> weakTopics(Long userId, Long deckId) {
        return topics(userId, deckId).stream().filter(TopicPerformanceResponse::weak).toList();
    }

    /** Cards per adaptive difficulty tier, every tier present even when empty. */
    @Transactional(readOnly = true)
    public DifficultyDistributionResponse difficulty(Long userId) {
        Map<String, Long> counts = cardRepository.countByDifficulty(userId).stream()
                .collect(Collectors.toMap(DifficultyCount::getDifficulty, DifficultyCount::getCards));
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        List<DifficultyDistributionResponse.Bucket> buckets = new ArrayList<>();
        for (DifficultyTier tier : DifficultyTier.values()) {
            long cards = counts.getOrDefault(tier.name(), 0L);
            buckets.add(new DifficultyDistributionResponse.Bucket(tier, cards, DeckService.progressPercent(cards, total)));
        }
        Double averageResponse = cardRepository.averageResponseMs(userId);
        return new DifficultyDistributionResponse(total, buckets,
                averageResponse == null ? null : (int) Math.round(averageResponse),
                cardRepository.countWithLapses(userId));
    }

    private TopicPerformanceResponse toTopicResponse(TopicStats stats) {
        double recent = stats.getRecentAverageQuality() == null
                ? stats.getAverageQuality()
                : stats.getRecentAverageQuality();
        return new TopicPerformanceResponse(
                stats.getTopic(),
                stats.getCardCount(),
                stats.getReviews(),
                round(stats.getAverageQuality()),
                round(recent),
                percent(stats.getSuccessRate()),
                stats.getLastReviewedAt(),
                weakTopicDetector.isWeak(stats.getReviews(), recent));
    }

    private static Double round(Double value) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(QUALITY_SCALE, RoundingMode.HALF_UP).doubleValue();
    }

    private static Integer percent(Double fraction) {
        return fraction == null ? null : (int) Math.round(fraction * 100);
    }
}
