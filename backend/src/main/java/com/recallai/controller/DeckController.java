package com.recallai.controller;

import com.recallai.dto.CardRequest;
import com.recallai.dto.CardResponse;
import com.recallai.dto.DeckRequest;
import com.recallai.dto.DeckResponse;
import com.recallai.dto.PageResponse;
import com.recallai.security.AuthenticatedUser;
import com.recallai.service.CardService;
import com.recallai.service.DeckService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/decks")
@Tag(name = "Decks", description = "Deck management and the cards within a deck")
@SecurityRequirement(name = "bearerAuth")
public class DeckController {

    private final DeckService deckService;
    private final CardService cardService;

    public DeckController(DeckService deckService, CardService cardService) {
        this.deckService = deckService;
        this.cardService = cardService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a deck")
    public DeckResponse create(@AuthenticationPrincipal AuthenticatedUser user,
                               @Valid @RequestBody DeckRequest request) {
        return deckService.create(user.id(), request);
    }

    @GetMapping
    @Operation(summary = "List or search the user's decks",
            description = "q searches name, description, subject and tags; subject and tag are exact filters.")
    public PageResponse<DeckResponse> list(@AuthenticationPrincipal AuthenticatedUser user,
                                           @RequestParam(required = false) String q,
                                           @RequestParam(required = false) String subject,
                                           @RequestParam(required = false) String tag,
                                           @RequestParam(defaultValue = "0") @Min(0) int page,
                                           @RequestParam(defaultValue = "" + Paging.DEFAULT_SIZE)
                                           @Min(1) @Max(Paging.MAX_SIZE) int size) {
        return deckService.search(user.id(), q, subject, tag, Paging.of(page, size));
    }

    @GetMapping("/{deckId}")
    @Operation(summary = "Get a deck with its card statistics")
    public DeckResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long deckId) {
        return deckService.get(user.id(), deckId);
    }

    @PutMapping("/{deckId}")
    @Operation(summary = "Update a deck")
    public DeckResponse update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long deckId,
                               @Valid @RequestBody DeckRequest request) {
        return deckService.update(user.id(), deckId, request);
    }

    @DeleteMapping("/{deckId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a deck and all of its cards")
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long deckId) {
        deckService.delete(user.id(), deckId);
    }

    @PostMapping("/{deckId}/cards")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a card to a deck")
    public CardResponse createCard(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long deckId,
                                   @Valid @RequestBody CardRequest request) {
        return cardService.create(user.id(), deckId, request);
    }

    @GetMapping("/{deckId}/cards")
    @Operation(summary = "List or search the cards in a deck")
    public PageResponse<CardResponse> listCards(@AuthenticationPrincipal AuthenticatedUser user,
                                                @PathVariable Long deckId,
                                                @RequestParam(required = false) String q,
                                                @RequestParam(required = false) String topic,
                                                @RequestParam(required = false) String tag,
                                                @RequestParam(defaultValue = "0") @Min(0) int page,
                                                @RequestParam(defaultValue = "" + Paging.DEFAULT_SIZE)
                                                @Min(1) @Max(Paging.MAX_SIZE) int size) {
        return cardService.searchInDeck(user.id(), deckId, q, topic, tag, Paging.of(page, size));
    }
}
