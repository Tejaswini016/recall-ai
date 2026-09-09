package com.recallai.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Tags are stored in one canonical form so that "Biology", " biology " and "BIOLOGY"
 * are the same tag and array equality searches hit the GIN index.
 */
public final class TagNormalizer {

    private TagNormalizer() {
    }

    public static List<String> normalize(List<String> tags) {
        if (tags == null) {
            return List.of();
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String tag : tags) {
            String normalized = normalizeOne(tag);
            if (normalized != null) {
                unique.add(normalized);
            }
        }
        return List.copyOf(unique);
    }

    /** @return the canonical tag, or null if the input is blank. */
    public static String normalizeOne(String tag) {
        if (tag == null) {
            return null;
        }
        String normalized = tag.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        return normalized.isEmpty() ? null : normalized;
    }
}
