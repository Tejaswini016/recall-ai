package com.recallai.dto;

import java.time.Instant;
import java.util.List;

/**
 * An estimated exam readiness score. It is a heuristic built from measured numbers, not a
 * prediction of an exam result, and the UI must label it as an estimate.
 *
 * @param estimate   always true; kept in the payload so no client can mistake this for a fact
 * @param confidence how much evidence the estimate rests on: LOW, MEDIUM or HIGH
 */
public record ReadinessResponse(
        int score,
        String label,
        boolean estimate,
        String confidence,
        long evidenceAttempts,
        List<Component> components,
        String recommendation,
        Instant computedAt) {

    /**
     * @param score  0-100 for this component
     * @param weight share of the overall score, in percent, after redistribution of unavailable components
     */
    public record Component(String key, String label, int score, int weight, String detail, boolean available) {
    }
}
