package com.recallai.dto;

import java.time.Instant;
import java.util.List;

/** A quiz ready to be taken; answers are not included. */
public record QuizResponse(
        Long id,
        Long deckId,
        String deckName,
        String title,
        int questionCount,
        long attemptCount,
        Integer bestScorePercent,
        Instant createdAt,
        List<QuizQuestionResponse> questions) {
}
