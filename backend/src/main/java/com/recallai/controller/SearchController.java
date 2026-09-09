package com.recallai.controller;

import com.recallai.dto.SearchResponse;
import com.recallai.security.AuthenticatedUser;
import com.recallai.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Search", description = "Global search and tag discovery")
@SecurityRequirement(name = "bearerAuth")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/search")
    @Operation(summary = "Search decks and cards at once (top 10 of each)")
    public SearchResponse search(@AuthenticationPrincipal AuthenticatedUser user, @RequestParam String q) {
        return searchService.search(user.id(), q);
    }

    @GetMapping("/tags")
    @Operation(summary = "All tags used on the user's decks and cards")
    public List<String> tags(@AuthenticationPrincipal AuthenticatedUser user) {
        return searchService.tags(user.id());
    }
}
