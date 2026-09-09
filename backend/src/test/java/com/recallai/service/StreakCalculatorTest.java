package com.recallai.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.recallai.service.StreakCalculator.StreakSummary;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class StreakCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 9);

    @Test
    void noActivityMeansNoStreak() {
        assertThat(StreakCalculator.calculate(List.of(), TODAY)).isEqualTo(StreakSummary.NONE);
        assertThat(StreakCalculator.calculate(null, TODAY)).isEqualTo(StreakSummary.NONE);
    }

    @Test
    void reviewingTodayStartsAStreakOfOne() {
        StreakSummary summary = StreakCalculator.calculate(List.of(TODAY), TODAY);

        assertThat(summary.currentStreak()).isEqualTo(1);
        assertThat(summary.longestStreak()).isEqualTo(1);
        assertThat(summary.lastActiveDate()).isEqualTo(TODAY);
    }

    @Test
    void streakEndingYesterdayIsStillCurrent() {
        StreakSummary summary = StreakCalculator.calculate(
                List.of(TODAY.minusDays(1), TODAY.minusDays(2), TODAY.minusDays(3)), TODAY);

        assertThat(summary.currentStreak()).isEqualTo(3);
        assertThat(summary.lastActiveDate()).isEqualTo(TODAY.minusDays(1));
    }

    @Test
    void streakEndingTwoDaysAgoIsBroken() {
        StreakSummary summary = StreakCalculator.calculate(
                List.of(TODAY.minusDays(2), TODAY.minusDays(3), TODAY.minusDays(4)), TODAY);

        assertThat(summary.currentStreak()).isZero();
        assertThat(summary.longestStreak()).isEqualTo(3);
        assertThat(summary.lastActiveDate()).isEqualTo(TODAY.minusDays(2));
    }

    @Test
    void consecutiveDaysAreCheckedNotJustCounted() {
        // Five active days but with a gap: the run is 3, not 5.
        StreakSummary summary = StreakCalculator.calculate(
                List.of(TODAY, TODAY.minusDays(1), TODAY.minusDays(2), TODAY.minusDays(4), TODAY.minusDays(5)),
                TODAY);

        assertThat(summary.currentStreak()).isEqualTo(3);
        assertThat(summary.longestStreak()).isEqualTo(3);
    }

    @Test
    void longestStreakCanBeInThePast() {
        StreakSummary summary = StreakCalculator.calculate(List.of(
                TODAY,
                TODAY.minusDays(10), TODAY.minusDays(11), TODAY.minusDays(12), TODAY.minusDays(13),
                TODAY.minusDays(14)), TODAY);

        assertThat(summary.currentStreak()).isEqualTo(1);
        assertThat(summary.longestStreak()).isEqualTo(5);
    }

    @Test
    void duplicatesAndOrderDoNotMatter() {
        StreakSummary summary = StreakCalculator.calculate(
                List.of(TODAY.minusDays(1), TODAY, TODAY, TODAY.minusDays(1), TODAY.minusDays(2)), TODAY);

        assertThat(summary.currentStreak()).isEqualTo(3);
        assertThat(summary.longestStreak()).isEqualTo(3);
    }

    @Test
    void streakCrossesMonthAndYearBoundaries() {
        LocalDate newYear = LocalDate.of(2027, 1, 1);
        StreakSummary summary = StreakCalculator.calculate(
                List.of(LocalDate.of(2026, 12, 30), LocalDate.of(2026, 12, 31), newYear), newYear);

        assertThat(summary.currentStreak()).isEqualTo(3);
    }

    @Test
    void futureDatesAreIgnoredForTheCurrentStreak() {
        // Clock skew should not manufacture a streak.
        StreakSummary summary = StreakCalculator.calculate(List.of(TODAY.plusDays(1), TODAY.plusDays(2)), TODAY);

        assertThat(summary.currentStreak()).isZero();
        assertThat(summary.longestStreak()).isEqualTo(2);
    }
}
