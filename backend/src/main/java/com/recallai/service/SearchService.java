package com.recallai.service;

import com.recallai.dto.CardResponse;
import com.recallai.dto.DeckResponse;
import com.recallai.dto.SearchResponse;
import com.recallai.repository.CardRepository;
import com.recallai.repository.DeckRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Global search across a user's decks and cards, for the app-wide search box. */
@Service
public class SearchService {

    private static final int RESULTS_PER_TYPE = 10;

    private final DeckRepository deckRepository;
    private final CardRepository cardRepository;
    private final DeckService deckService;

    public SearchService(DeckRepository deckRepository, CardRepository cardRepository, DeckService deckService) {
        this.deckRepository = deckRepository;
        this.cardRepository = cardRepository;
        this.deckService = deckService;
    }

    @Transactional(readOnly = true)
    public SearchResponse search(Long userId, String query) {
        String q = DeckService.blankToNull(query);
        if (q == null) {
            return new SearchResponse(List.of(), List.of());
        }
        PageRequest limit = PageRequest.of(0, RESULTS_PER_TYPE);
        List<DeckResponse> decks = deckService.toResponses(
                deckRepository.search(userId, q, null, null, limit).getContent());
        List<CardResponse> cards = cardRepository.search(userId, null, q, null, null, limit)
                .getContent().stream().map(CardResponse::from).toList();
        return new SearchResponse(decks, cards);
    }

    @Transactional(readOnly = true)
    public List<String> tags(Long userId) {
        return cardRepository.findDistinctTagsByUserId(userId);
    }
}
