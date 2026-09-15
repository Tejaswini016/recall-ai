package com.recallai.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.recallai.dto.TopicCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TopicCategorizerTest {

    @ParameterizedTest
    @CsvSource({
            "100, STRONG",
            "85, STRONG",
            "84, GOOD",
            "70, GOOD",
            "69, WEAK",
            "50, WEAK",
            "49, CRITICAL",
            "0, CRITICAL",
    })
    void thresholdsAreInclusiveAtTheLowerBound(int accuracy, TopicCategory expected) {
        assertThat(TopicCategorizer.categorize(10, accuracy, 3)).isEqualTo(expected);
    }

    @Test
    void tooFewAttemptsIsUnratedWhateverTheAccuracy() {
        assertThat(TopicCategorizer.categorize(2, 0, 3)).isEqualTo(TopicCategory.UNRATED);
        assertThat(TopicCategorizer.categorize(2, 100, 3)).isEqualTo(TopicCategory.UNRATED);
        assertThat(TopicCategorizer.categorize(3, 100, 3)).isEqualTo(TopicCategory.STRONG);
    }

    @Test
    void percentRoundsHalfUpAndHandlesZeroAttempts() {
        assertThat(TopicCategorizer.percent(0, 0)).isZero();
        assertThat(TopicCategorizer.percent(1, 3)).isEqualTo(33);
        assertThat(TopicCategorizer.percent(2, 3)).isEqualTo(67);
        assertThat(TopicCategorizer.percent(1, 8)).isEqualTo(13);
    }

    @Test
    void everyCategoryHasAnAction() {
        for (TopicCategory category : TopicCategory.values()) {
            assertThat(TopicCategorizer.recommendedAction(category)).isNotBlank();
        }
        assertThat(TopicCategorizer.recommendedAction(TopicCategory.CRITICAL)).contains("mistakes");
        assertThat(TopicCategorizer.recommendedAction(TopicCategory.WEAK)).contains("quiz");
    }
}
