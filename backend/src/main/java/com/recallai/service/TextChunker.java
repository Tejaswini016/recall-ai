package com.recallai.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Splits study material into pieces no longer than a limit while keeping related text
 * together: paragraphs first, then sentences, and only as a last resort a hard cut at the
 * nearest whitespace. Nothing is dropped; concatenating the chunks reproduces every word.
 */
public final class TextChunker {

    private static final Pattern PARAGRAPH_BREAK = Pattern.compile("\\n\\s*\\n");
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.!?])\\s+");
    private static final String PARAGRAPH_JOIN = "\n\n";

    private TextChunker() {
    }

    public static List<String> chunk(String text, int maxChars) {
        if (maxChars <= 0) {
            throw new IllegalArgumentException("maxChars must be positive");
        }
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }
        StringBuilder current = new StringBuilder();
        for (String paragraph : PARAGRAPH_BREAK.split(text.strip())) {
            String p = paragraph.strip();
            if (p.isEmpty()) {
                continue;
            }
            if (p.length() > maxChars) {
                flush(current, chunks);
                chunks.addAll(splitLongParagraph(p, maxChars));
            } else if (current.length() + PARAGRAPH_JOIN.length() + p.length() > maxChars && current.length() > 0) {
                flush(current, chunks);
                current.append(p);
            } else {
                if (current.length() > 0) {
                    current.append(PARAGRAPH_JOIN);
                }
                current.append(p);
            }
        }
        flush(current, chunks);
        return chunks;
    }

    private static List<String> splitLongParagraph(String paragraph, int maxChars) {
        List<String> pieces = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String sentence : SENTENCE_END.split(paragraph)) {
            if (sentence.length() > maxChars) {
                flush(current, pieces);
                pieces.addAll(hardCut(sentence, maxChars));
            } else if (current.length() + 1 + sentence.length() > maxChars && current.length() > 0) {
                flush(current, pieces);
                current.append(sentence);
            } else {
                if (current.length() > 0) {
                    current.append(' ');
                }
                current.append(sentence);
            }
        }
        flush(current, pieces);
        return pieces;
    }

    /** Cuts at the last whitespace before the limit, or exactly at the limit if there is none. */
    private static List<String> hardCut(String text, int maxChars) {
        List<String> pieces = new ArrayList<>();
        String remaining = text;
        while (remaining.length() > maxChars) {
            int cut = remaining.lastIndexOf(' ', maxChars);
            if (cut <= 0) {
                cut = maxChars;
            }
            pieces.add(remaining.substring(0, cut).strip());
            remaining = remaining.substring(cut).strip();
        }
        if (!remaining.isEmpty()) {
            pieces.add(remaining);
        }
        return pieces;
    }

    private static void flush(StringBuilder current, List<String> into) {
        if (current.length() > 0) {
            into.add(current.toString());
            current.setLength(0);
        }
    }
}
