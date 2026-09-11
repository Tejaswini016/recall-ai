package com.recallai.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class DemoClaudeClientTest {

    private static final String NOTES = """
            Photosynthesis is the process by which plants convert light energy into chemical energy.
            It takes place in the chloroplasts of leaf cells. Chlorophyll is the green pigment that
            absorbs light. The light-dependent reactions produce ATP and NADPH. The Calvin cycle
            uses ATP and NADPH to fix carbon dioxide. Short line.
            """;

    private final ObjectMapper mapper = new ObjectMapper();
    private final DemoClaudeClient client = new DemoClaudeClient(mapper);
    private final AiResponseValidator validator = new AiResponseValidator(mapper);
    private final PromptService prompts = new PromptService(new FlashcardPromptBuilder(mapper), new QuizPromptBuilder(mapper));

    @Test
    void flashcardsPassTheRealValidatorAndAreLabelledDemo() {
        AiCompletion completion = client.complete(prompts.flashcards(NOTES, 4));

        List<GeneratedFlashcard> cards = validator.validateFlashcards(completion.text(), 4);
        assertThat(cards).hasSize(4);
        assertThat(cards).allSatisfy(card -> {
            assertThat(card.tags()).containsExactly("demo");
            assertThat(card.topic()).isEqualTo("Demo notes");
            assertThat(card.explanation()).contains("Demo mode");
        });
        assertThat(cards.get(0).question()).isEqualTo("What is Photosynthesis?");
        assertThat(cards.get(0).answer()).startsWith("The process by which plants convert");
    }

    @Test
    void quizQuestionsPassTheRealValidator() {
        AiCompletion completion = client.complete(prompts.quiz(NOTES, 10));

        List<GeneratedQuizQuestion> questions = validator.validateQuiz(completion.text(), 10);
        assertThat(questions).isNotEmpty();
        assertThat(questions).allSatisfy(q -> {
            assertThat(q.options()).hasSize(4);
            assertThat(q.correctAnswer()).isBetween(0, 3);
            assertThat(q.explanation()).contains("Your notes say");
        });
        // The correct option is the "rest" of the matched sentence, placed at the reported index.
        assertThat(questions.get(0).options().get(questions.get(0).correctAnswer()))
                .startsWith("The process by which plants convert");
    }

    @Test
    void isDeterministicAndRespectsTheRequestedCount() {
        String first = client.complete(prompts.flashcards(NOTES, 2)).text();
        String second = client.complete(prompts.flashcards(NOTES, 2)).text();

        assertThat(first).isEqualTo(second);
        assertThat(validator.validateFlashcards(first, 2)).hasSize(2);
    }

    @Test
    void neverReturnsAnEmptyResultForThinMaterial() {
        AiCompletion cards = client.complete(prompts.flashcards("Too short.", 5));
        AiCompletion quiz = client.complete(prompts.quiz("Too short.", 5));

        assertThat(validator.validateFlashcards(cards.text(), 5)).hasSize(1);
        assertThat(validator.validateQuiz(quiz.text(), 5)).hasSize(1);
    }

    @Test
    void sentenceSplittingDropsFragmentsAndDuplicates() {
        List<String> sentences = DemoClaudeClient.sentences("One two three four five six. Short one. One two three four five six.");

        assertThat(sentences).containsExactly("One two three four five six.");
    }
}
