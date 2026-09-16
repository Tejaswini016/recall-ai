package com.recallai.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recallai.entity.ExamQuestionType;
import com.recallai.service.TagNormalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Turns raw model text into validated domain objects, or throws
 * {@link AiInvalidResponseException} listing every problem found. Nothing the model says is
 * trusted: types, presence, lengths, ranges and duplicates are all checked here even though
 * the request also carried a JSON schema.
 */
@Component
public class AiResponseValidator {

    private static final int MAX_REPORTED_PROBLEMS = 10;

    private final ObjectMapper objectMapper;

    public AiResponseValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<GeneratedFlashcard> validateFlashcards(String rawText, int maxCards) {
        JsonNode root = parseObject(rawText);
        JsonNode cards = requireArray(root, "cards");
        if (cards.isEmpty()) {
            throw invalid("\"cards\" is empty; at least one card is required");
        }
        // Extra well-formed cards are not an error; a corrective retry would just cost money.
        int cardLimit = Math.min(cards.size(), maxCards);

        List<String> problems = new ArrayList<>();
        List<GeneratedFlashcard> result = new ArrayList<>();
        Set<String> seenQuestions = new HashSet<>();
        for (int i = 0; i < cardLimit; i++) {
            GeneratedFlashcard card = parseCard(cards.get(i), "cards[" + i + "]", problems);
            if (card == null) {
                continue;
            }
            if (!seenQuestions.add(card.question().toLowerCase(Locale.ROOT))) {
                // Duplicates are dropped rather than rejected; the remaining cards are still valid.
                continue;
            }
            result.add(card);
        }
        failIfAny(problems);
        if (result.isEmpty()) {
            throw invalid("no usable cards remained after validation");
        }
        return List.copyOf(result);
    }

    /** The single corrective card a mistake becomes: {@code {"card": {...}}}. */
    public GeneratedFlashcard validateMistakeCard(String rawText) {
        JsonNode root = parseObject(rawText);
        JsonNode node = root.get("card");
        if (node == null || node.isNull()) {
            throw invalid("\"card\" is missing");
        }
        List<String> problems = new ArrayList<>();
        GeneratedFlashcard card = parseCard(node, "card", problems);
        failIfAny(problems);
        if (card == null) {
            throw invalid("no usable card in the response");
        }
        return card;
    }

    /**
     * Study-plan coaching text. Advice for topics that are not in the plan is dropped rather than
     * rejected; duplicates keep the first entry.
     */
    public StudyPlanAdvice validateStudyPlanAdvice(String rawText, List<String> allowedTopics) {
        JsonNode root = parseObject(rawText);
        List<String> problems = new ArrayList<>();
        String summary = requiredText(root, "summary", "response", StudyPlanPromptBuilder.MAX_SUMMARY_CHARS, problems);
        JsonNode list = root.get("topicAdvice");
        List<StudyPlanAdvice.TopicAdvice> advice = new ArrayList<>();
        if (list == null || !list.isArray()) {
            problems.add("\"topicAdvice\" must be an array");
        } else {
            Set<String> allowed = new HashSet<>();
            for (String topic : allowedTopics) {
                allowed.add(topic.strip().toLowerCase(Locale.ROOT));
            }
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < list.size(); i++) {
                JsonNode node = list.get(i);
                String where = "topicAdvice[" + i + "]";
                if (!node.isObject()) {
                    problems.add(where + " is not an object");
                    continue;
                }
                String topic = requiredText(node, "topic", where, FlashcardPromptBuilder.MAX_TOPIC_CHARS, problems);
                String text = requiredText(node, "advice", where, StudyPlanPromptBuilder.MAX_ADVICE_CHARS, problems);
                if (topic == null || text == null) {
                    continue;
                }
                String key = topic.toLowerCase(Locale.ROOT);
                if (allowed.contains(key) && seen.add(key)) {
                    advice.add(new StudyPlanAdvice.TopicAdvice(topic, text));
                }
            }
        }
        failIfAny(problems);
        return new StudyPlanAdvice(summary, List.copyOf(advice));
    }

    /** Mixed-type exam questions; each type has its own shape. Disallowed types are a problem, not a drop. */
    public List<GeneratedExamQuestion> validateMockExam(String rawText, int maxQuestions, Set<ExamQuestionType> allowed) {
        JsonNode root = parseObject(rawText);
        JsonNode questions = requireArray(root, "questions");
        if (questions.isEmpty()) {
            throw invalid("\"questions\" is empty; at least one question is required");
        }
        int limit = Math.min(questions.size(), maxQuestions);
        List<String> problems = new ArrayList<>();
        List<GeneratedExamQuestion> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < limit; i++) {
            JsonNode node = questions.get(i);
            String where = "questions[" + i + "]";
            if (!node.isObject()) {
                problems.add(where + " is not an object");
                continue;
            }
            ExamQuestionType type = examType(node, where, allowed, problems);
            String question = requiredText(node, "question", where, MockExamPromptBuilder.MAX_QUESTION_CHARS, problems);
            String explanation = requiredText(node, "explanation", where, MockExamPromptBuilder.MAX_EXPLANATION_CHARS, problems);
            String topic = optionalText(node, "topic", where, MockExamPromptBuilder.MAX_TOPIC_CHARS, problems);
            if (type == null || question == null || explanation == null) {
                continue;
            }
            GeneratedExamQuestion parsed = switch (type) {
                case MCQ -> {
                    List<String> options = quizOptions(node, where, problems);
                    Integer correct = correctAnswerIndex(node, "correctOption", where, 4, problems);
                    yield options == null || correct == null ? null
                            : new GeneratedExamQuestion(type, question, options, correct, null, List.of(), explanation, topic);
                }
                case TRUE_FALSE -> {
                    Integer correct = correctAnswerIndex(node, "correctOption", where, 2, problems);
                    yield correct == null ? null
                            : new GeneratedExamQuestion(type, question, List.of("True", "False"), correct, null, List.of(),
                                    explanation, topic);
                }
                case SHORT_ANSWER -> {
                    String answer = requiredText(node, "correctAnswer", where, MockExamPromptBuilder.MAX_SHORT_ANSWER_CHARS, problems);
                    List<String> acceptable = acceptableAnswers(node, where, problems);
                    yield answer == null ? null
                            : new GeneratedExamQuestion(type, question, List.of(), null, answer, acceptable, explanation, topic);
                }
            };
            if (parsed == null || !seen.add(question.toLowerCase(Locale.ROOT))) {
                continue;
            }
            result.add(parsed);
        }
        failIfAny(problems);
        if (result.isEmpty()) {
            throw invalid("no usable questions remained after validation");
        }
        return List.copyOf(result);
    }

    private static ExamQuestionType examType(JsonNode node, String where, Set<ExamQuestionType> allowed,
                                             List<String> problems) {
        JsonNode value = node.get("type");
        if (value == null || !value.isTextual()) {
            problems.add(where + ".type must be one of MCQ, TRUE_FALSE, SHORT_ANSWER");
            return null;
        }
        try {
            ExamQuestionType type = ExamQuestionType.valueOf(value.asText().strip().toUpperCase(Locale.ROOT));
            if (!allowed.contains(type)) {
                problems.add(where + ".type " + type + " was not requested; allowed: " + allowed);
                return null;
            }
            return type;
        } catch (IllegalArgumentException e) {
            problems.add(where + ".type \"" + value.asText() + "\" is not one of MCQ, TRUE_FALSE, SHORT_ANSWER");
            return null;
        }
    }

    private static Integer correctAnswerIndex(JsonNode node, String field, String where, int optionCount,
                                              List<String> problems) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber()) {
            problems.add(where + "." + field + " must be an integer between 0 and " + (optionCount - 1));
            return null;
        }
        int index = value.asInt();
        if (index < 0 || index >= optionCount) {
            problems.add(where + "." + field + " is " + index + "; it must be between 0 and " + (optionCount - 1));
            return null;
        }
        return index;
    }

    private static List<String> acceptableAnswers(JsonNode node, String where, List<String> problems) {
        JsonNode value = node.get("acceptableAnswers");
        if (value == null || value.isNull()) {
            return List.of();
        }
        if (!value.isArray()) {
            problems.add(where + ".acceptableAnswers must be an array of strings");
            return List.of();
        }
        List<String> answers = new ArrayList<>();
        for (JsonNode item : value) {
            if (!item.isTextual()) {
                problems.add(where + ".acceptableAnswers must contain only strings");
                return List.of();
            }
            String text = item.asText().strip();
            if (text.length() > MockExamPromptBuilder.MAX_SHORT_ANSWER_CHARS) {
                problems.add(where + ".acceptableAnswers contains an answer longer than "
                        + MockExamPromptBuilder.MAX_SHORT_ANSWER_CHARS + " characters");
                return List.of();
            }
            if (!text.isEmpty() && answers.size() < MockExamPromptBuilder.MAX_ACCEPTABLE_ANSWERS) {
                answers.add(text);
            }
        }
        return List.copyOf(answers);
    }

    /** One card object; records every problem and returns null when the card is unusable. */
    private static GeneratedFlashcard parseCard(JsonNode node, String where, List<String> problems) {
        if (!node.isObject()) {
            problems.add(where + " is not an object");
            return null;
        }
        String question = requiredText(node, "question", where, FlashcardPromptBuilder.MAX_QUESTION_CHARS, problems);
        String answer = requiredText(node, "answer", where, FlashcardPromptBuilder.MAX_ANSWER_CHARS, problems);
        String explanation = optionalText(node, "explanation", where,
                FlashcardPromptBuilder.MAX_EXPLANATION_CHARS, problems);
        String topic = optionalText(node, "topic", where, FlashcardPromptBuilder.MAX_TOPIC_CHARS, problems);
        List<String> tags = optionalTags(node, where, problems);
        if (question == null || answer == null) {
            return null;
        }
        return new GeneratedFlashcard(question, answer, explanation, topic, tags);
    }

    public List<GeneratedQuizQuestion> validateQuiz(String rawText, int maxQuestions) {
        JsonNode root = parseObject(rawText);
        JsonNode questions = requireArray(root, "questions");
        if (questions.isEmpty()) {
            throw invalid("\"questions\" is empty; at least one question is required");
        }
        int questionLimit = Math.min(questions.size(), maxQuestions);

        List<String> problems = new ArrayList<>();
        List<GeneratedQuizQuestion> result = new ArrayList<>();
        Set<String> seenQuestions = new HashSet<>();
        for (int i = 0; i < questionLimit; i++) {
            JsonNode node = questions.get(i);
            String where = "questions[" + i + "]";
            if (!node.isObject()) {
                problems.add(where + " is not an object");
                continue;
            }
            String question = requiredText(node, "question", where, QuizPromptBuilder.MAX_QUESTION_CHARS, problems);
            String explanation = requiredText(node, "explanation", where, QuizPromptBuilder.MAX_EXPLANATION_CHARS,
                    problems);
            List<String> options = quizOptions(node, where, problems);
            Integer correct = correctAnswer(node, where, problems);
            String topic = optionalText(node, "topic", where, QuizPromptBuilder.MAX_TOPIC_CHARS, problems);
            if (question == null || explanation == null || options == null || correct == null) {
                continue;
            }
            if (!seenQuestions.add(question.toLowerCase(Locale.ROOT))) {
                continue;
            }
            result.add(new GeneratedQuizQuestion(question, options, correct, explanation, topic));
        }
        failIfAny(problems);
        if (result.isEmpty()) {
            throw invalid("no usable questions remained after validation");
        }
        return List.copyOf(result);
    }

    // ---- shared helpers ----

    private JsonNode parseObject(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            throw invalid("response is empty");
        }
        String candidate = stripCodeFence(rawText.strip());
        JsonNode root;
        try {
            root = objectMapper.readTree(candidate);
        } catch (JsonProcessingException e) {
            throw invalid("response is not valid JSON: " + e.getOriginalMessage());
        }
        if (root == null || !root.isObject()) {
            throw invalid("response is not a JSON object");
        }
        return root;
    }

    /** Tolerates a ```json fence around otherwise valid JSON; anything else is left as is. */
    static String stripCodeFence(String text) {
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                return text.substring(firstNewline + 1, lastFence).strip();
            }
        }
        return text;
    }

    private static JsonNode requireArray(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null) {
            throw invalid("\"" + field + "\" is missing");
        }
        if (!node.isArray()) {
            throw invalid("\"" + field + "\" must be an array");
        }
        return node;
    }

    private static String requiredText(JsonNode node, String field, String where, int maxLength,
                                       List<String> problems) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            problems.add(where + "." + field + " is missing");
            return null;
        }
        if (!value.isTextual()) {
            problems.add(where + "." + field + " must be a string");
            return null;
        }
        String text = value.asText().strip();
        if (text.isEmpty()) {
            problems.add(where + "." + field + " is blank");
            return null;
        }
        if (text.length() > maxLength) {
            problems.add(where + "." + field + " exceeds " + maxLength + " characters");
            return null;
        }
        return text;
    }

    private static String optionalText(JsonNode node, String field, String where, int maxLength,
                                       List<String> problems) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            problems.add(where + "." + field + " must be a string");
            return null;
        }
        String text = value.asText().strip();
        if (text.length() > maxLength) {
            problems.add(where + "." + field + " exceeds " + maxLength + " characters");
            return null;
        }
        return text.isEmpty() ? null : text;
    }

    private static List<String> optionalTags(JsonNode node, String where, List<String> problems) {
        JsonNode value = node.get("tags");
        if (value == null || value.isNull()) {
            return List.of();
        }
        if (!value.isArray()) {
            problems.add(where + ".tags must be an array of strings");
            return List.of();
        }
        List<String> raw = new ArrayList<>();
        for (JsonNode tag : value) {
            if (!tag.isTextual()) {
                problems.add(where + ".tags must contain only strings");
                return List.of();
            }
            if (tag.asText().length() > FlashcardPromptBuilder.MAX_TAG_CHARS) {
                problems.add(where + ".tags contains a tag longer than " + FlashcardPromptBuilder.MAX_TAG_CHARS
                        + " characters");
                return List.of();
            }
            raw.add(tag.asText());
        }
        List<String> normalized = TagNormalizer.normalize(raw);
        return normalized.size() > FlashcardPromptBuilder.MAX_TAGS
                ? normalized.subList(0, FlashcardPromptBuilder.MAX_TAGS)
                : normalized;
    }

    private static List<String> quizOptions(JsonNode node, String where, List<String> problems) {
        JsonNode value = node.get("options");
        if (value == null || !value.isArray()) {
            problems.add(where + ".options must be an array of exactly " + QuizPromptBuilder.OPTION_COUNT + " strings");
            return null;
        }
        if (value.size() != QuizPromptBuilder.OPTION_COUNT) {
            problems.add(where + ".options has " + value.size() + " entries; exactly "
                    + QuizPromptBuilder.OPTION_COUNT + " are required");
            return null;
        }
        List<String> options = new ArrayList<>();
        Set<String> distinct = new HashSet<>();
        for (JsonNode option : value) {
            if (!option.isTextual() || option.asText().isBlank()) {
                problems.add(where + ".options must contain only non-empty strings");
                return null;
            }
            String text = option.asText().strip();
            if (text.length() > QuizPromptBuilder.MAX_OPTION_CHARS) {
                problems.add(where + ".options contains an option longer than " + QuizPromptBuilder.MAX_OPTION_CHARS
                        + " characters");
                return null;
            }
            if (!distinct.add(text.toLowerCase(Locale.ROOT))) {
                problems.add(where + ".options contains duplicate options");
                return null;
            }
            options.add(text);
        }
        return List.copyOf(options);
    }

    private static Integer correctAnswer(JsonNode node, String where, List<String> problems) {
        JsonNode value = node.get("correctAnswer");
        if (value == null || !value.isIntegralNumber()) {
            problems.add(where + ".correctAnswer must be an integer between 0 and " + (QuizPromptBuilder.OPTION_COUNT - 1));
            return null;
        }
        int index = value.asInt();
        if (index < 0 || index >= QuizPromptBuilder.OPTION_COUNT) {
            problems.add(where + ".correctAnswer is " + index + "; it must be between 0 and "
                    + (QuizPromptBuilder.OPTION_COUNT - 1));
            return null;
        }
        return index;
    }

    private static void failIfAny(List<String> problems) {
        if (!problems.isEmpty()) {
            List<String> reported = problems.size() > MAX_REPORTED_PROBLEMS
                    ? new ArrayList<>(problems.subList(0, MAX_REPORTED_PROBLEMS))
                    : problems;
            if (problems.size() > MAX_REPORTED_PROBLEMS) {
                reported.add("... and " + (problems.size() - MAX_REPORTED_PROBLEMS) + " more");
            }
            throw new AiInvalidResponseException(reported);
        }
    }

    private static AiInvalidResponseException invalid(String problem) {
        return new AiInvalidResponseException(List.of(problem));
    }
}
