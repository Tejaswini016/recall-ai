package com.recallai.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
