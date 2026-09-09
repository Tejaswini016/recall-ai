package com.recallai.controller;

import com.recallai.dto.CardRequest;
import com.recallai.dto.CardResponse;
import com.recallai.dto.PageResponse;
import com.recallai.security.AuthenticatedUser;
import com.recallai.service.CardService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cards")
@Tag(name = "Cards", description = "Individual card access and cross-deck card search")
@SecurityRequirement(name = "bearerAuth")
public class CardController {

    private final CardService cardService;

    public CardController(CardService cardService) {
        this.cardService = cardService;
    }

    @GetMapping
    @Operation(summary = "Search cards across all of the user's decks")
    public PageResponse<CardResponse> search(@AuthenticationPrincipal AuthenticatedUser user,
                                             @RequestParam(required = false) String q,
                                             @RequestParam(required = false) String topic,
                                             @RequestParam(required = false) String tag,
                                             @RequestParam(defaultValue = "0") @Min(0) int page,
                                             @RequestParam(defaultValue = "" + Paging.DEFAULT_SIZE)
                                             @Min(1) @Max(Paging.MAX_SIZE) int size) {
        return cardService.searchAll(user.id(), q, topic, tag, Paging.of(page, size));
    }

    @GetMapping("/{cardId}")
    @Operation(summary = "Get a card")
    public CardResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long cardId) {
        return cardService.get(user.id(), cardId);
    }

    @PutMapping("/{cardId}")
    @Operation(summary = "Update a card's content (scheduling fields are managed by reviews)")
    public CardResponse update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long cardId,
                               @Valid @RequestBody CardRequest request) {
        return cardService.update(user.id(), cardId, request);
    }

    @DeleteMapping("/{cardId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a card")
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long cardId) {
        cardService.delete(user.id(), cardId);
    }
}
