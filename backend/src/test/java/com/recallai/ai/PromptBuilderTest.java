package com.recallai.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class PromptBuilderTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void flashcardPromptGroundsTheModelInTheMaterialAndDemandsJson() {
        AiPrompt prompt = new FlashcardPromptBuilder(mapper).build("  Cells have mitochondria.  ", 12);

        assertThat(prompt.operation()).isEqualTo(AiOperation.FLASHCARDS);
        assertThat(prompt.promptVersion()).isEqualTo("v1");
        assertThat(prompt.system()).contains("ONLY the study material", "No duplicate", "No prose");
        assertThat(prompt.messages()).hasSize(1);
        assertThat(prompt.messages().get(0).role()).isEqualTo(AiPrompt.Role.USER);
        assertThat(prompt.messages().get(0).content())
                .contains("up to 12 flashcards")
                .contains("<study_material>\nCells have mitochondria.\n</study_material>");
        assertThat(prompt.outputSchema().get("properties").get("cards").get("items").get("required"))
                .hasSize(5);
        assertThat(prompt.outputSchema().get("additionalProperties").asBoolean()).isFalse();
    }

    @Test
    void quizSchemaPinsFourOptionsAndIndexRange() {
        AiPrompt prompt = new QuizPromptBuilder(mapper).build("Material", 5);
        var question = prompt.outputSchema().get("properties").get("questions").get("items").get("properties");

        assertThat(question.get("options").get("minItems").asInt()).isEqualTo(4);
        assertThat(question.get("options").get("maxItems").asInt()).isEqualTo(4);
        assertThat(question.get("correctAnswer").get("minimum").asInt()).isZero();
        assertThat(question.get("correctAnswer").get("maximum").asInt()).isEqualTo(3);
        assertThat(prompt.system()).contains("exactly four options", "Exactly one option is correct");
    }

    @Test
    void correctivePromptTruncatesLongRejectedReplies() {
        PromptService service = new PromptService(new FlashcardPromptBuilder(mapper), new QuizPromptBuilder(mapper));
        AiPrompt original = service.flashcards("Material", 5);

        AiPrompt corrective = service.corrective(original, "x".repeat(10_000), java.util.List.of("bad"));

        assertThat(corrective.messages().get(1).content()).hasSize(PromptService.MAX_ECHOED_REPLY_CHARS);
        assertThat(corrective.messages().get(2).content()).contains("- bad");
    }
}
