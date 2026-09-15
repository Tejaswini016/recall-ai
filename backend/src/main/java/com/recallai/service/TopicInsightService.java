package com.recallai.service;

import com.recallai.config.AnalyticsProperties;
import com.recallai.dto.TopicCategory;
import com.recallai.dto.TopicInsightResponse;
import com.recallai.repository.QuizAttemptAnswerRepository;
import com.recallai.repository.ReviewHistoryRepository;
import com.recallai.repository.TopicQuizStats;
import com.recallai.repository.TopicReviewStats;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Weak-topic detection over everything the student has done: every flashcard review and every
 * quiz answer contributes one attempt to its topic. The two sources are aggregated in SQL and
 * merged here by case-insensitive topic key, then {@link TopicCategorizer} labels each topic.
 */
@Service
public class TopicInsightService {

    private final ReviewHistoryRepository reviewHistoryRepository;
    private final QuizAttemptAnswerRepository quizAttemptAnswerRepository;
    private final DeckService deckService;
    private final AnalyticsProperties properties;

    public TopicInsightService(ReviewHistoryRepository reviewHistoryRepository,
                               QuizAttemptAnswerRepository quizAttemptAnswerRepository, DeckService deckService,
                               AnalyticsProperties properties) {
        this.reviewHistoryRepository = reviewHistoryRepository;
        this.quizAttemptAnswerRepository = quizAttemptAnswerRepository;
        this.deckService = deckService;
        this.properties = properties;
    }

    /** Every studied topic, most urgent first (critical, weak, good, strong, then unrated). */
    @Transactional(readOnly = true)
    public List<TopicInsightResponse> insights(Long userId, Long deckId) {
        if (deckId != null) {
            deckService.getOwnedDeck(userId, deckId);
        }
        Map<String, Accumulator> byTopic = new LinkedHashMap<>();
        for (TopicReviewStats row : reviewHistoryRepository.topicReviewStats(userId, deckId)) {
            Accumulator acc = byTopic.computeIfAbsent(key(row.getTopic()), k -> new Accumulator(row.getTopic()));
            acc.cardCount = row.getCardCount();
            acc.cardReviews = row.getReviews();
            acc.cardSuccesses = row.getSuccessful();
            acc.touch(row.getLastReviewedAt());
        }
        for (TopicQuizStats row : quizAttemptAnswerRepository.topicQuizStats(userId, deckId)) {
            Accumulator acc = byTopic.computeIfAbsent(key(row.getTopic()), k -> new Accumulator(row.getTopic()));
            acc.quizAnswers = row.getAnswers();
            acc.quizCorrect = row.getCorrect();
            acc.touch(row.getLastAnsweredAt());
        }
        return byTopic.values().stream()
                .map(this::toResponse)
                .sorted(Comparator.comparing(TopicInsightResponse::category)
                        .thenComparingInt(TopicInsightResponse::accuracyPercent)
                        .thenComparing(TopicInsightResponse::attempts, Comparator.reverseOrder())
                        .thenComparing(TopicInsightResponse::topic, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** Topics that need work: critical and weak only. */
    @Transactional(readOnly = true)
    public List<TopicInsightResponse> weakTopics(Long userId, Long deckId) {
        return insights(userId, deckId).stream()
                .filter(t -> t.category() == TopicCategory.CRITICAL || t.category() == TopicCategory.WEAK)
                .toList();
    }

    private TopicInsightResponse toResponse(Accumulator acc) {
        long attempts = acc.cardReviews + acc.quizAnswers;
        long successes = acc.cardSuccesses + acc.quizCorrect;
        int accuracy = TopicCategorizer.percent(successes, attempts);
        TopicCategory category = TopicCategorizer.categorize(attempts, accuracy, properties.weakTopicMinReviews());
        return new TopicInsightResponse(acc.topic, category, accuracy, attempts, attempts - successes,
                acc.cardCount, acc.cardReviews, acc.cardSuccesses, acc.quizAnswers, acc.quizCorrect,
                acc.lastStudiedAt, TopicCategorizer.recommendedAction(category));
    }

    static String key(String topic) {
        return topic.strip().toLowerCase(Locale.ROOT);
    }

    private static final class Accumulator {
        final String topic;
        long cardCount;
        long cardReviews;
        long cardSuccesses;
        long quizAnswers;
        long quizCorrect;
        Instant lastStudiedAt;

        Accumulator(String topic) {
            this.topic = topic.strip();
        }

        void touch(Instant at) {
            if (at != null && (lastStudiedAt == null || at.isAfter(lastStudiedAt))) {
                lastStudiedAt = at;
            }
        }
    }
}
