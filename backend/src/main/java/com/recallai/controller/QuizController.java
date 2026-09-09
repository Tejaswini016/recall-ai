package com.recallai.controller;

import com.recallai.dto.PageResponse;
import com.recallai.dto.QuizAttemptRequest;
import com.recallai.dto.QuizAttemptResponse;
import com.recallai.dto.QuizAttemptSummaryResponse;
import com.recallai.dto.QuizResponse;
import com.recallai.dto.QuizSummaryResponse;
import com.recallai.security.AuthenticatedUser;
import com.recallai.service.QuizService;
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
@RequestMapping("/api/quizzes")
@Tag(name = "Quizzes", description = "Take quizzes, submit attempts and review results")
@SecurityRequirement(name = "bearerAuth")
public class QuizController {

    private final QuizService quizService;

    public QuizController(QuizService quizService) {
        this.quizService = quizService;
    }

    @GetMapping
    @Operation(summary = "List the user's quizzes, optionally for one deck, with attempt statistics")
    public PageResponse<QuizSummaryResponse> list(@AuthenticationPrincipal AuthenticatedUser user,
                                                  @RequestParam(required = false) Long deckId,
                                                  @RequestParam(defaultValue = "0") @Min(0) int page,
                                                  @RequestParam(defaultValue = "" + Paging.DEFAULT_SIZE)
                                                  @Min(1) @Max(Paging.MAX_SIZE) int size) {
        return quizService.list(user.id(), deckId, Paging.of(page, size));
    }

    @GetMapping("/{quizId}")
    @Operation(summary = "Get a quiz to take (correct answers are not included)")
    public QuizResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long quizId) {
        return quizService.get(user.id(), quizId);
    }

    @DeleteMapping("/{quizId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a quiz and its attempts")
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long quizId) {
        quizService.delete(user.id(), quizId);
    }

    @PostMapping("/{quizId}/attempts")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Submit answers; returns the score plus the correct answer and explanation per question")
    public QuizAttemptResponse submit(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long quizId,
                                      @Valid @RequestBody QuizAttemptRequest request) {
        return quizService.submitAttempt(user.id(), quizId, request);
    }

    @GetMapping("/{quizId}/attempts")
    @Operation(summary = "Previous attempts at a quiz, newest first")
    public List<QuizAttemptSummaryResponse> attempts(@AuthenticationPrincipal AuthenticatedUser user,
                                                     @PathVariable Long quizId) {
        return quizService.listAttempts(user.id(), quizId);
    }

    @GetMapping("/{quizId}/attempts/{attemptId}")
    @Operation(summary = "Full result of one attempt")
    public QuizAttemptResponse attempt(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long quizId,
                                       @PathVariable Long attemptId) {
        return quizService.getAttempt(user.id(), quizId, attemptId);
    }
}
