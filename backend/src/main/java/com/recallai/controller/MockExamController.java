package com.recallai.controller;

import com.recallai.dto.CreateMockExamRequest;
import com.recallai.dto.MockExamResponse;
import com.recallai.dto.MockExamResultResponse;
import com.recallai.dto.MockExamStatsResponse;
import com.recallai.dto.MockExamSubmitRequest;
import com.recallai.dto.MockExamSummaryResponse;
import com.recallai.security.AiRateLimited;
import com.recallai.security.AuthenticatedUser;
import com.recallai.service.MockExamService;
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
@RequestMapping("/api/mock-exams")
@Tag(name = "Mock exams", description = "Timed exams with multiple-choice, true/false and short-answer questions")
@SecurityRequirement(name = "bearerAuth")
public class MockExamController {

    private final MockExamService mockExamService;

    public MockExamController(MockExamService mockExamService) {
        this.mockExamService = mockExamService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @AiRateLimited
    @Operation(summary = "Generate an exam from your cards (by topic, deck, or most recent) and start its clock")
    public MockExamResponse create(@AuthenticationPrincipal AuthenticatedUser user,
                                   @Valid @RequestBody CreateMockExamRequest request) {
        return mockExamService.create(user.id(), request);
    }

    @GetMapping
    @Operation(summary = "Exam history, newest first")
    public List<MockExamSummaryResponse> list(@AuthenticationPrincipal AuthenticatedUser user,
                                              @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return mockExamService.list(user.id(), limit);
    }

    @GetMapping("/stats")
    @Operation(summary = "Average, best and latest scores plus the most recent exams")
    public MockExamStatsResponse stats(@AuthenticationPrincipal AuthenticatedUser user) {
        return mockExamService.stats(user.id());
    }

    @GetMapping("/{examId}")
    @Operation(summary = "The exam to take: questions without answers, and the remaining time")
    public MockExamResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long examId) {
        return mockExamService.get(user.id(), examId);
    }

    @PostMapping("/{examId}/submit")
    @Operation(summary = "Submit answers once; grades locally, records mistakes and returns the full result")
    public MockExamResultResponse submit(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long examId,
                                         @Valid @RequestBody MockExamSubmitRequest request) {
        return mockExamService.submit(user.id(), examId, request);
    }

    @GetMapping("/{examId}/results")
    public MockExamResultResponse results(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long examId) {
        return mockExamService.results(user.id(), examId);
    }

    @DeleteMapping("/{examId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long examId) {
        mockExamService.delete(user.id(), examId);
    }
}
