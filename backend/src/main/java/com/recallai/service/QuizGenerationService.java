package com.recallai.service;

import com.recallai.ai.AiGenerationResult;
import com.recallai.ai.AiGenerationService;
import com.recallai.ai.AiProperties;
import com.recallai.ai.GeneratedQuizQuestion;
import com.recallai.config.MaterialProperties;
import com.recallai.dto.GenerateQuizRequest;
import com.recallai.dto.GenerateTopicQuizRequest;
import com.recallai.dto.QuizResponse;
import com.recallai.entity.Card;
import com.recallai.entity.Deck;
import com.recallai.entity.Quiz;
import com.recallai.exception.ApiException;
import com.recallai.exception.ErrorCode;
import com.recallai.repository.CardRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds a quiz from pasted material, the cards already in a deck, or every card on one topic.
 * Like flashcard generation, model calls run outside any database transaction and the quiz is
 * stored only once every chunk has produced validated questions.
 */
@Service
public class QuizGenerationService {

    private static final Logger log = LoggerFactory.getLogger(QuizGenerationService.class);
    static final int DEFAULT_COUNT = 10;
    static final int DEFAULT_TOPIC_COUNT = 5;
    /** Enough cards to describe a topic without exceeding the per-request input limit. */
    static final int MAX_TOPIC_CARDS = 60;

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
        String title = request.title() == null || request.title().isBlank()
                ? deck.getName() + " quiz"
                : request.title().strip();
        return generateFromMaterial(userId, deck, material, count, title);
    }

    /**
     * A practice quiz on one topic, built from the user's cards that carry it (across decks). The
     * quiz is stored under the deck holding most of those cards so it shows up where the student
     * expects it.
     */
    public QuizResponse generateForTopic(Long userId, GenerateTopicQuizRequest request) {
        String topic = request.topic().strip();
        List<Card> cards = cardRepository.findByTopic(userId, topic, PageRequest.of(0, MAX_TOPIC_CARDS));
        if (cards.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "No cards carry the topic \"" + topic + "\"; add or generate some first");
        }
        int count = request.count() == null ? DEFAULT_TOPIC_COUNT : request.count();
        return generateFromMaterial(userId, mostCommonDeck(cards), materialFromCards(cards), count,
                "Practice: " + topic);
    }

    private QuizResponse generateFromMaterial(Long userId, Deck deck, String material, int count, String title) {
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
        return materialFromCards(cards);
    }

    /** Cards as Q/A/explanation blocks; the topic line lets the model reuse the student's own labels. */
    String materialFromCards(List<Card> cards) {
        StringBuilder material = new StringBuilder();
        for (Card card : cards) {
            if (card.getTopic() != null) {
                material.append("Topic: ").append(card.getTopic()).append('\n');
            }
            material.append("Q: ").append(card.getQuestion()).append('\n')
                    .append("A: ").append(card.getAnswer()).append('\n');
            if (card.getExplanation() != null) {
                material.append(card.getExplanation()).append('\n');
            }
            material.append('\n');
        }
        return extractor.clean(material.toString());
    }

    /** The deck most of the cards belong to; ties go to the deck that appears first in the list. */
    static Deck mostCommonDeck(List<Card> cards) {
        Map<Long, Integer> counts = new LinkedHashMap<>();
        Map<Long, Deck> decks = new LinkedHashMap<>();
        for (Card card : cards) {
            counts.merge(card.getDeck().getId(), 1, Integer::sum);
            decks.putIfAbsent(card.getDeck().getId(), card.getDeck());
        }
        Long best = null;
        for (Map.Entry<Long, Integer> entry : counts.entrySet()) {
            if (best == null || entry.getValue() > counts.get(best)) {
                best = entry.getKey();
            }
        }
        return decks.get(best);
    }

    static Quiz newQuiz(Deck deck, String title, List<GeneratedQuizQuestion> questions) {
        Quiz quiz = new Quiz(deck, title);
        for (GeneratedQuizQuestion question : questions) {
            quiz.addQuestion(question.question(), question.options(), question.correctAnswer(),
                    question.explanation(), question.topic());
        }
        return quiz;
    }
}
