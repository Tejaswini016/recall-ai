package com.recallai.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class TagNormalizerTest {

    @Test
    void trimsLowercasesAndCollapsesWhitespace() {
        assertThat(TagNormalizer.normalize(List.of("  Cell   Biology ", "DNA")))
                .containsExactly("cell biology", "dna");
    }

    @Test
    void removesDuplicatesAfterNormalizationPreservingFirstOrder() {
        assertThat(TagNormalizer.normalize(List.of("Biology", "chemistry", "BIOLOGY", " biology")))
                .containsExactly("biology", "chemistry");
    }

    @Test
    void dropsBlankAndNullEntries() {
        assertThat(TagNormalizer.normalize(Arrays.asList("", "   ", null, "ok"))).containsExactly("ok");
    }

    @Test
    void nullListBecomesEmpty() {
        assertThat(TagNormalizer.normalize(null)).isEmpty();
    }

    @Test
    void normalizeOneReturnsNullForBlank() {
        assertThat(TagNormalizer.normalizeOne("  ")).isNull();
        assertThat(TagNormalizer.normalizeOne(null)).isNull();
        assertThat(TagNormalizer.normalizeOne(" Physics ")).isEqualTo("physics");
    }
}
