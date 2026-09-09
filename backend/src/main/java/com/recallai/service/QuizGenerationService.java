package com.recallai.service;

import com.recallai.ai.AiGenerationResult;
import com.recallai.ai.AiGenerationService;
import com.recallai.ai.AiProperties;
import com.recallai.ai.GeneratedQuizQuestion;
import com.recallai.config.MaterialProperties;
import com.recallai.dto.GenerateQuizRequest;
import com.recallai.dto.QuizResponse;
import com.recallai.entity.Card;
import com.recallai.entity.Deck;
import com.recallai.entity.Quiz;
import com.recallai.exception.ApiException;
import com.recallai.exception.ErrorCode;
import com.recallai.repository.CardRepository;
import com.recallai.repository.QuizRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds a quiz from either pasted material or the cards already in a deck. Like flashcard
 * generation, model calls run outside any database transaction and the quiz is stored only
 * once every chunk has produced validated questions.
 */
@Service
public class QuizGenerationService {

    private static final Logger log = LoggerFactory.getLogger(QuizGenerationService.class);
    static final int DEFAULT_COUNT = 10;

    private final DeckService deckService;
    private final CardRepository cardRepository;
    private final StudyMaterialExtractor extractor;
    private final AiGenerationService aiGenerationService;
    private final QuizService quizService;
    private final AiProperties aiProperties;
    private final MaterialProperties materialProperties;

    public QuizGenerationService(DeckService deckService, CardRepository cardRepository,
                                 StudyMaterialExtractor extractor, AiGenerationService aiGenerationService,
                                 QuizService quizService, AiProperties aiProperties,
                                 MaterialProperties materialProperties) {
        this.deckService = deckService;
        this.cardRepository = cardRepository;
        this.extractor = extractor;
        this.aiGenerationService = aiGenerationService;
        this.quizService = quizService;
        this.aiProperties = aiProperties;
        this.materialProperties = materialProperties;
    }

    public QuizResponse generate(Long userId, GenerateQuizRequest request) {
        Deck deck = deckService.getOwnedDeck(userId, request.deckId());
        String material = request.text() != null && !request.text().isBlank()
                ? extractor.fromText(request.text())
                : materialFromDeck(deck);
        int count = request.count() == null ? DEFAULT_COUNT : request.count();

        List<String> chunks = TextChunker.chunk(material, aiProperties.maxInputChars());
        if (chunks.size() > materialProperties.maxChunks()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Study material is too long for one quiz ("
                    + chunks.size() + " parts; the maximum is " + materialProperties.maxChunks() + ")");
        }
        List<Integer> shares = GenerationPlanner.shares(chunks, count, aiProperties.maxQuizQuestions());

        List<GeneratedQuizQuestion> merged = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int cachedChunks = 0;
        for (int i = 0; i < chunks.size(); i++) {
            AiGenerationResult<GeneratedQuizQuestion> result =
                    aiGenerationService.generateQuiz(chunks.get(i), shares.get(i));
            if (result.cached()) {
                cachedChunks++;
            }
            for (GeneratedQuizQuestion question : result.items()) {
                if (seen.add(question.question().toLowerCase(Locale.ROOT))) {
                    merged.add(question);
                }
            }
        }
        List<GeneratedQuizQuestion> selected = merged.size() > count ? merged.subList(0, count) : merged;

        String title = request.title() == null || request.title().isBlank()
                ? deck.getName() + " quiz"
                : request.title().strip();
        QuizResponse response = quizService.store(userId, deck.getId(), title, selected);
        log.info("User {} generated quiz {} with {} questions for deck {} ({} chunks, {} cached)",
                userId, response.id(), response.questionCount(), deck.getId(), chunks.size(), cachedChunks);
        return response;
    }

    /** Renders a deck's cards as study material so quizzes test what the student is learning. */
    @Transactional(readOnly = true)
    protected String materialFromDeck(Deck deck) {
        List<Card> cards = cardRepository.findByDeckIdOrderByIdAsc(deck.getId());
        if (cards.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "This deck has no cards yet; add cards or provide study material to build a quiz from");
        }
        StringBuilder material = new StringBuilder();
        for (Card card : cards) {
            material.append("Q: ").append(card.getQuestion()).append('\n')
                    .append("A: ").append(card.getAnswer()).append('\n');
            if (card.getExplanation() != null) {
                material.append(card.getExplanation()).append('\n');
            }
            material.append('\n');
        }
        return extractor.clean(material.toString());
    }

    static Quiz newQuiz(Deck deck, String title, List<GeneratedQuizQuestion> questions) {
        Quiz quiz = new Quiz(deck, title);
        for (GeneratedQuizQuestion question : questions) {
            quiz.addQuestion(question.question(), question.options(), question.correctAnswer(),
                    question.explanation());
        }
        return quiz;
    }
}
