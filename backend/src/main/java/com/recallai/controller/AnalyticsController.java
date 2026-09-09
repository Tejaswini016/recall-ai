package com.recallai.controller;

import com.recallai.dto.ActivityPoint;
import com.recallai.dto.AnalyticsSummaryResponse;
import com.recallai.dto.MasteryPoint;
import com.recallai.dto.TopicPerformanceResponse;
import com.recallai.security.AuthenticatedUser;
import com.recallai.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
@Tag(name = "Analytics", description = "Dashboard statistics, charts and weak-topic detection")
@SecurityRequirement(name = "bearerAuth")
public class AnalyticsController {

    static final int MAX_DAYS = 365;

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/summary")
    @Operation(summary = "Headline numbers: due, reviewed, mastered, streaks, recall, quizzes")
    public AnalyticsSummaryResponse summary(@AuthenticationPrincipal AuthenticatedUser user) {
        return analyticsService.summary(user.id());
    }

    @GetMapping("/activity")
    @Operation(summary = "Reviews per day with success counts, average quality and retention")
    public List<ActivityPoint> activity(@AuthenticationPrincipal AuthenticatedUser user,
                                        @RequestParam(defaultValue = "30") @Min(1) @Max(MAX_DAYS) int days) {
        return analyticsService.activity(user.id(), days);
    }

    @GetMapping("/mastery")
    @Operation(summary = "Cumulative cards mastered per day")
    public List<MasteryPoint> mastery(@AuthenticationPrincipal AuthenticatedUser user,
                                      @RequestParam(defaultValue = "90") @Min(1) @Max(MAX_DAYS) int days) {
        return analyticsService.mastery(user.id(), days);
    }

    @GetMapping("/topics")
    @Operation(summary = "Performance per topic, weakest first, optionally for one deck")
    public List<TopicPerformanceResponse> topics(@AuthenticationPrincipal AuthenticatedUser user,
                                                 @RequestParam(required = false) Long deckId) {
        return analyticsService.topics(user.id(), deckId);
    }

    @GetMapping("/weak-topics")
    @Operation(summary = "Topics the student consistently struggles with (deterministic rule over review history)")
    public List<TopicPerformanceResponse> weakTopics(@AuthenticationPrincipal AuthenticatedUser user,
                                                     @RequestParam(required = false) Long deckId) {
        return analyticsService.weakTopics(user.id(), deckId);
    }
}
