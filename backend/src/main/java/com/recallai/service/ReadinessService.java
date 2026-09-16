package com.recallai.service;

import com.recallai.dto.ReadinessResponse;
import com.recallai.dto.UpcomingReviewsResponse;
import com.recallai.entity.MockExamStatus;
import com.recallai.repository.CardRepository;
import com.recallai.repository.DueCount;
import com.recallai.repository.MockExamRepository;
import com.recallai.repository.ReviewHistoryRepository;
import java.sql.Date;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gathers the measured inputs for {@link ReadinessCalculator} and the upcoming-review outlook. */
@Service
public class ReadinessService {

    private static final int RETENTION_WINDOW_DAYS = 30;
    private static final int ACTIVE_WINDOW_DAYS = 7;

    private final TopicInsightService topicInsightService;
    private final ReviewHistoryRepository reviewHistoryRepository;
    private final CardRepository cardRepository;
    private final MockExamRepository mockExamRepository;
    private final Clock clock;

    public ReadinessService(TopicInsightService topicInsightService, ReviewHistoryRepository reviewHistoryRepository,
                            CardRepository cardRepository, MockExamRepository mockExamRepository, Clock clock) {
        this.topicInsightService = topicInsightService;
        this.reviewHistoryRepository = reviewHistoryRepository;
        this.cardRepository = cardRepository;
        this.mockExamRepository = mockExamRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ReadinessResponse readiness(Long userId) {
        LocalDate today = LocalDate.now(clock);
        Double retention = reviewHistoryRepository.retentionSince(userId,
                clock.instant().minus(Duration.ofDays(RETENTION_WINDOW_DAYS)));
        LocalDate weekStart = today.minusDays(ACTIVE_WINDOW_DAYS - 1L);
        int activeDays = (int) reviewHistoryRepository.findDistinctReviewDays(userId).stream()
                .map(Date::toLocalDate)
                .filter(day -> !day.isBefore(weekStart) && !day.isAfter(today))
                .count();
        Double mockAverage = mockExamRepository.averagePercent(userId, MockExamStatus.SUBMITTED);
        ReadinessCalculator.Input input = new ReadinessCalculator.Input(
                topicInsightService.insights(userId, null),
                retention == null ? null : (int) Math.round(retention * 100),
                activeDays,
                cardRepository.countOverdue(userId, today),
                cardRepository.countByDeckUserId(userId),
                mockAverage == null ? null : (int) Math.round(mockAverage));
        return ReadinessCalculator.compute(input, clock.instant());
    }

    /** Cards due on each of the next {@code days} days (today included), gaps filled with zero. */
    @Transactional(readOnly = true)
    public UpcomingReviewsResponse upcoming(Long userId, int days) {
        LocalDate today = LocalDate.now(clock);
        LocalDate end = today.plusDays(days - 1L);
        Map<LocalDate, Long> byDay = cardRepository.dueByDay(userId, today, end).stream()
                .collect(Collectors.toMap(d -> d.getDay().toLocalDate(), DueCount::getCards));
        List<UpcomingReviewsResponse.Day> series = new ArrayList<>();
        for (LocalDate day = today; !day.isAfter(end); day = day.plusDays(1)) {
            series.add(new UpcomingReviewsResponse.Day(day, byDay.getOrDefault(day, 0L)));
        }
        return new UpcomingReviewsResponse(today, cardRepository.countOverdue(userId, today),
                byDay.getOrDefault(today, 0L), series);
    }
}
