package com.recallai.controller;

import com.recallai.dto.CreateStudyPlanRequest;
import com.recallai.dto.StudyPlanResponse;
import com.recallai.dto.StudyPlanSummaryResponse;
import com.recallai.dto.StudyPlanTaskResponse;
import com.recallai.dto.TodayPlanResponse;
import com.recallai.dto.UpdatePlanStatusRequest;
import com.recallai.dto.UpdateTaskStatusRequest;
import com.recallai.security.AiRateLimited;
import com.recallai.security.AuthenticatedUser;
import com.recallai.service.StudyPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/study-plans")
@Tag(name = "Study plans", description = "Exam study plans built from your topic performance, with daily tasks")
@SecurityRequirement(name = "bearerAuth")
public class StudyPlanController {

    private final StudyPlanService studyPlanService;

    public StudyPlanController(StudyPlanService studyPlanService) {
        this.studyPlanService = studyPlanService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @AiRateLimited
    @Operation(summary = "Create a plan: the schedule is computed from your weak topics, cards and mistakes; the model adds a summary and advice")
    public StudyPlanResponse create(@AuthenticationPrincipal AuthenticatedUser user,
                                    @Valid @RequestBody CreateStudyPlanRequest request) {
        return studyPlanService.create(user.id(), request);
    }

    @GetMapping
    @Operation(summary = "All plans, newest first, with progress")
    public List<StudyPlanSummaryResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return studyPlanService.list(user.id());
    }

    @GetMapping("/today")
    @Operation(summary = "Today's tasks across active plans plus pending tasks carried over from the last week")
    public TodayPlanResponse today(@AuthenticationPrincipal AuthenticatedUser user) {
        return studyPlanService.today(user.id());
    }

    @GetMapping("/{planId}")
    @Operation(summary = "A plan with every task")
    public StudyPlanResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long planId) {
        return studyPlanService.get(user.id(), planId);
    }

    @PostMapping("/{planId}/regenerate")
    @AiRateLimited
    @Operation(summary = "Rebuild the pending tasks from today's topic performance; completed work is kept")
    public StudyPlanResponse regenerate(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long planId) {
        return studyPlanService.regenerate(user.id(), planId);
    }

    @PatchMapping("/{planId}/tasks/{taskId}")
    @Operation(summary = "Mark a task done, skipped or pending; a plan completes when nothing is pending")
    public StudyPlanTaskResponse updateTask(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long planId,
                                            @PathVariable Long taskId, @Valid @RequestBody UpdateTaskStatusRequest request) {
        return studyPlanService.updateTask(user.id(), planId, taskId, request.status());
    }

    @PatchMapping("/{planId}")
    @Operation(summary = "Change a plan's status (active, completed, archived)")
    public StudyPlanSummaryResponse updateStatus(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long planId,
                                                 @Valid @RequestBody UpdatePlanStatusRequest request) {
        return studyPlanService.updateStatus(user.id(), planId, request.status());
    }

    @DeleteMapping("/{planId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long planId) {
        studyPlanService.delete(user.id(), planId);
    }
}
