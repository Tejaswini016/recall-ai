package com.recallai.dto;

import java.time.Instant;
import java.util.List;

public record DeckResponse(
        Long id,
        String name,
        String description,
        String subject,
        List<String> tags,
        long cardCount,
        long dueCount,
        long masteredCount,
        int progressPercent,
        Instant createdAt,
        Instant updatedAt) {
}
