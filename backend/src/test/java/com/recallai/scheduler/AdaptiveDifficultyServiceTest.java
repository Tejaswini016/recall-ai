package com.recallai.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.recallai.scheduler.AdaptiveDifficultyService.Outcome;
import com.recallai.scheduler.AdaptiveDifficultyService.State;
import org.junit.jupiter.api.Test;

class AdaptiveDifficultyServiceTest {

    private final AdaptiveDifficultyService service = new AdaptiveDifficultyService();

    @Test
    void lapseMovesOneTierHarderAndResetsTheStreak() {
        Outcome outcome = service.evaluate(new State(DifficultyTier.MEDIUM, 2, 0, 5, null), 2, null);

        assertThat(outcome.tier()).isEqualTo(DifficultyTier.HARD);
        assertThat(outcome.tierChanged()).isTrue();
        assertThat(outcome.successStreak()).isZero();
        assertThat(outcome.lapseCount()).isEqualTo(1);
        assertThat(outcome.totalReviews()).isEqualTo(6);
    }

    @Test
    void blankMovesTwoTiersHarderAndExpertIsTheCeiling() {
        assertThat(service.evaluate(new State(DifficultyTier.EASY, 0, 0, 0, null), 0, null).tier())
                .isEqualTo(DifficultyTier.HARD);
        assertThat(service.evaluate(new State(DifficultyTier.HARD, 0, 0, 0, null), 0, null).tier())
                .isEqualTo(DifficultyTier.EXPERT);
        assertThat(service.evaluate(new State(DifficultyTier.EXPERT, 0, 3, 0, null), 1, null).tier())
                .isEqualTo(DifficultyTier.EXPERT);
    }

    @Test
    void threeConfidentSuccessesMoveOneTierEasierThenTheStreakRestarts() {
        State state = new State(DifficultyTier.HARD, 0, 1, 3, null);
        Outcome first = service.evaluate(state, 4, null);
        Outcome second = service.evaluate(from(first), 5, null);
        Outcome third = service.evaluate(from(second), 4, null);

        assertThat(first.tier()).isEqualTo(DifficultyTier.HARD);
        assertThat(second.tier()).isEqualTo(DifficultyTier.HARD);
        assertThat(second.successStreak()).isEqualTo(2);
        assertThat(third.tier()).isEqualTo(DifficultyTier.MEDIUM);
        assertThat(third.tierChanged()).isTrue();
        assertThat(third.successStreak()).isZero();
        assertThat(third.lapseCount()).isEqualTo(1);
    }

    @Test
    void effortfulSuccessKeepsTheTierButExtendsTheStreak() {
        Outcome outcome = service.evaluate(new State(DifficultyTier.MEDIUM, 2, 0, 2, null), 3, null);
        assertThat(outcome.tier()).isEqualTo(DifficultyTier.MEDIUM);
        assertThat(outcome.successStreak()).isEqualTo(3);
        assertThat(outcome.tierChanged()).isFalse();
    }

    @Test
    void slowRecallDoesNotPromoteEvenWhenRatedEasy() {
        State state = new State(DifficultyTier.MEDIUM, 2, 0, 2, 4000);
        Outcome slow = service.evaluate(state, 5, 9000);
        Outcome quick = service.evaluate(state, 5, 3000);

        assertThat(slow.slow()).isTrue();
        assertThat(slow.tier()).isEqualTo(DifficultyTier.MEDIUM);
        assertThat(quick.slow()).isFalse();
        assertThat(quick.tier()).isEqualTo(DifficultyTier.EASY);
    }

    @Test
    void responseTimeIsAnExponentialMovingAverage() {
        assertThat(AdaptiveDifficultyService.nextAverage(null, null)).isNull();
        assertThat(AdaptiveDifficultyService.nextAverage(null, 5000)).isEqualTo(5000);
        assertThat(AdaptiveDifficultyService.nextAverage(4000, null)).isEqualTo(4000);
        assertThat(AdaptiveDifficultyService.nextAverage(4000, 8000)).isEqualTo(5200);
        assertThat(service.evaluate(new State(DifficultyTier.MEDIUM, 0, 0, 0, 4000), 4, 8000).avgResponseMs())
                .isEqualTo(5200);
    }

    @Test
    void multiplierSparesSm2SeedsAndLapsesAndNeverDropsBelowOneDay() {
        assertThat(service.adjustInterval(1, 1, DifficultyTier.EXPERT)).isEqualTo(1);
        assertThat(service.adjustInterval(6, 2, DifficultyTier.EXPERT)).isEqualTo(6);
        assertThat(service.adjustInterval(1, 0, DifficultyTier.EXPERT)).isEqualTo(1);
        assertThat(service.adjustInterval(15, 3, DifficultyTier.MEDIUM)).isEqualTo(15);
        assertThat(service.adjustInterval(15, 3, DifficultyTier.EASY)).isEqualTo(15);
        assertThat(service.adjustInterval(15, 3, DifficultyTier.HARD)).isEqualTo(13);
        assertThat(service.adjustInterval(15, 3, DifficultyTier.EXPERT)).isEqualTo(11);
        assertThat(service.adjustInterval(38, 4, DifficultyTier.EXPERT)).isEqualTo(27);
        assertThat(service.adjustInterval(1, 5, DifficultyTier.EXPERT)).isEqualTo(1);
    }

    @Test
    void tiersStepInOrder() {
        assertThat(DifficultyTier.EASY.easier()).isEqualTo(DifficultyTier.EASY);
        assertThat(DifficultyTier.EASY.harder()).isEqualTo(DifficultyTier.MEDIUM);
        assertThat(DifficultyTier.EXPERT.harder()).isEqualTo(DifficultyTier.EXPERT);
        assertThat(DifficultyTier.EXPERT.easier()).isEqualTo(DifficultyTier.HARD);
    }

    private static State from(Outcome outcome) {
        return new State(outcome.tier(), outcome.successStreak(), outcome.lapseCount(), outcome.totalReviews(),
                outcome.avgResponseMs());
    }
}
