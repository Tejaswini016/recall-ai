package com.recallai.service;

import com.recallai.config.ReviewProperties;
import com.recallai.dto.DeckRequest;
import com.recallai.dto.DeckResponse;
import com.recallai.dto.PageResponse;
import com.recallai.entity.Deck;
import com.recallai.exception.ResourceNotFoundException;
import com.recallai.repository.DeckRepository;
import com.recallai.repository.DeckStats;
import com.recallai.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeckService {

    private static final Logger log = LoggerFactory.getLogger(DeckService.class);

    private final DeckRepository deckRepository;
    private final UserRepository userRepository;
    private final ReviewProperties reviewProperties;
    private final Clock clock;

    public DeckService(DeckRepository deckRepository, UserRepository userRepository,
                       ReviewProperties reviewProperties, Clock clock) {
        this.deckRepository = deckRepository;
        this.userRepository = userRepository;
        this.reviewProperties = reviewProperties;
        this.clock = clock;
    }

    @Transactional
    public DeckResponse create(Long userId, DeckRequest request) {
        Deck deck = new Deck(
                userRepository.getReferenceById(userId),
                request.name().trim(),
                blankToNull(request.description()),
                blankToNull(request.subject()),
                TagNormalizer.normalize(request.tags()));
        deck = deckRepository.save(deck);
        log.info("User {} created deck {}", userId, deck.getId());
        return toResponse(deck, emptyStats(deck.getId()));
    }

    @Transactional
    public DeckResponse update(Long userId, Long deckId, DeckRequest request) {
        Deck deck = getOwnedDeck(userId, deckId);
        deck.update(request.name().trim(), blankToNull(request.description()),
                blankToNull(request.subject()), TagNormalizer.normalize(request.tags()));
        return toResponse(deck, statsFor(List.of(deck)).get(deck.getId()));
    }

    @Transactional
    public void delete(Long userId, Long deckId) {
        Deck deck = getOwnedDeck(userId, deckId);
        // Cards, quizzes and review history cascade at the database level.
        deckRepository.delete(deck);
        log.info("User {} deleted deck {}", userId, deckId);
    }

    @Transactional(readOnly = true)
    public DeckResponse get(Long userId, Long deckId) {
        Deck deck = getOwnedDeck(userId, deckId);
        return toResponse(deck, statsFor(List.of(deck)).get(deck.getId()));
    }

    @Transactional(readOnly = true)
    public PageResponse<DeckResponse> search(Long userId, String query, String subject, String tag,
                                             Pageable pageable) {
        Page<Deck> page = deckRepository.search(userId, blankToNull(query), blankToNull(subject),
                TagNormalizer.normalizeOne(tag), pageable);
        return toResponsePage(page);
    }

    @Transactional(readOnly = true)
    public List<DeckResponse> toResponses(List<Deck> decks) {
        Map<Long, DeckStats> stats = statsFor(decks);
        return decks.stream().map(deck -> toResponse(deck, stats.get(deck.getId()))).toList();
    }

    /**
     * Loads a deck the user owns, or throws not-found. Returning 404 rather than 403 for
     * another user's deck avoids confirming that the id exists.
     */
    @Transactional(readOnly = true)
    public Deck getOwnedDeck(Long userId, Long deckId) {
        return deckRepository.findByIdAndUserId(deckId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Deck", deckId));
    }

    private PageResponse<DeckResponse> toResponsePage(Page<Deck> page) {
        Map<Long, DeckStats> stats = statsFor(page.getContent());
        return PageResponse.from(page, deck -> toResponse(deck, stats.get(deck.getId())));
    }

    private Map<Long, DeckStats> statsFor(List<Deck> decks) {
        if (decks.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = decks.stream().map(Deck::getId).toList();
        return deckRepository.findStats(ids, LocalDate.now(clock), reviewProperties.masteredIntervalDays())
                .stream()
                .collect(Collectors.toMap(DeckStats::getDeckId, Function.identity()));
    }

    static DeckResponse toResponse(Deck deck, DeckStats stats) {
        long cards = stats == null ? 0 : stats.getCardCount();
        long due = stats == null ? 0 : stats.getDueCount();
        long mastered = stats == null ? 0 : stats.getMasteredCount();
        return new DeckResponse(
                deck.getId(),
                deck.getName(),
                deck.getDescription(),
                deck.getSubject(),
                deck.getTags(),
                cards,
                due,
                mastered,
                progressPercent(mastered, cards),
                deck.getCreatedAt(),
                deck.getUpdatedAt());
    }

    static int progressPercent(long mastered, long total) {
        if (total == 0) {
            return 0;
        }
        return (int) Math.round(mastered * 100.0 / total);
    }

    private static DeckStats emptyStats(Long deckId) {
        return new DeckStats() {
            @Override
            public Long getDeckId() {
                return deckId;
            }

            @Override
            public long getCardCount() {
                return 0;
            }

            @Override
            public long getDueCount() {
                return 0;
            }

            @Override
            public long getMasteredCount() {
                return 0;
            }
        };
    }

    static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
