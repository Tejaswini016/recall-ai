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
    private final ReviewProperties reviewProperties;
    private final Clock clock;

    public ReviewService(CardRepository cardRepository, ReviewHistoryRepository reviewHistoryRepository,
                         UserRepository userRepository, DeckService deckService, Sm2Service sm2Service,
                         ReviewProperties reviewProperties, Clock clock) {
        this.cardRepository = cardRepository;
        this.reviewHistoryRepository = reviewHistoryRepository;
        this.userRepository = userRepository;
        this.deckService = deckService;
        this.sm2Service = sm2Service;
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

    @Transactional
    public ReviewResponse review(Long userId, Long cardId, int quality) {
        Card card = cardRepository.findOwnedForUpdate(cardId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Card", cardId));

        ReviewResult result = sm2Service.calculateNextReview(card, quality);
        card.applySchedule(result.newEaseFactor(), result.newInterval(), result.newRepetitions(),
                result.nextDueDate());
        reviewHistoryRepository.save(
                new ReviewHistory(card, userRepository.getReferenceById(userId), result, clock.instant()));

        long remaining = cardRepository.countDue(userId, LocalDate.now(clock));
        log.info("User {} reviewed card {} quality={} interval {}->{} due {}",
                userId, cardId, quality, result.previousInterval(), result.newInterval(), result.nextDueDate());

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
                remaining);
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
