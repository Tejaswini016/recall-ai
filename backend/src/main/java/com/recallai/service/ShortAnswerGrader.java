package com.recallai.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Rule-based grading of short answers, so no model call is needed to score an exam. An answer
 * is correct when, after normalisation (case, accents, punctuation, articles, whitespace), it
 * equals the model answer or any acceptable alternative, or when it contains every word of one
 * of them without being much longer (so "the mitochondrion, obviously" matches "mitochondrion"
 * but a paragraph that happens to include the word does not). Numbers compare numerically.
 */
public final class ShortAnswerGrader {

    private static final Set<String> ARTICLES = Set.of("the", "a", "an");
    private static final int MAX_EXTRA_WORDS = 3;

    private ShortAnswerGrader() {
    }

    public static boolean isCorrect(String given, String correct, List<String> acceptable) {
        if (given == null || given.isBlank()) {
            return false;
        }
        String answer = normalize(given);
        if (answer.isEmpty()) {
            return false;
        }
        List<String> expected = new ArrayList<>();
        if (correct != null) {
            expected.add(correct);
        }
        if (acceptable != null) {
            expected.addAll(acceptable);
        }
        for (String candidate : expected) {
            String target = normalize(candidate);
            if (target.isEmpty()) {
                continue;
            }
            if (answer.equals(target) || numericallyEqual(answer, target)) {
                return true;
            }
            List<String> answerWords = words(answer);
            List<String> targetWords = words(target);
            if (answerWords.size() <= targetWords.size() + MAX_EXTRA_WORDS
                    && new HashSet<>(answerWords).containsAll(targetWords)) {
                return true;
            }
        }
        return false;
    }

    static String normalize(String text) {
        String stripped = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        // Thousands separators vanish ("1,000" -> "1000"); hyphens split words; a dot only survives inside a number.
        String cleaned = stripped.toLowerCase(Locale.ROOT)
                .replaceAll("(?<=\\d),(?=\\d{3}\\b)", "")
                .replaceAll("[^\\p{L}\\p{N}\\s.]", " ")
                .replaceAll("(?<!\\d)\\.|\\.(?!\\d)", " ")
                .replaceAll("\\s+", " ").strip();
        List<String> kept = new ArrayList<>();
        for (String word : cleaned.split(" ")) {
            if (!word.isEmpty() && !ARTICLES.contains(word)) {
                kept.add(word);
            }
        }
        return String.join(" ", kept);
    }

    private static List<String> words(String normalized) {
        return normalized.isEmpty() ? List.of() : Arrays.asList(normalized.split(" "));
    }

    private static boolean numericallyEqual(String a, String b) {
        try {
            return Double.parseDouble(a) == Double.parseDouble(b);
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
