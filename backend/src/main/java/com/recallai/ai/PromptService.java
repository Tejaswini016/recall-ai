package com.recallai.ai;

import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Single entry point for prompt construction so no other class assembles prompt text.
 */
@Service
public class PromptService {

    /** How much of a rejected reply is echoed back in a corrective prompt. */
    static final int MAX_ECHOED_REPLY_CHARS = 4000;

    private final FlashcardPromptBuilder flashcardPromptBuilder;
    private final QuizPromptBuilder quizPromptBuilder;

    public PromptService(FlashcardPromptBuilder flashcardPromptBuilder, QuizPromptBuilder quizPromptBuilder) {
        this.flashcardPromptBuilder = flashcardPromptBuilder;
        this.quizPromptBuilder = quizPromptBuilder;
    }

    public AiPrompt flashcards(String material, int maxCards) {
        return flashcardPromptBuilder.build(material, maxCards);
    }

    public AiPrompt quiz(String material, int maxQuestions) {
        return quizPromptBuilder.build(material, maxQuestions);
    }

    public String promptVersion(AiOperation operation) {
        return switch (operation) {
            case FLASHCARDS -> FlashcardPromptBuilder.VERSION;
            case QUIZ -> QuizPromptBuilder.VERSION;
        };
    }

    /**
     * Extends a prompt with the rejected reply and an instruction that names each problem, so
     * the model can fix exactly what was wrong rather than guessing.
     */
    public AiPrompt corrective(AiPrompt original, String rejectedReply, List<String> problems) {
        StringBuilder correction = new StringBuilder(
                "Your previous response was rejected because it did not satisfy the required format:\n");
        for (String problem : problems) {
            correction.append("- ").append(problem).append('\n');
        }
        correction.append("\nRespond again with ONLY a single JSON object that matches the schema exactly. ")
                .append("Keep only content supported by the study material. ")
                .append("No prose, no markdown, no code fences.");
        String echoed = rejectedReply == null ? "" : rejectedReply;
        if (echoed.length() > MAX_ECHOED_REPLY_CHARS) {
            echoed = echoed.substring(0, MAX_ECHOED_REPLY_CHARS);
        }
        if (echoed.isBlank()) {
            echoed = "(empty response)";
        }
        return original.withCorrection(echoed, correction.toString());
    }
}
