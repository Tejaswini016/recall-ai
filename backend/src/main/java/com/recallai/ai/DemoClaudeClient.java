package com.recallai.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Stand-in for Claude when {@code AI_DEMO_MODE=true}. Builds cards and quiz questions from the
 * supplied notes with simple sentence heuristics, no network and no cost, so the whole
 * pipeline (chunking, validation, caching, persistence, rate limiting) can be demonstrated
 * without an API key. Output is deterministic, tagged {@code demo}, and clearly labelled in
 * every explanation. It is not AI and the README says so.
 */
@Component
@ConditionalOnProperty(prefix = "recallai.ai", name = "demo-mode", havingValue = "true")
public class DemoClaudeClient implements ClaudeClient {

    private static final Logger log = LoggerFactory.getLogger(DemoClaudeClient.class);

    static final String DEMO_TAG = "demo";
    static final String DEMO_TOPIC = "Demo notes";
    private static final String DEMO_NOTE = "Demo mode: built from your notes without calling Claude.";
    private static final int MIN_SENTENCE_WORDS = 5;
    private static final int MAX_SUBJECT_CHARS = 80;
    private static final int OPTION_COUNT = 4;

    private static final Pattern SENTENCE_SPLIT = Pattern.compile("(?<=[.!?])\\s+");
    private static final Pattern MATERIAL = Pattern.compile("<study_material>\\s*(.*?)\\s*</study_material>", Pattern.DOTALL);
    private static final Pattern REQUESTED = Pattern.compile("up to (\\d+)");
    private static final Pattern STATEMENT = Pattern.compile(
            "^(.{3," + MAX_SUBJECT_CHARS + "}?)\\s+(is|are|was|were|occurs|occur|takes place|take place|produces|produce|"
                    + "contains|contain|converts|convert|uses|use|includes|include|requires|require)\\s+(.{3,}?)[.!?]?$",
            Pattern.CASE_INSENSITIVE);
    private static final List<String> FALLBACK_DISTRACTORS = List.of(
            "This is not stated in the notes",
            "The notes say the opposite",
            "The notes do not cover this",
            "None of the above");

    private final ObjectMapper objectMapper;

    public DemoClaudeClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        log.warn("AI demo mode is ON: generation uses local heuristics instead of Claude");
    }

    @Override
    public AiCompletion complete(AiPrompt prompt) {
        String user = prompt.messages().get(0).content();
        String material = extract(MATERIAL, user).orElse(user);
        int requested = extract(REQUESTED, user).map(Integer::parseInt).orElse(10);
        List<String> sentences = sentences(material);
        ObjectNode root = objectMapper.createObjectNode();
        if (prompt.operation() == AiOperation.QUIZ) {
            root.set("questions", quiz(sentences, requested));
        } else {
            root.set("cards", cards(sentences, requested));
        }
        try {
            return AiCompletion.ofText(objectMapper.writeValueAsString(root));
        } catch (JsonProcessingException e) {
            throw new AiUnavailableException("Demo generator could not serialize its output", false, e);
        }
    }

    private ArrayNode cards(List<String> sentences, int requested) {
        ArrayNode cards = objectMapper.createArrayNode();
        for (String sentence : sentences) {
            if (cards.size() >= requested) {
                break;
            }
            ObjectNode card = cards.addObject();
            Matcher m = STATEMENT.matcher(sentence);
            if (m.matches()) {
                card.put("question", questionFor(m.group(1).trim(), m.group(2).toLowerCase(Locale.ROOT)));
                card.put("answer", capitalize(m.group(3).trim()) + ".");
            } else {
                card.put("question", "Complete the statement: \"" + prefix(sentence) + " …\"");
                card.put("answer", sentence);
            }
            card.put("explanation", "From your notes: \"" + sentence + "\" " + DEMO_NOTE);
            card.put("topic", DEMO_TOPIC);
            card.putArray("tags").add(DEMO_TAG);
        }
        if (cards.isEmpty()) {
            ObjectNode card = cards.addObject();
            card.put("question", "What is the main idea of these notes?");
            card.put("answer", sentences.isEmpty() ? "The notes were too short to summarise." : sentences.get(0));
            card.put("explanation", DEMO_NOTE);
            card.put("topic", DEMO_TOPIC);
            card.putArray("tags").add(DEMO_TAG);
        }
        return cards;
    }

    private ArrayNode quiz(List<String> sentences, int requested) {
        List<String[]> facts = new ArrayList<>();
        for (String sentence : sentences) {
            Matcher m = STATEMENT.matcher(sentence);
            if (m.matches()) {
                facts.add(new String[] {sentence, m.group(1).trim(), m.group(2).toLowerCase(Locale.ROOT),
                        capitalize(m.group(3).trim())});
            }
        }
        ArrayNode questions = objectMapper.createArrayNode();
        for (int i = 0; i < facts.size() && questions.size() < requested; i++) {
            String[] fact = facts.get(i);
            List<String> options = new ArrayList<>();
            options.add(fact[3]);
            for (int j = 1; j < facts.size() && options.size() < OPTION_COUNT; j++) {
                String candidate = facts.get((i + j) % facts.size())[3];
                if (options.stream().noneMatch(o -> o.equalsIgnoreCase(candidate))) {
                    options.add(candidate);
                }
            }
            for (String filler : FALLBACK_DISTRACTORS) {
                if (options.size() >= OPTION_COUNT) {
                    break;
                }
                if (options.stream().noneMatch(o -> o.equalsIgnoreCase(filler))) {
                    options.add(filler);
                }
            }
            int correctIndex = i % OPTION_COUNT;
            String correct = options.remove(0);
            options.add(correctIndex, correct);

            ObjectNode question = questions.addObject();
            question.put("question", questionFor(fact[1], fact[2]));
            ArrayNode optionNode = question.putArray("options");
            options.forEach(optionNode::add);
            question.put("correctAnswer", correctIndex);
            question.put("explanation", "Your notes say: \"" + fact[0] + "\" " + DEMO_NOTE);
        }
        if (questions.isEmpty()) {
            ObjectNode question = questions.addObject();
            question.put("question", "Which statement comes from these notes?");
            ArrayNode optionNode = question.putArray("options");
            optionNode.add(sentences.isEmpty() ? "The notes were empty" : sentences.get(0));
            FALLBACK_DISTRACTORS.stream().limit(OPTION_COUNT - 1).forEach(optionNode::add);
            question.put("correctAnswer", 0);
            question.put("explanation", DEMO_NOTE);
        }
        return questions;
    }

    static String questionFor(String subject, String verb) {
        return switch (verb) {
            case "is", "are", "was", "were" -> "What " + verb + " " + subject + "?";
            case "occurs", "occur", "takes place", "take place" -> "Where does " + subject + " take place?";
            case "produces", "produce" -> "What does " + subject + " produce?";
            case "contains", "contain", "includes", "include" -> "What does " + subject + " contain?";
            case "converts", "convert" -> "What does " + subject + " convert?";
            case "uses", "use", "requires", "require" -> "What does " + subject + " use?";
            default -> "What do the notes say about " + subject + "?";
        };
    }

    static List<String> sentences(String material) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String raw : SENTENCE_SPLIT.split(material.replaceAll("\\s+", " ").strip())) {
            String sentence = raw.strip();
            if (sentence.split("\\s+").length >= MIN_SENTENCE_WORDS) {
                unique.add(sentence);
            }
        }
        return List.copyOf(unique);
    }

    private static String prefix(String sentence) {
        String[] words = sentence.split("\\s+");
        int keep = Math.max(3, (int) Math.ceil(words.length * 0.6));
        return String.join(" ", List.of(words).subList(0, Math.min(keep, words.length)));
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static Optional<String> extract(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }
}
