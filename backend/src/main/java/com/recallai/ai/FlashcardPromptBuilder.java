package com.recallai.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

/**
 * Builds the flashcard-generation prompt. {@link #VERSION} is part of the cache key, so any
 * wording change here must bump it or cached responses from the old prompt would be reused.
 */
@Component
public class FlashcardPromptBuilder {

    public static final String VERSION = "v1";

    static final int MAX_QUESTION_CHARS = 2000;
    static final int MAX_ANSWER_CHARS = 5000;
    static final int MAX_EXPLANATION_CHARS = 5000;
    static final int MAX_TOPIC_CHARS = 150;
    static final int MAX_TAGS = 10;
    static final int MAX_TAG_CHARS = 30;

    private static final String SYSTEM = """
            You write flashcards for RecallAI, a spaced-repetition study app.

            Rules:
            - Use ONLY the study material supplied by the user. Never introduce facts, names, numbers \
            or definitions that are not stated or directly implied by it. If the material does not \
            support a card, do not write that card.
            - If the material contains little testable content, return fewer cards. Never pad.
            - Each card tests exactly one fact, definition, relationship or process step.
            - "question": concise, self-contained, answerable from memory (aim for under 25 words). \
            Prefer "What", "Why", "How", "Which" questions over yes/no questions.
            - "answer": the precise answer, one to three sentences. Do not restate the question.
            - "explanation": one to three sentences of context from the material that help the \
            student understand why the answer is right. May be empty if the material offers nothing more.
            - "topic": a short label (two to five words) naming the sub-topic the card belongs to. \
            Reuse the same label for cards on the same sub-topic so they can be grouped.
            - "tags": one to three lowercase keywords.
            - No duplicate or near-duplicate cards. Do not ask the same thing with different wording.
            - Do not write cards about the document itself (its author, formatting, headings, length).
            - Write in the same language as the study material.

            Respond with a single JSON object that matches the required schema. No prose, no \
            markdown, no code fences, nothing before or after the JSON.
            """;

    private final ObjectMapper objectMapper;
    private final JsonNode schema;

    public FlashcardPromptBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.schema = buildSchema();
    }

    public AiPrompt build(String material, int maxCards) {
        String user = "Create up to " + maxCards + " flashcards from the study material below.\n\n"
                + "<study_material>\n" + material.strip() + "\n</study_material>";
        return AiPrompt.of(AiOperation.FLASHCARDS, VERSION, SYSTEM, user, schema);
    }

    public JsonNode schema() {
        return schema;
    }

    private JsonNode buildSchema() {
        ObjectNode card = objectMapper.createObjectNode();
        card.put("type", "object");
        card.put("additionalProperties", false);
        ObjectNode props = card.putObject("properties");
        props.putObject("question").put("type", "string").put("maxLength", MAX_QUESTION_CHARS);
        props.putObject("answer").put("type", "string").put("maxLength", MAX_ANSWER_CHARS);
        props.putObject("explanation").put("type", "string").put("maxLength", MAX_EXPLANATION_CHARS);
        props.putObject("topic").put("type", "string").put("maxLength", MAX_TOPIC_CHARS);
        ObjectNode tags = props.putObject("tags");
        tags.put("type", "array").put("maxItems", MAX_TAGS);
        tags.putObject("items").put("type", "string").put("maxLength", MAX_TAG_CHARS);
        card.putArray("required").add("question").add("answer").add("explanation").add("topic").add("tags");

        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "object");
        root.put("additionalProperties", false);
        ObjectNode cards = root.putObject("properties").putObject("cards");
        cards.put("type", "array");
        cards.set("items", card);
        root.putArray("required").add("cards");
        return root;
    }
}
