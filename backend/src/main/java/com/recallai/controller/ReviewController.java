package com.recallai.controller;

import com.recallai.dto.PageResponse;
import com.recallai.dto.ReviewHistoryResponse;
import com.recallai.dto.ReviewQueueResponse;
import com.recallai.dto.ReviewRequest;
import com.recallai.dto.ReviewResponse;
import com.recallai.dto.StreakResponse;
import com.recallai.security.AuthenticatedUser;
import com.recallai.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reviews")
@Tag(name = "Reviews", description = "Daily review queue, SM-2 grading, streaks and history")
@SecurityRequirement(name = "bearerAuth")
public class ReviewController {

    static final int DEFAULT_QUEUE_LIMIT = 50;
    static final int MAX_QUEUE_LIMIT = 200;

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/due")
    @Operation(summary = "Cards due today, most overdue and weakest first",
            description = "Never includes cards whose due date is in the future. Optionally limited to one deck.")
    public ReviewQueueResponse due(@AuthenticationPrincipal AuthenticatedUser user,
                                   @RequestParam(required = false) Long deckId,
                                   @RequestParam(defaultValue = "" + DEFAULT_QUEUE_LIMIT)
                                   @Min(1) @Max(MAX_QUEUE_LIMIT) int limit) {
        return reviewService.dueQueue(user.id(), deckId, limit);
    }

    @GetMapping("/practice")
    @Operation(summary = "Cards on one topic for targeted practice, hardest first, regardless of due date",
            description = "Cards without a topic belong to their deck's name. Grade them with POST /{cardId} as usual.")
    public ReviewQueueResponse practice(@AuthenticationPrincipal AuthenticatedUser user,
                                        @RequestParam @NotBlank @Size(max = 150) String topic,
                                        @RequestParam(defaultValue = "" + DEFAULT_QUEUE_LIMIT)
                                        @Min(1) @Max(MAX_QUEUE_LIMIT) int limit) {
        return reviewService.practiceQueue(user.id(), topic, limit);
    }

    @PostMapping("/{cardId}")
    @Operation(summary = "Grade a card 0-5 and reschedule it with SM-2 plus adaptive difficulty",
            description = "Send responseMs (time to reveal the answer) so slow recalls do not promote the card to an easier tier.")
    public ReviewResponse review(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long cardId,
                                 @Valid @RequestBody ReviewRequest request) {
        return reviewService.review(user.id(), cardId, request.quality(), request.responseMs());
    }

    @GetMapping("/streak")
    @Operation(summary = "Current and longest daily study streak")
    public StreakResponse streak(@AuthenticationPrincipal AuthenticatedUser user) {
        return reviewService.streak(user.id());
    }

    @GetMapping("/history")
    @Operation(summary = "Review history, newest first")
    public PageResponse<ReviewHistoryResponse> history(@AuthenticationPrincipal AuthenticatedUser user,
                                                       @RequestParam(defaultValue = "0") @Min(0) int page,
                                                       @RequestParam(defaultValue = "" + Paging.DEFAULT_SIZE)
                                                       @Min(1) @Max(Paging.MAX_SIZE) int size) {
        return reviewService.history(user.id(), Paging.of(page, size));
    }
}
