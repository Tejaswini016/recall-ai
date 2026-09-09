package com.recallai.service;

import com.recallai.dto.CardRequest;
import com.recallai.dto.CardResponse;
import com.recallai.dto.PageResponse;
import com.recallai.entity.Card;
import com.recallai.entity.Deck;
import com.recallai.exception.ResourceNotFoundException;
import com.recallai.repository.CardRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CardService {

    private static final Logger log = LoggerFactory.getLogger(CardService.class);

    private final CardRepository cardRepository;
    private final DeckService deckService;
    private final Clock clock;

    public CardService(CardRepository cardRepository, DeckService deckService, Clock clock) {
        this.cardRepository = cardRepository;
        this.deckService = deckService;
        this.clock = clock;
    }

    @Transactional
    public CardResponse create(Long userId, Long deckId, CardRequest request) {
        Deck deck = deckService.getOwnedDeck(userId, deckId);
        Card card = cardRepository.save(newCard(deck, request));
        log.info("User {} added card {} to deck {}", userId, card.getId(), deckId);
        return CardResponse.from(card);
    }

    /** Bulk insert used by AI generation; all cards land in one transaction or none do. */
    @Transactional
    public List<CardResponse> createAll(Long userId, Long deckId, List<CardRequest> requests) {
        Deck deck = deckService.getOwnedDeck(userId, deckId);
        List<Card> cards = requests.stream().map(request -> newCard(deck, request)).toList();
        List<Card> saved = cardRepository.saveAll(cards);
        log.info("User {} added {} cards to deck {}", userId, saved.size(), deckId);
        return saved.stream().map(CardResponse::from).toList();
    }

    @Transactional
    public CardResponse update(Long userId, Long cardId, CardRequest request) {
        Card card = getOwnedCard(userId, cardId);
        card.updateContent(request.question().trim(), request.answer().trim(),
                DeckService.blankToNull(request.explanation()), DeckService.blankToNull(request.topic()),
                TagNormalizer.normalize(request.tags()));
        return CardResponse.from(card);
    }

    @Transactional
    public void delete(Long userId, Long cardId) {
        Card card = getOwnedCard(userId, cardId);
        cardRepository.delete(card);
        log.info("User {} deleted card {}", userId, cardId);
    }

    @Transactional(readOnly = true)
    public CardResponse get(Long userId, Long cardId) {
        return CardResponse.from(getOwnedCard(userId, cardId));
    }

    @Transactional(readOnly = true)
    public PageResponse<CardResponse> searchInDeck(Long userId, Long deckId, String query, String topic,
                                                   String tag, Pageable pageable) {
        deckService.getOwnedDeck(userId, deckId);
        return PageResponse.from(
                cardRepository.search(userId, deckId, DeckService.blankToNull(query),
                        DeckService.blankToNull(topic), TagNormalizer.normalizeOne(tag), pageable),
                CardResponse::from);
    }

    @Transactional(readOnly = true)
    public PageResponse<CardResponse> searchAll(Long userId, String query, String topic, String tag,
                                                Pageable pageable) {
        return PageResponse.from(
                cardRepository.search(userId, null, DeckService.blankToNull(query),
                        DeckService.blankToNull(topic), TagNormalizer.normalizeOne(tag), pageable),
                CardResponse::from);
    }

    @Transactional(readOnly = true)
    public Card getOwnedCard(Long userId, Long cardId) {
        return cardRepository.findByIdAndDeckUserId(cardId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Card", cardId));
    }

    private Card newCard(Deck deck, CardRequest request) {
        // New cards are due immediately so they enter the review queue on creation day.
        return new Card(deck,
                request.question().trim(),
                request.answer().trim(),
                DeckService.blankToNull(request.explanation()),
                DeckService.blankToNull(request.topic()),
                TagNormalizer.normalize(request.tags()),
                LocalDate.now(clock));
    }
}
