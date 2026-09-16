package com.recallai.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

/**
 * Builds the prompt that turns one mistake into one corrective flashcard. {@link #VERSION} is
 * part of the cache key. The card's limits are the flashcard limits so it is stored like any
 * other card.
 */
@Component
public class MistakePromptBuilder {

    public static final String VERSION = "v1";

    private static final String SYSTEM = """
            You write one corrective flashcard for RecallAI, a spaced-repetition study app. The \
            student answered a question wrongly; the card must fix the misunderstanding.
            Rules:
            - Use ONLY the question, answers and explanation supplied. Never introduce facts that are \
            not stated or directly implied by them.
            - "question": a self-contained recall question about the underlying fact or concept, \
            answerable from memory. Do not list multiple-choice options and do not mention the quiz.
            - "answer": the precise correct answer, one to three sentences.
            - "explanation": two to four sentences that say why the correct answer is right and, \
            when the student's answer is given, why that answer was wrong. Address the likely \
            misconception directly. Do not scold.
            - "topic": a short label (two to five words) for the sub-topic; reuse the supplied topic \
            when there is one.
            - "tags": one to three lowercase keywords.
            - Write in the same language as the question.
            Respond with a single JSON object that matches the required schema. No prose, no \
            markdown, no code fences, nothing before or after the JSON.
            """;

    private final JsonNode schema;

    public MistakePromptBuilder(ObjectMapper objectMapper) {
        this.schema = buildSchema(objectMapper);
    }

    public AiPrompt build(MistakeContext mistake) {
        StringBuilder user = new StringBuilder("Write one corrective flashcard for the mistake below.\n\n<mistake>\n");
        user.append("Question: ").append(mistake.question().strip()).append('\n');
        user.append("Student's answer: ")
                .append(mistake.givenAnswer() == null ? "(the student skipped the question)" : mistake.givenAnswer().strip())
                .append('\n');
        user.append("Correct answer: ").append(mistake.correctAnswer().strip()).append('\n');
        if (mistake.explanation() != null && !mistake.explanation().isBlank()) {
            user.append("Explanation: ").append(mistake.explanation().strip()).append('\n');
        }
        if (mistake.topic() != null && !mistake.topic().isBlank()) {
            user.append("Topic: ").append(mistake.topic().strip()).append('\n');
        }
        user.append("</mistake>");
        return AiPrompt.of(AiOperation.MISTAKE_CARD, VERSION, SYSTEM, user.toString(), schema);
    }

    public JsonNode schema() {
        return schema;
    }

    private static JsonNode buildSchema(ObjectMapper objectMapper) {
        ObjectNode card = objectMapper.createObjectNode();
        card.put("type", "object");
        card.put("additionalProperties", false);
        ObjectNode props = card.putObject("properties");
        props.putObject("question").put("type", "string").put("maxLength", FlashcardPromptBuilder.MAX_QUESTION_CHARS);
        props.putObject("answer").put("type", "string").put("maxLength", FlashcardPromptBuilder.MAX_ANSWER_CHARS);
        props.putObject("explanation").put("type", "string").put("maxLength", FlashcardPromptBuilder.MAX_EXPLANATION_CHARS);
        props.putObject("topic").put("type", "string").put("maxLength", FlashcardPromptBuilder.MAX_TOPIC_CHARS);
        ObjectNode tags = props.putObject("tags");
        tags.put("type", "array").put("maxItems", FlashcardPromptBuilder.MAX_TAGS);
        tags.putObject("items").put("type", "string").put("maxLength", FlashcardPromptBuilder.MAX_TAG_CHARS);
        card.putArray("required").add("question").add("answer").add("explanation").add("topic").add("tags");

        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "object");
        root.put("additionalProperties", false);
        root.putObject("properties").set("card", card);
        root.putArray("required").add("card");
        return root;
    }
}
