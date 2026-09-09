package com.recallai.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recallai.entity.Card;
import com.recallai.entity.Deck;
import com.recallai.entity.User;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class Sm2ServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 9);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-09T12:00:00Z"), ZoneOffset.UTC);

    private final Sm2Service sm2 = new Sm2Service(CLOCK);

    private static Sm2State state(String ease, int interval, int repetitions) {
        return new Sm2State(new BigDecimal(ease), interval, repetitions);
    }

    private static BigDecimal ease(String value) {
        return new BigDecimal(value);
    }

    @Nested
    @DisplayName("first successful review")
    class FirstReview {

        @ParameterizedTest(name = "quality {0}")
        @ValueSource(ints = {3, 4, 5})
        void schedulesOneDayLaterWithOneRepetition(int quality) {
            ReviewResult result = sm2.schedule(Sm2State.fresh(), quality, TODAY);

            assertThat(result.successful()).isTrue();
            assertThat(result.newInterval()).isEqualTo(1);
            assertThat(result.newRepetitions()).isEqualTo(1);
            assertThat(result.nextDueDate()).isEqualTo(TODAY.plusDays(1));
        }

        @Test
        void recordsPreviousValues() {
            ReviewResult result = sm2.schedule(Sm2State.fresh(), 4, TODAY);

            assertThat(result.quality()).isEqualTo(4);
            assertThat(result.previousEaseFactor()).isEqualByComparingTo("2.50");
            assertThat(result.previousInterval()).isZero();
            assertThat(result.previousRepetitions()).isZero();
        }
    }

    @Nested
    @DisplayName("second and subsequent successful reviews")
    class LaterReviews {

        @Test
        void secondReviewIsSixDays() {
            ReviewResult result = sm2.schedule(state("2.50", 1, 1), 4, TODAY);

            assertThat(result.newInterval()).isEqualTo(6);
            assertThat(result.newRepetitions()).isEqualTo(2);
            assertThat(result.nextDueDate()).isEqualTo(TODAY.plusDays(6));
        }

        @Test
        void thirdReviewMultipliesIntervalByEase() {
            ReviewResult result = sm2.schedule(state("2.50", 6, 2), 4, TODAY);

            assertThat(result.newInterval()).isEqualTo(15);
            assertThat(result.newRepetitions()).isEqualTo(3);
            assertThat(result.nextDueDate()).isEqualTo(TODAY.plusDays(15));
        }

        @Test
        void intervalUsesEaseBeforeThisReviewsAdjustment() {
            // 6 × 2.50 = 15 with the old ease; 6 × 2.60 = 15.6 → 16 if the new ease were used.
            ReviewResult result = sm2.schedule(state("2.50", 6, 2), 5, TODAY);

            assertThat(result.newInterval()).isEqualTo(15);
            assertThat(result.newEaseFactor()).isEqualByComparingTo("2.60");
        }

        @ParameterizedTest(name = "interval {0} × ease {1} → {2}")
        @CsvSource({
                "6, 2.36, 14",     // 14.16 rounds down
                "7, 2.36, 17",     // 16.52 rounds up
                "10, 1.30, 13",
                "15, 2.50, 38",    // 37.5 rounds half up
                "100, 2.70, 270",
        })
        void intervalRoundsHalfUp(int interval, String ease, int expected) {
            ReviewResult result = sm2.schedule(state(ease, interval, 5), 4, TODAY);

            assertThat(result.newInterval()).isEqualTo(expected);
        }

        @Test
        void intervalAlwaysGrowsOnSuccessEvenAtMinimumEase() {
            // 1 × 1.30 would round back to 1; the interval must still advance.
            ReviewResult result = sm2.schedule(state("1.30", 1, 2), 3, TODAY);

            assertThat(result.newInterval()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("failed reviews (quality 0, 1, 2)")
    class FailedReviews {

        @ParameterizedTest(name = "quality {0}")
        @ValueSource(ints = {0, 1, 2})
        void resetRepetitionsAndRelearnTomorrowKeepingEase(int quality) {
            ReviewResult result = sm2.schedule(state("2.36", 38, 4), quality, TODAY);

            assertThat(result.successful()).isFalse();
            assertThat(result.newRepetitions()).isZero();
            assertThat(result.newInterval()).isEqualTo(1);
            assertThat(result.nextDueDate()).isEqualTo(TODAY.plusDays(1));
            assertThat(result.newEaseFactor()).isEqualByComparingTo("2.36");
            assertThat(result.previousInterval()).isEqualTo(38);
            assertThat(result.previousRepetitions()).isEqualTo(4);
        }

        @Test
        void failingAFreshCardIsAlsoDueTomorrow() {
            ReviewResult result = sm2.schedule(Sm2State.fresh(), 0, TODAY);

            assertThat(result.newInterval()).isEqualTo(1);
            assertThat(result.newRepetitions()).isZero();
            assertThat(result.newEaseFactor()).isEqualByComparingTo("2.50");
        }

        @Test
        void afterALapseTheCardRestartsTheIntervalLadder() {
            ReviewResult lapse = sm2.schedule(state("2.50", 38, 4), 1, TODAY);
            ReviewResult first = sm2.schedule(lapse.newState(), 4, TODAY.plusDays(1));
            ReviewResult second = sm2.schedule(first.newState(), 4, TODAY.plusDays(2));

            assertThat(first.newInterval()).isEqualTo(1);
            assertThat(second.newInterval()).isEqualTo(6);
        }
    }

    @Nested
    @DisplayName("ease factor")
    class EaseFactor {

        @ParameterizedTest(name = "quality {0}: 2.50 → {1}")
        @CsvSource({
                "5, 2.60",   // +0.10
                "4, 2.50",   // unchanged
                "3, 2.36",   // -0.14
        })
        void successAdjustsEaseByTheSm2Formula(int quality, String expected) {
            ReviewResult result = sm2.schedule(Sm2State.fresh(), quality, TODAY);

            assertThat(result.newEaseFactor()).isEqualByComparingTo(expected);
        }

        @Test
        void easeNeverDropsBelowMinimum() {
            ReviewResult fromMinimum = sm2.schedule(state("1.30", 6, 2), 3, TODAY);
            ReviewResult justAbove = sm2.schedule(state("1.40", 6, 2), 3, TODAY);

            assertThat(fromMinimum.newEaseFactor()).isEqualByComparingTo("1.30");
            assertThat(justAbove.newEaseFactor()).isEqualByComparingTo("1.30");
        }

        @Test
        void easeBelowMinimumInStorageIsClampedBeforeUse() {
            ReviewResult result = sm2.schedule(state("1.10", 6, 2), 4, TODAY);

            assertThat(result.previousEaseFactor()).isEqualByComparingTo("1.30");
            assertThat(result.newEaseFactor()).isEqualByComparingTo("1.30");
            assertThat(result.newInterval()).isEqualTo(8);
        }

        @Test
        void easeHasNoUpperBoundAndKeepsTwoDecimals() {
            ReviewResult result = sm2.schedule(state("3.15", 50, 6), 5, TODAY);

            assertThat(result.newEaseFactor()).isEqualByComparingTo("3.25");
            assertThat(result.newEaseFactor().scale()).isEqualTo(2);
        }

        @Test
        void repeatedHardRecallsDriveEaseDownToTheFloor() {
            Sm2State state = Sm2State.fresh();
            for (int i = 0; i < 10; i++) {
                state = sm2.schedule(state, 3, TODAY).newState();
            }
            assertThat(state.easeFactor()).isEqualByComparingTo("1.30");
        }
    }

    @Nested
    @DisplayName("interval progression and due dates")
    class Progression {

        @Test
        void steadyGoodRecallsFollowTheClassicSm2Ladder() {
            List<Integer> expectedIntervals = List.of(1, 6, 15, 38, 95, 238);
            Sm2State state = Sm2State.fresh();
            LocalDate reviewDay = TODAY;

            for (int expected : expectedIntervals) {
                ReviewResult result = sm2.schedule(state, 4, reviewDay);
                assertThat(result.newInterval()).isEqualTo(expected);
                assertThat(result.nextDueDate()).isEqualTo(reviewDay.plusDays(expected));
                assertThat(result.newEaseFactor()).isEqualByComparingTo("2.50");
                state = result.newState();
                reviewDay = result.nextDueDate();
            }
            assertThat(state.repetitions()).isEqualTo(6);
            assertThat(reviewDay).isEqualTo(TODAY.plusDays(1 + 6 + 15 + 38 + 95 + 238));
        }

        @Test
        void perfectRecallsGrowFasterThanGoodOnes() {
            Sm2State good = Sm2State.fresh();
            Sm2State perfect = Sm2State.fresh();
            for (int i = 0; i < 5; i++) {
                good = sm2.schedule(good, 4, TODAY).newState();
                perfect = sm2.schedule(perfect, 5, TODAY).newState();
            }
            assertThat(perfect.easeFactor()).isEqualByComparingTo("3.00");
            assertThat(perfect.intervalDays()).isGreaterThan(good.intervalDays());
        }

        @Test
        void dueDateCrossesMonthAndYearBoundaries() {
            ReviewResult result = sm2.schedule(state("2.50", 38, 4), 4, LocalDate.of(2026, 12, 20));

            assertThat(result.newInterval()).isEqualTo(95);
            assertThat(result.nextDueDate()).isEqualTo(LocalDate.of(2027, 3, 25));
        }
    }

    @Nested
    @DisplayName("input validation")
    class Validation {

        @ParameterizedTest
        @ValueSource(ints = {-1, 6, 42})
        void rejectsQualityOutsideZeroToFive(int quality) {
            assertThatThrownBy(() -> sm2.schedule(Sm2State.fresh(), quality, TODAY))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("between 0 and 5");
        }

        @Test
        void stateRejectsNegativeValues() {
            assertThatThrownBy(() -> new Sm2State(ease("2.50"), -1, 0)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new Sm2State(ease("2.50"), 0, -1)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new Sm2State(null, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void isPureAndDeterministic() {
            Sm2State state = state("2.36", 15, 3);
            ReviewResult first = sm2.schedule(state, 5, TODAY);
            ReviewResult second = sm2.schedule(state, 5, TODAY);

            assertThat(first).isEqualTo(second);
            assertThat(state).isEqualTo(state("2.36", 15, 3));
        }
    }

    @Nested
    @DisplayName("card adapter")
    class CardAdapter {

        @Test
        void readsStateFromCardAndUsesClockForToday() {
            Deck deck = new Deck(new User("Ada", "ada@example.com", "hash"), "Deck", null, null, List.of());
            Card card = new Card(deck, "Q", "A", null, null, List.of(), TODAY);
            card.applySchedule(ease("2.36"), 6, 2, TODAY);

            ReviewResult result = sm2.calculateNextReview(card, 5);

            assertThat(result.previousEaseFactor()).isEqualByComparingTo("2.36");
            assertThat(result.newInterval()).isEqualTo(14);
            assertThat(result.newRepetitions()).isEqualTo(3);
            assertThat(result.newEaseFactor()).isEqualByComparingTo("2.46");
            assertThat(result.nextDueDate()).isEqualTo(TODAY.plusDays(14));
        }

        @Test
        void freshCardMatchesFreshState() {
            Deck deck = new Deck(new User("Ada", "ada@example.com", "hash"), "Deck", null, null, List.of());
            Card card = new Card(deck, "Q", "A", null, null, List.of(), TODAY);

            ReviewResult fromCard = sm2.calculateNextReview(card, 4);
            ReviewResult fromState = sm2.schedule(Sm2State.fresh(), 4, TODAY);

            assertThat(fromCard).isEqualTo(fromState);
        }
    }
}
