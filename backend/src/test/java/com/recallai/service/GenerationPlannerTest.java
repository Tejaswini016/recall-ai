package com.recallai.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class GenerationPlannerTest {

    @Test
    void singleChunkGetsTheWholeCountUpToTheCap() {
        assertThat(GenerationPlanner.shares(List.of("abc"), 10, 30)).containsExactly(10);
        assertThat(GenerationPlanner.shares(List.of("abc"), 50, 30)).containsExactly(30);
    }

    @Test
    void sharesAreProportionalToLengthAndAtLeastOne() {
        List<Integer> shares = GenerationPlanner.shares(List.of("x".repeat(900), "x".repeat(100)), 10, 30);

        assertThat(shares).containsExactly(9, 1);
        assertThat(GenerationPlanner.shares(List.of("x".repeat(999), "x"), 2, 30)).containsExactly(2, 1);
    }

    @Test
    void roundsUpSoTheTotalNeverFallsShort() {
        List<Integer> shares = GenerationPlanner.shares(List.of("x".repeat(100), "x".repeat(100), "x".repeat(100)),
                10, 30);

        assertThat(shares).containsExactly(4, 4, 4);
    }
}
