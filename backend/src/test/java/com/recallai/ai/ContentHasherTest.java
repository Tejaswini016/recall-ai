package com.recallai.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ContentHasherTest {

    @Test
    void hashIsSha256Hex() {
        assertThat(ContentHasher.hash("The mitochondria is the powerhouse of the cell.", "count=10"))
                .hasSize(64)
                .matches("[0-9a-f]{64}");
    }

    @Test
    void whitespaceCaseAndUnicodeFormDoNotChangeTheHash() {
        String canonical = ContentHasher.hash("Photosynthesis converts light energy.", "count=10");

        assertThat(ContentHasher.hash("  photosynthesis   converts\n\nlight   ENERGY. ", "count=10"))
                .isEqualTo(canonical);
        // "é" composed vs. decomposed
        assertThat(ContentHasher.hash("café", "x")).isEqualTo(ContentHasher.hash("café", "x"));
    }

    @Test
    void differentMaterialOrParametersChangeTheHash() {
        String base = ContentHasher.hash("Photosynthesis converts light energy.", "count=10");

        assertThat(ContentHasher.hash("Photosynthesis converts light energy!", "count=10")).isNotEqualTo(base);
        assertThat(ContentHasher.hash("Photosynthesis converts light energy.", "count=5")).isNotEqualTo(base);
    }

    @Test
    void normalizeHandlesNull() {
        assertThat(ContentHasher.normalize(null)).isEmpty();
    }
}
