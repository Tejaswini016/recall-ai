package com.recallai.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ShortAnswerGraderTest {

    @Test
    void ignoresCaseAccentsPunctuationArticlesAndSpacing() {
        assertThat(ShortAnswerGrader.isCorrect("  The Mitochondrion. ", "mitochondrion", List.of())).isTrue();
        assertThat(ShortAnswerGrader.isCorrect("café au lait", "Cafe au Lait", List.of())).isTrue();
        assertThat(ShortAnswerGrader.isCorrect("dna-polymerase", "DNA polymerase", List.of())).isTrue();
    }

    @Test
    void acceptsAlternativesAndContainedAnswersButNotEssays() {
        assertThat(ShortAnswerGrader.isCorrect("ATP", "adenosine triphosphate", List.of("ATP"))).isTrue();
        assertThat(ShortAnswerGrader.isCorrect("it is the mitochondrion, obviously", "mitochondrion", List.of())).isTrue();
        assertThat(ShortAnswerGrader.isCorrect(
                "the nucleus holds the DNA while the mitochondrion makes ATP and the ribosome builds proteins",
                "mitochondrion", List.of())).isFalse();
        assertThat(ShortAnswerGrader.isCorrect("mitochondria", "mitochondrion", List.of())).isFalse();
    }

    @Test
    void numbersCompareNumericallyAndBlankIsWrong() {
        assertThat(ShortAnswerGrader.isCorrect("3.0", "3", List.of())).isTrue();
        assertThat(ShortAnswerGrader.isCorrect("1,000", "1000", List.of())).isTrue();
        assertThat(ShortAnswerGrader.isCorrect("4", "3", List.of())).isFalse();
        assertThat(ShortAnswerGrader.isCorrect("   ", "3", List.of())).isFalse();
        assertThat(ShortAnswerGrader.isCorrect(null, "3", List.of())).isFalse();
        assertThat(ShortAnswerGrader.isCorrect("x", null, null)).isFalse();
    }

    @Test
    void normalisationIsStable() {
        assertThat(ShortAnswerGrader.normalize("An  Über-Answer!!")).isEqualTo("uber answer");
        assertThat(ShortAnswerGrader.normalize("3.14 pi")).isEqualTo("3.14 pi");
        assertThat(ShortAnswerGrader.normalize("end.")).isEqualTo("end");
    }
}
