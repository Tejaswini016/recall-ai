package com.recallai.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.recallai.entity.ExamDifficulty;
import com.recallai.entity.ExamQuestionType;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Builds the mock-exam prompt: mixed question types at a requested difficulty, grounded in the
 * student's own cards. {@link #VERSION} is part of the cache key.
 */
@Component
public class MockExamPromptBuilder {

    public static final String VERSION = "v1";
    public static final int MAX_QUESTION_CHARS = 2000;
    public static final int MAX_OPTION_CHARS = 500;
    public static final int MAX_SHORT_ANSWER_CHARS = 300;
    public static final int MAX_ACCEPTABLE_ANSWERS = 6;
    public static final int MAX_EXPLANATION_CHARS = 5000;
    public static final int MAX_TOPIC_CHARS = 150;

    private static final String SYSTEM = """
            You write exam questions for RecallAI, a study app. The student takes them as a timed \
            mock exam, so they must be unambiguous and gradable automatically.
            Rules:
            - Use ONLY the study material supplied by the user. Every correct answer must be \
            supported by the material. Never invent facts.
            - If the material supports fewer questions than requested, return fewer. Never pad.
            - Question types and their fields:
              * "MCQ": exactly four "options", one correct; "correctOption" is its zero-based index. \
            Distractors are plausible, same category and length, never "all/none of the above".
              * "TRUE_FALSE": a single factual statement as the question; "options" is exactly \
            ["True", "False"] and "correctOption" is 0 for true or 1 for false. Avoid negations.
              * "SHORT_ANSWER": a question with a short, definite answer (one to five words: a term, \
            name, number or short phrase). "correctAnswer" is the canonical answer; "acceptableAnswers" \
            lists up to five equivalent spellings or synonyms the student might write. "options" is \
            an empty array and "correctOption" is -1.
            - Fields that do not apply to a type are still present: "correctAnswer" is "" and \
            "acceptableAnswers" is [] for MCQ and true/false questions.
            - Use only the allowed types named by the user and spread the questions across them \
            roughly evenly. Vary the position of the correct option.
            - "explanation": one to three sentences saying why the answer is right and why the \
            alternatives are not. The student reads it after the exam.
            - "topic": a short label (two to five words) for the sub-topic; reuse labels that appear \
            in the material as "Topic:" lines.
            - No duplicate or near-duplicate questions. Write in the language of the material.
            Respond with a single JSON object that matches the required schema. No prose, no \
            markdown, no code fences, nothing before or after the JSON.
            """;

    private final JsonNode schema;

    public MockExamPromptBuilder(ObjectMapper objectMapper) {
        this.schema = buildSchema(objectMapper);
    }

    public AiPrompt build(String material, int count, ExamDifficulty difficulty, Set<ExamQuestionType> types) {
        String typeList = types.stream().map(Enum::name).sorted().collect(Collectors.joining(", "));
        String user = "Create up to " + count + " exam questions from the study material below.\n"
                + "Allowed question types: " + typeList + ".\n"
                + "Difficulty: " + difficulty.name().toLowerCase(Locale.ROOT) + " (" + difficultyHint(difficulty) + ").\n\n"
                + "<study_material>\n" + material.strip() + "\n</study_material>";
        return AiPrompt.of(AiOperation.MOCK_EXAM, VERSION, SYSTEM, user, schema);
    }

    static String difficultyHint(ExamDifficulty difficulty) {
        return switch (difficulty) {
            case EASY -> "direct recall of single facts and definitions";
            case MEDIUM -> "understanding and application; distractors that test common confusions";
            case HARD -> "multi-step reasoning, comparisons and subtle distinctions";
            case EXPERT -> "synthesis across several facts, edge cases and the trickiest plausible distractors";
        };
    }

    private static JsonNode buildSchema(ObjectMapper objectMapper) {
        ObjectNode question = objectMapper.createObjectNode();
        question.put("type", "object");
        question.put("additionalProperties", false);
        ObjectNode props = question.putObject("properties");
        ArrayNode types = props.putObject("type").put("type", "string").putArray("enum");
        for (ExamQuestionType type : ExamQuestionType.values()) {
            types.add(type.name());
        }
        props.putObject("question").put("type", "string").put("maxLength", MAX_QUESTION_CHARS);
        ObjectNode options = props.putObject("options");
        options.put("type", "array").put("maxItems", 4);
        options.putObject("items").put("type", "string").put("maxLength", MAX_OPTION_CHARS);
        props.putObject("correctOption").put("type", "integer").put("minimum", -1).put("maximum", 3);
        props.putObject("correctAnswer").put("type", "string").put("maxLength", MAX_SHORT_ANSWER_CHARS);
        ObjectNode acceptable = props.putObject("acceptableAnswers");
        acceptable.put("type", "array").put("maxItems", MAX_ACCEPTABLE_ANSWERS);
        acceptable.putObject("items").put("type", "string").put("maxLength", MAX_SHORT_ANSWER_CHARS);
        props.putObject("explanation").put("type", "string").put("maxLength", MAX_EXPLANATION_CHARS);
        props.putObject("topic").put("type", "string").put("maxLength", MAX_TOPIC_CHARS);
        question.putArray("required").add("type").add("question").add("options").add("correctOption")
                .add("correctAnswer").add("acceptableAnswers").add("explanation").add("topic");

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
