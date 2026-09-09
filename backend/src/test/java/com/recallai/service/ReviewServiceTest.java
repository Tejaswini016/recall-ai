package com.recallai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recallai.config.ReviewProperties;
import com.recallai.dto.ReviewResponse;
import com.recallai.entity.Card;
import com.recallai.entity.Deck;
import com.recallai.entity.ReviewHistory;
import com.recallai.entity.User;
import com.recallai.exception.ResourceNotFoundException;
import com.recallai.repository.CardRepository;
import com.recallai.repository.ReviewHistoryRepository;
import com.recallai.repository.UserRepository;
import com.recallai.scheduler.Sm2Service;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-09T12:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 9);
    private static final Long USER_ID = 1L;
    private static final Long CARD_ID = 10L;

    @Mock
    private CardRepository cardRepository;
    @Mock
    private ReviewHistoryRepository reviewHistoryRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DeckService deckService;

    private ReviewService reviewService;
    private User user;
    private Card card;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        reviewService = new ReviewService(cardRepository, reviewHistoryRepository, userRepository, deckService,
                new Sm2Service(clock), new ReviewProperties(21), clock);
        user = new User("Ada", "ada@example.com", "hash");
        ReflectionTestUtils.setField(user, "id", USER_ID);
        Deck deck = new Deck(user, "Deck", null, null, List.of());
        card = new Card(deck, "Q", "A", null, null, List.of(), TODAY);
        ReflectionTestUtils.setField(card, "id", CARD_ID);
    }

    @Test
    void successfulReviewUpdatesCardAndRecordsHistory() {
        card.applySchedule(new BigDecimal("2.50"), 6, 2, TODAY);
        when(cardRepository.findOwnedForUpdate(CARD_ID, USER_ID)).thenReturn(Optional.of(card));
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
        when(cardRepository.countDue(USER_ID, TODAY)).thenReturn(4L);

        ReviewResponse response = reviewService.review(USER_ID, CARD_ID, 5);

        assertThat(card.getIntervalDays()).isEqualTo(15);
        assertThat(card.getRepetitions()).isEqualTo(3);
        assertThat(card.getEaseFactor()).isEqualByComparingTo("2.60");
        assertThat(card.getDueDate()).isEqualTo(TODAY.plusDays(15));

        ArgumentCaptor<ReviewHistory> saved = ArgumentCaptor.forClass(ReviewHistory.class);
        verify(reviewHistoryRepository).save(saved.capture());
        ReviewHistory history = saved.getValue();
        assertThat(history.getQualityScore()).isEqualTo(5);
        assertThat(history.getPreviousInterval()).isEqualTo(6);
        assertThat(history.getNewInterval()).isEqualTo(15);
        assertThat(history.getPreviousEaseFactor()).isEqualByComparingTo("2.50");
        assertThat(history.getNewEaseFactor()).isEqualByComparingTo("2.60");
        assertThat(history.getReviewedAt()).isEqualTo(NOW);
        assertThat(history.getUser()).isSameAs(user);
        assertThat(history.getCard()).isSameAs(card);

        assertThat(response.successful()).isTrue();
        assertThat(response.newInterval()).isEqualTo(15);
        assertThat(response.nextDueDate()).isEqualTo(TODAY.plusDays(15));
        assertThat(response.mastered()).isFalse();
        assertThat(response.remainingDue()).isEqualTo(4);
    }

    @Test
    void reachingTheMasteredIntervalIsReported() {
        card.applySchedule(new BigDecimal("2.50"), 15, 3, TODAY);
        when(cardRepository.findOwnedForUpdate(CARD_ID, USER_ID)).thenReturn(Optional.of(card));
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);

        ReviewResponse response = reviewService.review(USER_ID, CARD_ID, 4);

        assertThat(response.newInterval()).isEqualTo(38);
        assertThat(response.mastered()).isTrue();
    }

    @Test
    void failedReviewResetsAndKeepsEase() {
        card.applySchedule(new BigDecimal("2.36"), 38, 4, TODAY);
        when(cardRepository.findOwnedForUpdate(CARD_ID, USER_ID)).thenReturn(Optional.of(card));
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);

        ReviewResponse response = reviewService.review(USER_ID, CARD_ID, 1);

        assertThat(response.successful()).isFalse();
        assertThat(card.getRepetitions()).isZero();
        assertThat(card.getIntervalDays()).isEqualTo(1);
        assertThat(card.getEaseFactor()).isEqualByComparingTo("2.36");
        assertThat(card.getDueDate()).isEqualTo(TODAY.plusDays(1));
    }

    @Test
    void reviewingAnotherUsersCardIsNotFoundAndNothingIsWritten() {
        when(cardRepository.findOwnedForUpdate(CARD_ID, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.review(2L, CARD_ID, 4))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(reviewHistoryRepository, never()).save(any());
    }

    @Test
    void invalidQualityIsRejectedBeforeAnyWrite() {
        when(cardRepository.findOwnedForUpdate(CARD_ID, USER_ID)).thenReturn(Optional.of(card));

        assertThatThrownBy(() -> reviewService.review(USER_ID, CARD_ID, 6))
                .isInstanceOf(IllegalArgumentException.class);
        verify(reviewHistoryRepository, never()).save(any());
        assertThat(card.getRepetitions()).isZero();
    }

    @Test
    void dueQueueScopesToDeckAfterOwnershipCheck() {
        when(cardRepository.findDueInDeck(eq(USER_ID), eq(5L), eq(TODAY), any())).thenReturn(List.of(card));
        when(cardRepository.countDueInDeck(USER_ID, 5L, TODAY)).thenReturn(1L);

        var queue = reviewService.dueQueue(USER_ID, 5L, 50);

        verify(deckService).getOwnedDeck(USER_ID, 5L);
        assertThat(queue.today()).isEqualTo(TODAY);
        assertThat(queue.totalDue()).isEqualTo(1);
        assertThat(queue.cards()).hasSize(1);
        assertThat(queue.cards().get(0).deckName()).isEqualTo("Deck");
        assertThat(queue.cards().get(0).daysOverdue()).isZero();
    }
}
