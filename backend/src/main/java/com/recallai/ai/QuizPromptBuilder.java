package com.recallai.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

/**
 * Builds the multiple-choice quiz prompt. {@link #VERSION} is part of the cache key.
 */
@Component
public class QuizPromptBuilder {

    public static final String VERSION = "v2";

    static final int OPTION_COUNT = 4;
    static final int MAX_QUESTION_CHARS = 2000;
    static final int MAX_OPTION_CHARS = 500;
    static final int MAX_EXPLANATION_CHARS = 5000;
    static final int MAX_TOPIC_CHARS = 150;

    private static final String SYSTEM = """
            You write multiple-choice quiz questions for RecallAI, a study app.

            Rules:
            - Use ONLY the study material supplied by the user. Every correct answer must be \
            supported by the material. Never invent facts.
            - If the material supports fewer questions than requested, return fewer. Never pad.
            - Each question tests one fact, concept or relationship and has exactly four options.
            - Exactly one option is correct. The other three are plausible but clearly wrong given \
            the material: same category, similar length and style, no jokes, no "all of the above" \
            or "none of the above".
            - Vary the position of the correct option across questions.
            - "correctAnswer" is the zero-based index of the correct option (0, 1, 2 or 3).
            - "explanation": one to three sentences saying why the correct option is right and, \
            briefly, why the others are not. A student who answered wrongly reads this.
            - "topic": a short label (two to five words) naming the sub-topic the question tests. \
            Reuse the same label for questions on the same sub-topic so results can be grouped. When \
            the material is a list of flashcards that already name a topic, reuse that name exactly.
            - No duplicate or near-duplicate questions.
            - Write in the same language as the study material.

            Respond with a single JSON object that matches the required schema. No prose, no \
            markdown, no code fences, nothing before or after the JSON.
            """;

    private final ObjectMapper objectMapper;
    private final JsonNode schema;

    public QuizPromptBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.schema = buildSchema();
    }

    public AiPrompt build(String material, int maxQuestions) {
        String user = "Create up to " + maxQuestions + " multiple-choice questions from the study material below.\n\n"
                + "<study_material>\n" + material.strip() + "\n</study_material>";
        return AiPrompt.of(AiOperation.QUIZ, VERSION, SYSTEM, user, schema);
    }

    public JsonNode schema() {
        return schema;
    }

    private JsonNode buildSchema() {
        ObjectNode question = objectMapper.createObjectNode();
        question.put("type", "object");
        question.put("additionalProperties", false);
        ObjectNode props = question.putObject("properties");
        props.putObject("question").put("type", "string").put("maxLength", MAX_QUESTION_CHARS);
        ObjectNode options = props.putObject("options");
        options.put("type", "array").put("minItems", OPTION_COUNT).put("maxItems", OPTION_COUNT);
        options.putObject("items").put("type", "string").put("maxLength", MAX_OPTION_CHARS);
        props.putObject("correctAnswer").put("type", "integer").put("minimum", 0).put("maximum", OPTION_COUNT - 1);
        props.putObject("explanation").put("type", "string").put("maxLength", MAX_EXPLANATION_CHARS);
        props.putObject("topic").put("type", "string").put("maxLength", MAX_TOPIC_CHARS);
        question.putArray("required").add("question").add("options").add("correctAnswer").add("explanation")
                .add("topic");

        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "object");
        root.put("additionalProperties", false);
        ObjectNode questions = root.putObject("properties").putObject("questions");
        questions.put("type", "array");
        questions.set("items", question);
        root.putArray("required").add("questions");
        return root;
    }
}
