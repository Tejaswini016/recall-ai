package com.recallai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recallai.config.MaterialProperties;
import com.recallai.exception.ApiException;
import com.recallai.exception.ErrorCode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class StudyMaterialExtractorTest {

    private final StudyMaterialExtractor extractor = new StudyMaterialExtractor(new MaterialProperties(5000, 6));

    @Test
    void extractsAndCleansPlainText() {
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain",
                "Line one.\r\n\r\n\r\n\r\nLine   two.\tend".getBytes(StandardCharsets.UTF_8));

        assertThat(extractor.fromFile(file)).isEqualTo("Line one.\n\nLine two. end");
    }

    @Test
    void extractsTextFromAPdf() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "chapter.pdf", "application/pdf",
                com.recallai.TestPdf.withText("Photosynthesis converts light energy into chemical energy."));

        assertThat(extractor.fromFile(file)).contains("Photosynthesis converts light energy into chemical energy.");
    }

    @Test
    void acceptsPdfByContentTypeEvenWithoutExtension() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "upload", "application/pdf",
                com.recallai.TestPdf.withText("Mitochondria produce ATP."));

        assertThat(extractor.fromFile(file)).contains("Mitochondria produce ATP.");
    }

    @Test
    void rejectsPdfWithNoText() throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(out);
            MockMultipartFile file = new MockMultipartFile("file", "blank.pdf", "application/pdf", out.toByteArray());

            assertThatThrownBy(() -> extractor.fromFile(file))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("No text could be extracted");
        }
    }

    @Test
    void rejectsFileThatClaimsToBePdfButIsNot() {
        MockMultipartFile file = new MockMultipartFile("file", "fake.pdf", "application/pdf",
                "hello".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> extractor.fromFile(file)).hasMessageContaining("not a valid PDF");
    }

    @Test
    void rejectsCorruptPdf() {
        byte[] corrupt = "%PDF-1.7 garbage that is not a real document".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "broken.pdf", "application/pdf", corrupt);

        assertThatThrownBy(() -> extractor.fromFile(file))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo(ErrorCode.FILE_UPLOAD_ERROR));
    }

    @Test
    void rejectsUnsupportedTypesAndEmptyFiles() {
        MockMultipartFile docx = new MockMultipartFile("file", "notes.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", new byte[] {1, 2, 3});
        MockMultipartFile empty = new MockMultipartFile("file", "notes.txt", "text/plain", new byte[0]);
        MockMultipartFile blank = new MockMultipartFile("file", "notes.txt", "text/plain",
                "   \n\n ".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> extractor.fromFile(docx)).hasMessageContaining("Unsupported file type");
        assertThatThrownBy(() -> extractor.fromFile(empty)).hasMessageContaining("empty");
        assertThatThrownBy(() -> extractor.fromFile(blank)).hasMessageContaining("contains no text");
        assertThatThrownBy(() -> extractor.fromFile(null)).hasMessageContaining("empty");
    }

    @Test
    void rejectsMaterialAboveTheConfiguredLimit() {
        assertThatThrownBy(() -> extractor.fromText("x".repeat(5001)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("too long")
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThat(extractor.fromText("x".repeat(5000))).hasSize(5000);
    }

}
