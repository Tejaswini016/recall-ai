package com.recallai.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.recallai.ai.GeneratedFlashcard;
import com.recallai.ai.MistakeContext;
import org.junit.jupiter.api.Test;

class MistakeFlashcardServiceTest {

    @Test
    void fallbackCardUsesTheStoredFactsAndNamesTheWrongAnswer() {
        GeneratedFlashcard card = MistakeFlashcardService.fallbackCard(new MistakeContext(
                " What does DNA polymerase do? ", "Builds proteins", "Copies DNA ", "It replicates DNA.", "Genetics"));

        assertThat(card.question()).isEqualTo("What does DNA polymerase do?");
        assertThat(card.answer()).isEqualTo("Copies DNA");
        assertThat(card.explanation()).isEqualTo("It replicates DNA. You answered \"Builds proteins\" last time.");
        assertThat(card.topic()).isEqualTo("Genetics");
        assertThat(card.tags()).containsExactly("mistake");
    }

    @Test
    void fallbackCardHandlesSkipsAndMissingExplanations() {
        GeneratedFlashcard card = MistakeFlashcardService.fallbackCard(new MistakeContext("Q?", null, "A", null, null));

        assertThat(card.explanation()).isEqualTo("You skipped this question last time.");
        assertThat(card.topic()).isNull();
    }
}
