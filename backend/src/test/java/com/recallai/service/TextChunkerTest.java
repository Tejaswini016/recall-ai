package com.recallai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class TextChunkerTest {

    @Test
    void shortTextIsASingleChunk() {
        assertThat(TextChunker.chunk("One paragraph.\n\nAnother paragraph.", 100))
                .containsExactly("One paragraph.\n\nAnother paragraph.");
    }

    @Test
    void emptyTextProducesNoChunks() {
        assertThat(TextChunker.chunk("", 100)).isEmpty();
        assertThat(TextChunker.chunk("  \n\n ", 100)).isEmpty();
        assertThat(TextChunker.chunk(null, 100)).isEmpty();
    }

    @Test
    void splitsOnParagraphBoundariesBeforeAnythingElse() {
        String p1 = "Alpha ".repeat(10).strip();
        String p2 = "Beta ".repeat(10).strip();
        String p3 = "Gamma ".repeat(10).strip();

        List<String> chunks = TextChunker.chunk(p1 + "\n\n" + p2 + "\n\n" + p3, 120);

        assertThat(chunks).containsExactly(p1 + "\n\n" + p2, p3);
    }

    @Test
    void longParagraphIsSplitOnSentences() {
        String paragraph = "First sentence here. Second sentence here. Third sentence here! Fourth one? Fifth.";

        List<String> chunks = TextChunker.chunk(paragraph, 45);

        assertThat(chunks).containsExactly(
                "First sentence here. Second sentence here.",
                "Third sentence here! Fourth one? Fifth.");
    }

    @Test
    void sentenceLongerThanLimitIsHardCutAtWhitespace() {
        String words = "word ".repeat(30).strip();

        List<String> chunks = TextChunker.chunk(words, 24);

        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.length()).isLessThanOrEqualTo(24));
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk).doesNotStartWith(" ").doesNotEndWith(" "));
        assertThat(String.join(" ", chunks)).isEqualTo(words);
    }

    @Test
    void textWithoutAnyWhitespaceIsCutExactlyAtTheLimit() {
        String blob = "x".repeat(25);

        assertThat(TextChunker.chunk(blob, 10)).containsExactly("xxxxxxxxxx", "xxxxxxxxxx", "xxxxx");
    }

    @Test
    void noChunkExceedsTheLimitAndNoWordIsLost() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            text.append("Paragraph ").append(i).append(" talks about topic ").append(i)
                    .append(". It has a second sentence with detail ").append(i).append(".\n\n");
        }
        List<String> chunks = TextChunker.chunk(text.toString(), 300);

        assertThat(chunks.size()).isGreaterThan(5);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.length()).isLessThanOrEqualTo(300));
        String rejoined = String.join(" ", chunks).replaceAll("\\s+", " ");
        assertThat(rejoined).isEqualTo(text.toString().replaceAll("\\s+", " ").strip());
    }

    @Test
    void rejectsNonPositiveLimit() {
        assertThatThrownBy(() -> TextChunker.chunk("x", 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
