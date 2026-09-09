package com.recallai.ai;

import java.util.List;

/** A validated, normalized flashcard produced by the model. Safe to persist. */
public record GeneratedFlashcard(
        String question,
        String answer,
        String explanation,
        String topic,
        List<String> tags) {
}
