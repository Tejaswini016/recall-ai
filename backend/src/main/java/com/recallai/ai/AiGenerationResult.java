package com.recallai.ai;

import java.util.List;

/**
 * Outcome of a generation request.
 *
 * @param items    validated items (flashcards or quiz questions)
 * @param cached   true when served from the content-hash cache without calling the model
 * @param retries  corrective re-asks that were needed (0 when cached or right first time)
 */
public record AiGenerationResult<T>(List<T> items, boolean cached, int retries) {
}
