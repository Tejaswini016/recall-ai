package com.recallai.service;

import com.recallai.config.ReviewProperties;
import com.recallai.dto.DueCardResponse;
import com.recallai.dto.PageResponse;
import com.recallai.dto.ReviewHistoryResponse;
import com.recallai.dto.ReviewQueueResponse;
import com.recallai.dto.ReviewResponse;
import com.recallai.dto.StreakResponse;
import com.recallai.entity.Card;
import com.recallai.entity.ReviewHistory;
import com.recallai.exception.ResourceNotFoundException;
import com.recallai.repository.CardRepository;
import com.recallai.repository.ReviewHistoryRepository;
import com.recallai.repository.UserRepository;
import com.recallai.scheduler.AdaptiveDifficultyService;
import com.recallai.scheduler.ReviewResult;
import com.recallai.scheduler.Sm2Service;
import java.sql.Date;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates a review: load the owned card, ask {@link Sm2Service} for the new
 * schedule, apply it, record history. The algorithm itself lives in the scheduler
 * package and this class never does scheduling arithmetic.
 */
@Service
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    private final CardRepository cardRepository;
    private final ReviewHistoryRepository reviewHistoryRepository;
    private final UserRepository userRepository;
    private final DeckService deckService;
    private final Sm2Service sm2Service;
    private final AdaptiveDifficultyService adaptiveDifficultyService;
    private final ReviewProperties reviewProperties;
    private final Clock clock;

    public ReviewService(CardRepository cardRepository, ReviewHistoryRepository reviewHistoryRepository,
                         UserRepository userRepository, DeckService deckService, Sm2Service sm2Service,
                         AdaptiveDifficultyService adaptiveDifficultyService, ReviewProperties reviewProperties,
                         Clock clock) {
        this.cardRepository = cardRepository;
        this.reviewHistoryRepository = reviewHistoryRepository;
        this.userRepository = userRepository;
        this.deckService = deckService;
        this.sm2Service = sm2Service;
        this.adaptiveDifficultyService = adaptiveDifficultyService;
        this.reviewProperties = reviewProperties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ReviewQueueResponse dueQueue(Long userId, Long deckId, int limit) {
        LocalDate today = LocalDate.now(clock);
        Pageable page = PageRequest.of(0, limit);
        List<Card> cards;
        long total;
        if (deckId == null) {
            cards = cardRepository.findDue(userId, today, page);
            total = cardRepository.countDue(userId, today);
        } else {
            deckService.getOwnedDeck(userId, deckId);
            cards = cardRepository.findDueInDeck(userId, deckId, today, page);
            total = cardRepository.countDueInDeck(userId, deckId, today);
        }
        return new ReviewQueueResponse(today, total,
                cards.stream().map(card -> DueCardResponse.from(card, today)).toList());
    }

    /**
     * A practice session on one topic: every card carrying the topic, hardest first, whether or not
     * it is due. Grading goes through {@link #review} as usual, so SM-2 reschedules practised cards.
     */
    @Transactional(readOnly = true)
    public ReviewQueueResponse practiceQueue(Long userId, String topic, int limit) {
        LocalDate today = LocalDate.now(clock);
        List<Card> cards = cardRepository.findByTopic(userId, topic, PageRequest.of(0, limit));
        long total = cardRepository.countByTopic(userId, topic);
        return new ReviewQueueResponse(today, total,
                cards.stream().map(card -> DueCardResponse.from(card, today)).toList());
    }

    @Transactional
    public ReviewResponse review(Long userId, Long cardId, int quality) {
        return review(userId, cardId, quality, null);
    }

    /**
     * SM-2 first, then adaptive difficulty: the tier is updated from the outcome and may shorten the
     * interval SM-2 produced (hard and expert cards come back sooner). The SM-2 state on the card
     * (ease, repetitions) is exactly what SM-2 computed.
     */
    @Transactional
    public ReviewResponse review(Long userId, Long cardId, int quality, Integer responseMs) {
        Card card = cardRepository.findOwnedForUpdate(cardId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Card", cardId));

        ReviewResult sm2 = sm2Service.calculateNextReview(card, quality);
        AdaptiveDifficultyService.Outcome adaptive =
                adaptiveDifficultyService.evaluate(card.adaptiveState(), quality, responseMs);
        int interval = adaptiveDifficultyService.adjustInterval(sm2.newInterval(), sm2.newRepetitions(),
                adaptive.tier());
        ReviewResult result = interval == sm2.newInterval()
                ? sm2
                : sm2.withInterval(interval, LocalDate.now(clock).plusDays(interval));

        card.applySchedule(result.newEaseFactor(), result.newInterval(), result.newRepetitions(),
                result.nextDueDate());
        card.applyAdaptive(adaptive);
        reviewHistoryRepository.save(new ReviewHistory(card, userRepository.getReferenceById(userId), result,
                clock.instant(), responseMs, adaptive.tier()));

        long remaining = cardRepository.countDue(userId, LocalDate.now(clock));
        log.info("User {} reviewed card {} quality={} interval {}->{} (sm2 {}) due {} tier {}->{}",
                userId, cardId, quality, result.previousInterval(), result.newInterval(), sm2.newInterval(),
                result.nextDueDate(), adaptive.previousTier(), adaptive.tier());

        return new ReviewResponse(
                card.getId(),
                result.quality(),
                result.successful(),
                result.previousEaseFactor(),
                result.newEaseFactor(),
                result.previousInterval(),
                result.newInterval(),
                result.newRepetitions(),
                result.nextDueDate(),
                isMastered(result.newInterval()),
                remaining,
                adaptive.previousTier(),
                adaptive.tier(),
                adaptive.tierChanged(),
                adaptive.successStreak(),
                sm2.newInterval());
    }

    @Transactional(readOnly = true)
    public StreakResponse streak(Long userId) {
        LocalDate today = LocalDate.now(clock);
        List<LocalDate> activeDays = reviewHistoryRepository.findDistinctReviewDays(userId)
                .stream().map(Date::toLocalDate).toList();
        StreakCalculator.StreakSummary summary = StreakCalculator.calculate(activeDays, today);
        Instant startOfToday = today.atStartOfDay(clock.getZone()).toInstant();
        long reviewedToday = reviewHistoryRepository.countByUserIdAndReviewedAtGreaterThanEqual(userId, startOfToday);
        return new StreakResponse(summary.currentStreak(), summary.longestStreak(), summary.lastActiveDate(),
                reviewedToday);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReviewHistoryResponse> history(Long userId, Pageable pageable) {
        return PageResponse.from(reviewHistoryRepository.findByUserIdWithCards(userId, pageable),
                ReviewHistoryResponse::from);
    }

    public boolean isMastered(int intervalDays) {
        return intervalDays >= reviewProperties.masteredIntervalDays();
    }
}
