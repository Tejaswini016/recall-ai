package com.recallai.ai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Produces the content-addressed part of an AI cache key. Normalization makes trivially
 * different pastes of the same notes (extra whitespace, different casing, composed vs.
 * decomposed Unicode) hash identically, so they share one model call.
 */
public final class ContentHasher {

    private ContentHasher() {
    }

    public static String normalize(String material) {
        if (material == null) {
            return "";
        }
        return Normalizer.normalize(material, Normalizer.Form.NFC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * SHA-256 of the normalized material plus the request parameters that change the
     * output (e.g. requested item count), as 64 lower-case hex characters.
     */
    public static String hash(String material, String parameters) {
        String canonical = normalize(material) + "\n#params=" + parameters;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JVM specification", e);
        }
    }
}
