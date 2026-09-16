package com.recallai.controller;

import com.recallai.dto.ConvertMistakeRequest;
import com.recallai.dto.MistakeFlashcardResponse;
import com.recallai.dto.MistakeResponse;
import com.recallai.dto.MistakeSummaryResponse;
import com.recallai.dto.PageResponse;
import com.recallai.entity.MistakeSource;
import com.recallai.entity.MistakeStatus;
import com.recallai.security.AuthenticatedUser;
import com.recallai.service.MistakeFlashcardService;
import com.recallai.service.MistakeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mistakes")
@Tag(name = "Mistakes", description = "Questions answered wrongly, and turning them into flashcards")
@SecurityRequirement(name = "bearerAuth")
public class MistakeController {

    private final MistakeService mistakeService;
    private final MistakeFlashcardService mistakeFlashcardService;

    public MistakeController(MistakeService mistakeService, MistakeFlashcardService mistakeFlashcardService) {
        this.mistakeService = mistakeService;
        this.mistakeFlashcardService = mistakeFlashcardService;
    }

    @GetMapping
    @Operation(summary = "Mistakes, newest first, optionally filtered by status, source or topic")
    public PageResponse<MistakeResponse> list(@AuthenticationPrincipal AuthenticatedUser user,
                                              @RequestParam(required = false) MistakeStatus status,
                                              @RequestParam(required = false) MistakeSource source,
                                              @RequestParam(required = false) String topic,
                                              @RequestParam(defaultValue = "0") @Min(0) int page,
                                              @RequestParam(defaultValue = "" + Paging.DEFAULT_SIZE)
                                              @Min(1) @Max(Paging.MAX_SIZE) int size) {
        return mistakeService.list(user.id(), status, source, topic, Paging.of(page, size));
    }

    @GetMapping("/summary")
    @Operation(summary = "Open, converted and dismissed counts plus the topics with the most open mistakes")
    public MistakeSummaryResponse summary(@AuthenticationPrincipal AuthenticatedUser user) {
        return mistakeService.summary(user.id());
    }

    @GetMapping("/recent")
    @Operation(summary = "The five most recent open mistakes, for the dashboard")
    public List<MistakeResponse> recent(@AuthenticationPrincipal AuthenticatedUser user) {
        return mistakeService.recentOpen(user.id());
    }

    @GetMapping("/{mistakeId}")
    public MistakeResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long mistakeId) {
        return mistakeService.get(user.id(), mistakeId);
    }

    @PostMapping("/{mistakeId}/flashcard")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Review this mistake: generate an explanatory flashcard, save it due today and mark the mistake converted",
            description = "The model writes the card when configured; otherwise, or if its output is invalid, the card is built from the stored question, correct answer and explanation.")
    public MistakeFlashcardResponse toFlashcard(@AuthenticationPrincipal AuthenticatedUser user,
                                                @PathVariable Long mistakeId,
                                                @Valid @RequestBody(required = false) ConvertMistakeRequest request) {
        return mistakeFlashcardService.convert(user.id(), mistakeId, request == null ? null : request.deckId());
    }

    @PostMapping("/{mistakeId}/dismiss")
    @Operation(summary = "Set a mistake aside without making a card; it reopens if missed again")
    public MistakeResponse dismiss(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long mistakeId) {
        return mistakeService.dismiss(user.id(), mistakeId);
    }

    @PostMapping("/{mistakeId}/reopen")
    public MistakeResponse reopen(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long mistakeId) {
        return mistakeService.reopen(user.id(), mistakeId);
    }

    @DeleteMapping("/{mistakeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long mistakeId) {
        mistakeService.delete(user.id(), mistakeId);
    }
}
