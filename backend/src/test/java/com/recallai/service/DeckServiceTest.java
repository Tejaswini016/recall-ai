package com.recallai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recallai.config.ReviewProperties;
import com.recallai.dto.DeckRequest;
import com.recallai.dto.DeckResponse;
import com.recallai.entity.Deck;
import com.recallai.entity.User;
import com.recallai.exception.ResourceNotFoundException;
import com.recallai.repository.DeckRepository;
import com.recallai.repository.DeckStats;
import com.recallai.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
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
class DeckServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    @Mock
    private DeckRepository deckRepository;
    @Mock
    private UserRepository userRepository;

    private DeckService deckService;
    private User user;

    @BeforeEach
    void setUp() {
        deckService = new DeckService(deckRepository, userRepository, new ReviewProperties(21),
                Clock.fixed(Instant.parse("2026-09-09T12:00:00Z"), ZoneOffset.UTC));
        user = new User("Ada", "ada@example.com", "hash");
        ReflectionTestUtils.setField(user, "id", USER_ID);
    }

    @Test
    void createTrimsFieldsNormalizesTagsAndStartsWithZeroStats() {
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
        when(deckRepository.save(any(Deck.class))).thenAnswer(inv -> withId(inv.getArgument(0), 10L));

        DeckResponse response = deckService.create(USER_ID,
                new DeckRequest("  Biology 101 ", "   ", " Science ", List.of("Cells", "cells", " DNA ")));

        ArgumentCaptor<Deck> saved = ArgumentCaptor.forClass(Deck.class);
        verify(deckRepository).save(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("Biology 101");
        assertThat(saved.getValue().getDescription()).isNull();
        assertThat(saved.getValue().getSubject()).isEqualTo("Science");
        assertThat(saved.getValue().getTags()).containsExactly("cells", "dna");
        assertThat(response.cardCount()).isZero();
        assertThat(response.progressPercent()).isZero();
        verify(deckRepository, never()).findStats(anyList(), any(), anyInt());
    }

    @Test
    void getReturnsStatsAndProgress() {
        Deck deck = withId(new Deck(user, "Deck", null, null, List.of()), 10L);
        when(deckRepository.findByIdAndUserId(10L, USER_ID)).thenReturn(Optional.of(deck));
        when(deckRepository.findStats(List.of(10L), java.time.LocalDate.of(2026, 9, 9), 21))
                .thenReturn(List.of(stats(10L, 8, 3, 2)));

        DeckResponse response = deckService.get(USER_ID, 10L);

        assertThat(response.cardCount()).isEqualTo(8);
        assertThat(response.dueCount()).isEqualTo(3);
        assertThat(response.masteredCount()).isEqualTo(2);
        assertThat(response.progressPercent()).isEqualTo(25);
    }

    @Test
    void anotherUsersDeckIsNotFound() {
        when(deckRepository.findByIdAndUserId(10L, OTHER_USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deckService.get(OTHER_USER_ID, 10L))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> deckService.delete(OTHER_USER_ID, 10L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(deckRepository, never()).delete(any(Deck.class));
    }

    @Test
    void progressPercentRoundsAndHandlesEmptyDeck() {
        assertThat(DeckService.progressPercent(0, 0)).isZero();
        assertThat(DeckService.progressPercent(1, 3)).isEqualTo(33);
        assertThat(DeckService.progressPercent(2, 3)).isEqualTo(67);
        assertThat(DeckService.progressPercent(3, 3)).isEqualTo(100);
    }

    private static Deck withId(Deck deck, Long id) {
        ReflectionTestUtils.setField(deck, "id", id);
        return deck;
    }

    private static DeckStats stats(Long deckId, long cards, long due, long mastered) {
        return new DeckStats() {
            public Long getDeckId() {
                return deckId;
            }

            public long getCardCount() {
                return cards;
            }

            public long getDueCount() {
                return due;
            }

            public long getMasteredCount() {
                return mastered;
            }
        };
    }
}
