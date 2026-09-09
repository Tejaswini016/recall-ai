package com.recallai.service;

import com.recallai.config.MaterialProperties;
import com.recallai.exception.ApiException;
import com.recallai.exception.ErrorCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Turns an uploaded file or pasted text into clean study material. Everything untrusted
 * about an upload is checked here: type, emptiness, extractability and size.
 */
@Service
public class StudyMaterialExtractor {

    private static final Logger log = LoggerFactory.getLogger(StudyMaterialExtractor.class);

    static final Set<String> TEXT_TYPES = Set.of("text/plain");
    static final Set<String> PDF_TYPES = Set.of("application/pdf", "application/x-pdf");
    private static final byte[] PDF_MAGIC = "%PDF".getBytes(StandardCharsets.US_ASCII);

    private final MaterialProperties properties;

    public StudyMaterialExtractor(MaterialProperties properties) {
        this.properties = properties;
    }

    public String fromText(String text) {
        return clean(text == null ? "" : text);
    }

    public String fromFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw uploadError("The uploaded file is empty");
        }
        Kind kind = detectKind(file);
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw uploadError("The uploaded file could not be read");
        }
        String raw = switch (kind) {
            case TEXT -> new String(bytes, StandardCharsets.UTF_8);
            case PDF -> extractPdf(bytes);
        };
        String cleaned = clean(raw);
        if (cleaned.isBlank()) {
            throw uploadError(kind == Kind.PDF
                    ? "No text could be extracted from the PDF (scanned images are not supported)"
                    : "The uploaded file contains no text");
        }
        log.info("Extracted {} characters from {} upload", cleaned.length(), kind);
        return cleaned;
    }

    /** Normalizes whitespace, strips control characters, and enforces the size limit. */
    public String clean(String raw) {
        String text = raw.replace("\r\n", "\n").replace('\r', '\n')
                .replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "")
                .replaceAll("[ \\t]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
        if (text.length() > properties.maxChars()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Study material is too long (" + text.length()
                    + " characters); the maximum is " + properties.maxChars());
        }
        return text;
    }

    private static Kind detectKind(MultipartFile file) {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        boolean pdfByName = name.endsWith(".pdf");
        boolean txtByName = name.endsWith(".txt") || name.endsWith(".md") || name.endsWith(".text");
        if (pdfByName || PDF_TYPES.contains(contentType)) {
            if (!startsWithPdfMagic(file)) {
                throw uploadError("The file is not a valid PDF");
            }
            return Kind.PDF;
        }
        if (txtByName || TEXT_TYPES.contains(contentType)) {
            return Kind.TEXT;
        }
        throw uploadError("Unsupported file type; upload a .txt or .pdf file");
    }

    private static boolean startsWithPdfMagic(MultipartFile file) {
        try (var in = file.getInputStream()) {
            byte[] head = in.readNBytes(PDF_MAGIC.length);
            return java.util.Arrays.equals(head, PDF_MAGIC);
        } catch (IOException e) {
            return false;
        }
    }

    private static String extractPdf(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            if (document.isEncrypted()) {
                throw uploadError("Encrypted PDFs are not supported");
            }
            return new PDFTextStripper().getText(document);
        } catch (IOException e) {
            log.info("PDF extraction failed: {}", e.getMessage());
            throw uploadError("The PDF could not be parsed");
        }
    }

    private static ApiException uploadError(String message) {
        return new ApiException(ErrorCode.FILE_UPLOAD_ERROR, message);
    }

    private enum Kind { TEXT, PDF }
}
