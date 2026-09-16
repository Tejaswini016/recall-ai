package com.recallai.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AiResponseValidatorTest {

    private final AiResponseValidator validator = new AiResponseValidator(new ObjectMapper());

    private static final String VALID_CARDS = """
            {"cards": [
              {"question": "What is ATP?", "answer": "Adenosine triphosphate.",
               "explanation": "It stores energy in cells.", "topic": "Cell energy", "tags": ["Energy", "cells", "energy"]},
              {"question": "Where is ATP made?", "answer": "In mitochondria.",
               "explanation": "", "topic": "Cell energy", "tags": []}
            ]}
            """;

    private static final String VALID_QUIZ = """
            {"questions": [
              {"question": "Which organelle produces ATP?", "options": ["Nucleus", "Mitochondrion", "Ribosome", "Golgi"],
               "correctAnswer": 1, "explanation": "Mitochondria run cellular respiration."}
            ]}
            """;

    @Nested
    @DisplayName("flashcards")
    class Flashcards {

        @Test
        void validResponseIsNormalized() {
            List<GeneratedFlashcard> cards = validator.validateFlashcards(VALID_CARDS, 10);

            assertThat(cards).hasSize(2);
            assertThat(cards.get(0).question()).isEqualTo("What is ATP?");
            assertThat(cards.get(0).tags()).containsExactly("energy", "cells");
            assertThat(cards.get(1).explanation()).isNull();
            assertThat(cards.get(1).tags()).isEmpty();
        }

        @Test
        void codeFencedJsonIsTolerated() {
            assertThat(validator.validateFlashcards("```json\n" + VALID_CARDS + "\n```", 10)).hasSize(2);
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "Sure! Here are your cards:", "{not json", "[]", "\"just a string\"", "null"})
        void nonJsonOrNonObjectIsRejected(String raw) {
            assertThatThrownBy(() -> validator.validateFlashcards(raw, 10))
                    .isInstanceOf(AiInvalidResponseException.class);
        }

        @Test
        void proseAroundJsonIsRejectedRatherThanGuessed() {
            String raw = "Here you go:\n" + VALID_CARDS + "\nLet me know if you need more.";

            assertThatThrownBy(() -> validator.validateFlashcards(raw, 10))
                    .isInstanceOf(AiInvalidResponseException.class)
                    .hasMessageContaining("not valid JSON");
        }

        @Test
        void missingOrWrongTypedCardsArrayIsRejected() {
            assertThatThrownBy(() -> validator.validateFlashcards("{\"flashcards\": []}", 10))
                    .hasMessageContaining("\"cards\" is missing");
            assertThatThrownBy(() -> validator.validateFlashcards("{\"cards\": {}}", 10))
                    .hasMessageContaining("must be an array");
            assertThatThrownBy(() -> validator.validateFlashcards("{\"cards\": []}", 10))
                    .hasMessageContaining("empty");
        }

        @Test
        void missingFieldsAreReportedPerCard() {
            String raw = """
                    {"cards": [{"question": "Q only"}, {"answer": "A only", "question": ""}]}
                    """;

            assertThatThrownBy(() -> validator.validateFlashcards(raw, 10))
                    .isInstanceOf(AiInvalidResponseException.class)
                    .satisfies(ex -> assertThat(((AiInvalidResponseException) ex).getProblems())
                            .contains("cards[0].answer is missing", "cards[1].question is blank"));
        }

        @Test
        void wrongTypesAreRejected() {
            String raw = """
                    {"cards": [{"question": 42, "answer": ["a"], "tags": "not-a-list", "topic": 7}]}
                    """;

            assertThatThrownBy(() -> validator.validateFlashcards(raw, 10))
                    .satisfies(ex -> assertThat(((AiInvalidResponseException) ex).getProblems())
                            .contains("cards[0].question must be a string",
                                    "cards[0].answer must be a string",
                                    "cards[0].tags must be an array of strings",
                                    "cards[0].topic must be a string"));
        }

        @Test
        void extraCardsBeyondTheRequestedCountAreDroppedNotRejected() {
            List<GeneratedFlashcard> cards = validator.validateFlashcards(VALID_CARDS, 1);

            assertThat(cards).hasSize(1);
            assertThat(cards.get(0).question()).isEqualTo("What is ATP?");
        }

        @Test
        void overlongFieldsAreRejected() {
            String raw = "{\"cards\": [{\"question\": \"" + "x".repeat(2001) + "\", \"answer\": \"a\"}]}";

            assertThatThrownBy(() -> validator.validateFlashcards(raw, 10))
                    .hasMessageContaining("exceeds 2000 characters");
        }

        @Test
        void duplicateQuestionsAreDroppedButAllDuplicatesIsInvalid() {
            String raw = """
                    {"cards": [
                      {"question": "What is ATP?", "answer": "A"},
                      {"question": "what is atp?", "answer": "B"}
                    ]}
                    """;

            assertThat(validator.validateFlashcards(raw, 10)).hasSize(1);
        }

        @Test
        void unknownExtraFieldsAreIgnored() {
            String raw = """
                    {"cards": [{"question": "Q", "answer": "A", "confidence": 0.9}], "note": "extra"}
                    """;

            assertThat(validator.validateFlashcards(raw, 10)).hasSize(1);
        }
    }

    @Nested
    class MistakeCard {

        @Test
        void singleCardIsAccepted() {
            GeneratedFlashcard card = validator.validateMistakeCard("""
                    {"card": {"question": "Q", "answer": "A", "explanation": "E", "topic": "T", "tags": ["X"]}}
                    """);
            assertThat(card.question()).isEqualTo("Q");
            assertThat(card.tags()).containsExactly("x");
        }

        @Test
        void missingOrBrokenCardIsRejected() {
            assertThatThrownBy(() -> validator.validateMistakeCard("{\"cards\": []}")).hasMessageContaining("\"card\" is missing");
            assertThatThrownBy(() -> validator.validateMistakeCard("{\"card\": 3}")).hasMessageContaining("not an object");
            assertThatThrownBy(() -> validator.validateMistakeCard("{\"card\": {\"question\": \"Q\"}}"))
                    .hasMessageContaining("card.answer is missing");
        }
    }

    @Nested
    @DisplayName("quiz")
    class Quiz {

        @Test
        void validQuizIsAccepted() {
            List<GeneratedQuizQuestion> questions = validator.validateQuiz(VALID_QUIZ, 10);

            assertThat(questions).hasSize(1);
            assertThat(questions.get(0).options()).hasSize(4);
            assertThat(questions.get(0).correctAnswer()).isEqualTo(1);
            assertThat(questions.get(0).topic()).isNull();
        }

        @Test
        void topicIsOptionalButMustBeAShortString() {
            String withTopic = VALID_QUIZ.replace("\"correctAnswer\": 1", "\"correctAnswer\": 1, \"topic\": \" Cell energy \"");
            assertThat(validator.validateQuiz(withTopic, 10).get(0).topic()).isEqualTo("Cell energy");

            String numeric = VALID_QUIZ.replace("\"correctAnswer\": 1", "\"correctAnswer\": 1, \"topic\": 7");
            assertThatThrownBy(() -> validator.validateQuiz(numeric, 10)).hasMessageContaining("topic must be a string");

            String tooLong = VALID_QUIZ.replace("\"correctAnswer\": 1",
                    "\"correctAnswer\": 1, \"topic\": \"" + "t".repeat(151) + "\"");
            assertThatThrownBy(() -> validator.validateQuiz(tooLong, 10)).hasMessageContaining("exceeds 150");
        }

        @Test
        void wrongOptionCountIsRejected() {
            String three = VALID_QUIZ.replace(", \"Golgi\"", "");
            String five = VALID_QUIZ.replace("\"Golgi\"", "\"Golgi\", \"Lysosome\"");

            assertThatThrownBy(() -> validator.validateQuiz(three, 10)).hasMessageContaining("exactly 4");
            assertThatThrownBy(() -> validator.validateQuiz(five, 10)).hasMessageContaining("exactly 4");
        }

        @Test
        void duplicateOrBlankOptionsAreRejected() {
            String duplicate = VALID_QUIZ.replace("\"Golgi\"", "\"nucleus\"");
            String blank = VALID_QUIZ.replace("\"Golgi\"", "\"  \"");

            assertThatThrownBy(() -> validator.validateQuiz(duplicate, 10)).hasMessageContaining("duplicate options");
            assertThatThrownBy(() -> validator.validateQuiz(blank, 10)).hasMessageContaining("non-empty strings");
        }

        @ParameterizedTest
        @ValueSource(strings = {"-1", "4", "1.5", "\"1\"", "null"})
        void invalidCorrectAnswerIsRejected(String value) {
            String raw = VALID_QUIZ.replace("\"correctAnswer\": 1", "\"correctAnswer\": " + value);

            assertThatThrownBy(() -> validator.validateQuiz(raw, 10))
                    .isInstanceOf(AiInvalidResponseException.class)
                    .hasMessageContaining("correctAnswer");
        }

        @Test
        void missingOrBlankExplanationIsRejected() {
            String blank = VALID_QUIZ.replace("\"Mitochondria run cellular respiration.\"", "\"\"");
            String missing = VALID_QUIZ.replace(", \"explanation\": \"Mitochondria run cellular respiration.\"", "");

            assertThatThrownBy(() -> validator.validateQuiz(blank, 10)).hasMessageContaining("explanation is blank");
            assertThatThrownBy(() -> validator.validateQuiz(missing, 10)).hasMessageContaining("explanation is missing");
        }

        @Test
        void emptyQuestionsIsRejected() {
            assertThatThrownBy(() -> validator.validateQuiz("{\"questions\": []}", 10)).hasMessageContaining("empty");
        }

        @Test
        void manyProblemsAreCappedInTheMessage() {
            StringBuilder raw = new StringBuilder("{\"questions\": [");
            for (int i = 0; i < 15; i++) {
                raw.append(i > 0 ? "," : "").append("{\"question\": \"\"}");
            }
            raw.append("]}");

            assertThatThrownBy(() -> validator.validateQuiz(raw.toString(), 20))
                    .satisfies(ex -> assertThat(((AiInvalidResponseException) ex).getProblems())
                            .hasSize(11)
                            .last().asString().startsWith("... and"));
        }
    }
}
