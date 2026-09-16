package com.recallai.dto;

import com.recallai.entity.ExamDifficulty;
import com.recallai.entity.MockExam;
import com.recallai.entity.MockExamStatus;
import java.time.Instant;

public record MockExamSummaryResponse(
        Long id,
        String title,
        String topic,
        ExamDifficulty difficulty,
        int questionCount,
        int durationMinutes,
        MockExamStatus status,
        int score,
        Integer percent,
        Integer timeTakenSeconds,
        boolean timedOut,
        Instant startedAt,
        Instant submittedAt) {

    public static MockExamSummaryResponse from(MockExam exam) {
        boolean submitted = exam.getStatus() == MockExamStatus.SUBMITTED;
        return new MockExamSummaryResponse(exam.getId(), exam.getTitle(), exam.getTopic(), exam.getDifficulty(),
                exam.getTotalQuestions(), exam.getDurationMinutes(), exam.getStatus(), exam.getScore(),
                submitted ? exam.percent() : null, exam.getTimeTakenSeconds(), exam.isTimedOut(), exam.getStartedAt(),
                exam.getSubmittedAt());
    }
}
