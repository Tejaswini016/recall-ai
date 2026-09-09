package com.recallai.controller;

import com.recallai.dto.GenerateCardsResponse;
import com.recallai.dto.GenerateFlashcardsRequest;
import com.recallai.dto.GenerateQuizRequest;
import com.recallai.dto.QuizResponse;
import com.recallai.security.AuthenticatedUser;
import com.recallai.service.FlashcardGenerationService;
import com.recallai.service.QuizGenerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/ai")
@Tag(name = "AI generation", description = "Claude-powered flashcard and quiz generation")
@SecurityRequirement(name = "bearerAuth")
public class AiController {

    private final FlashcardGenerationService flashcardGenerationService;
    private final QuizGenerationService quizGenerationService;

    public AiController(FlashcardGenerationService flashcardGenerationService,
                        QuizGenerationService quizGenerationService) {
        this.flashcardGenerationService = flashcardGenerationService;
        this.quizGenerationService = quizGenerationService;
    }

    @PostMapping("/flashcards")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Generate flashcards from pasted study notes and add them to a deck")
    public GenerateCardsResponse fromText(@AuthenticationPrincipal AuthenticatedUser user,
                                          @Valid @RequestBody GenerateFlashcardsRequest request) {
        return flashcardGenerationService.generateFromText(user.id(), request.deckId(), request.text(),
                request.count());
    }

    @PostMapping(value = "/flashcards/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Generate flashcards from an uploaded .txt or .pdf file and add them to a deck")
    public GenerateCardsResponse fromFile(@AuthenticationPrincipal AuthenticatedUser user,
                                          @RequestParam Long deckId,
                                          @RequestParam(required = false)
                                          @Min(1) @Max(GenerateFlashcardsRequest.MAX_COUNT) Integer count,
                                          @RequestPart("file") MultipartFile file) {
        return flashcardGenerationService.generateFromFile(user.id(), deckId, file, count);
    }

    @PostMapping("/quiz")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Generate a multiple-choice quiz from a deck's cards or from supplied study material")
    public QuizResponse quiz(@AuthenticationPrincipal AuthenticatedUser user,
                             @Valid @RequestBody GenerateQuizRequest request) {
        return quizGenerationService.generate(user.id(), request);
    }
}
