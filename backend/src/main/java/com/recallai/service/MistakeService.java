package com.recallai.service;

import com.recallai.ai.GeneratedFlashcard;
import com.recallai.dto.CardRequest;
import com.recallai.dto.CardResponse;
import com.recallai.dto.MistakeFlashcardResponse;
import com.recallai.dto.MistakeResponse;
import com.recallai.dto.MistakeSummaryResponse;
import com.recallai.dto.PageResponse;
import com.recallai.entity.Card;
import com.recallai.entity.CardOrigin;
import com.recallai.entity.Deck;
import com.recallai.entity.Mistake;
import com.recallai.entity.MistakeSource;
import com.recallai.entity.MistakeStatus;
import com.recallai.entity.MockExam;
import com.recallai.entity.MockExamQuestion;
import com.recallai.entity.QuizAttempt;
import com.recallai.entity.QuizAttemptAnswer;
import com.recallai.entity.QuizQuestion;
import com.recallai.exception.ApiException;
import com.recallai.exception.ConflictException;
import com.recallai.exception.ErrorCode;
import com.recallai.exception.ResourceNotFoundException;
import com.recallai.repository.CardRepository;
import com.recallai.repository.MistakeRepository;
import com.recallai.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records mistakes as they happen and manages their lifecycle. Turning a mistake into a card is
 * split: {@link MistakeFlashcardService} talks to the model outside any transaction, then calls
 * {@link #persistFlashcard} here.
 */
@Service
public class MistakeService {

    private static final Logger log = LoggerFactory.getLogger(MistakeService.class);
    static final String MISTAKE_TAG = "mistake";

    private final MistakeRepository mistakeRepository;
    private final CardRepository cardRepository;
    private final CardService cardService;
    private final DeckService deckService;
    private final UserRepository userRepository;
    private final Clock clock;

    public MistakeService(MistakeRepository mistakeRepository, CardRepository cardRepository, CardService cardService,
                          DeckService deckService, UserRepository userRepository, Clock clock) {
        this.mistakeRepository = mistakeRepository;
        this.cardRepository = cardRepository;
        this.cardService = cardService;
        this.deckService = deckService;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    /** Every wrong or skipped answer of a submitted attempt becomes (or bumps) a mistake. */
    @Transactional
    public void recordQuizMistakes(Long userId, QuizAttempt attempt) {
        Instant now = clock.instant();
        int recorded = 0;
        for (QuizAttemptAnswer answer : attempt.getAnswers()) {
            if (answer.isCorrect()) {
                continue;
            }
            QuizQuestion question = answer.getQuestion();
            String given = answer.getSelectedAnswer() == null ? null : question.getOptions().get(answer.getSelectedAnswer());
            Optional<Mistake> existing = mistakeRepository.findByUserIdAndQuizQuestionId(userId, question.getId());
            if (existing.isPresent()) {
                existing.get().missedAgain(given, now);
            } else {
                mistakeRepository.save(new Mistake(userRepository.getReferenceById(userId), attempt.getQuiz().getDeck(),
                        MistakeSource.QUIZ, question.getId(), question.getQuestion(), given,
                        question.getOptions().get(question.getCorrectAnswer()), question.getExplanation(),
                        question.getTopic(), now));
            }
            recorded++;
        }
        if (recorded > 0) {
            log.info("User {} recorded {} mistakes from quiz attempt {}", userId, recorded, attempt.getId());
        }
    }

    /** Every wrong or skipped exam answer becomes a mistake with source MOCK_EXAM. */
    @Transactional
    public void recordExamMistakes(Long userId, MockExam exam) {
        Instant now = clock.instant();
        int recorded = 0;
        for (MockExamQuestion question : exam.getQuestions()) {
            if (Boolean.TRUE.equals(question.getCorrect())) {
                continue;
            }
            String given = question.givenAnswerText();
            Optional<Mistake> existing = mistakeRepository.findByUserIdAndMockExamQuestionId(userId, question.getId());
            if (existing.isPresent()) {
                existing.get().missedAgain(given, now);
            } else {
                mistakeRepository.save(Mistake.fromExam(userRepository.getReferenceById(userId), exam.getDeck(),
                        question.getId(), question.getQuestion(), given, question.correctAnswerText(),
                        question.getExplanation(), question.getTopic() != null ? question.getTopic() : exam.getTopic(), now));
            }
            recorded++;
        }
        if (recorded > 0) {
            log.info("User {} recorded {} mistakes from mock exam {}", userId, recorded, exam.getId());
        }
    }

    @Transactional(readOnly = true)
    public Map<Long, Mistake> forExamQuestions(Long userId, Collection<Long> questionIds) {
        if (questionIds.isEmpty()) {
            return Map.of();
        }
        return mistakeRepository.findByUserIdAndMockExamQuestionIdIn(userId, questionIds).stream()
                .collect(Collectors.toMap(Mistake::getMockExamQuestionId, Function.identity()));
    }

    /** Mistakes keyed by quiz question id, for decorating attempt results. */
    @Transactional(readOnly = true)
    public Map<Long, Mistake> forQuizQuestions(Long userId, Collection<Long> questionIds) {
        return mistakeRepository.findOwnedByQuestionIds(userId, questionIds).stream()
                .collect(Collectors.toMap(Mistake::getQuizQuestionId, Function.identity()));
    }

    @Transactional(readOnly = true)
    public PageResponse<MistakeResponse> list(Long userId, MistakeStatus status, MistakeSource source, String topic,
                                              Pageable pageable) {
        return PageResponse.from(mistakeRepository.search(userId,
                status == null ? null : status.name(),
                source == null ? null : source.name(),
                DeckService.blankToNull(topic), pageable), MistakeResponse::from);
    }

    @Transactional(readOnly = true)
    public List<MistakeResponse> recentOpen(Long userId) {
        return mistakeRepository.findTop5ByUserIdAndStatusOrderByLastMissedAtDescIdDesc(userId, MistakeStatus.OPEN)
                .stream().map(MistakeResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public MistakeSummaryResponse summary(Long userId) {
        return new MistakeSummaryResponse(
                mistakeRepository.countByUserIdAndStatus(userId, MistakeStatus.OPEN),
                mistakeRepository.countByUserIdAndStatus(userId, MistakeStatus.CONVERTED),
                mistakeRepository.countByUserIdAndStatus(userId, MistakeStatus.DISMISSED),
                mistakeRepository.countByUserId(userId),
                mistakeRepository.openByTopic(userId).stream()
                        .map(t -> new MistakeSummaryResponse.TopicMistakes(t.getTopic(), t.getCount()))
                        .toList());
    }

    @Transactional(readOnly = true)
    public MistakeResponse get(Long userId, Long mistakeId) {
        return MistakeResponse.from(getOwned(userId, mistakeId));
    }

    @Transactional(readOnly = true)
    public Mistake getOwned(Long userId, Long mistakeId) {
        return mistakeRepository.findByIdAndUserId(mistakeId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Mistake", mistakeId));
    }

    @Transactional
    public MistakeResponse dismiss(Long userId, Long mistakeId) {
        Mistake mistake = getOwned(userId, mistakeId);
        if (mistake.getStatus() == MistakeStatus.CONVERTED) {
            throw new ConflictException("This mistake already became a flashcard; delete the card instead");
        }
        mistake.dismiss(clock.instant());
        return MistakeResponse.from(mistake);
    }

    @Transactional
    public MistakeResponse reopen(Long userId, Long mistakeId) {
        Mistake mistake = getOwned(userId, mistakeId);
        if (mistake.getStatus() == MistakeStatus.CONVERTED && mistake.getCardId() != null
                && cardRepository.existsById(mistake.getCardId())) {
            throw new ConflictException("This mistake already became a flashcard; delete the card first");
        }
        mistake.reopen();
        return MistakeResponse.from(mistake);
    }

    @Transactional
    public void delete(Long userId, Long mistakeId) {
        mistakeRepository.delete(getOwned(userId, mistakeId));
        log.info("User {} deleted mistake {}", userId, mistakeId);
    }

    /** Resolves the deck for a new card: the requested one, else the deck the mistake came from. */
    @Transactional(readOnly = true)
    public Deck deckForFlashcard(Long userId, Mistake mistake, Long requestedDeckId) {
        if (requestedDeckId != null) {
            return deckService.getOwnedDeck(userId, requestedDeckId);
        }
        if (mistake.getDeck() == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "The deck this mistake came from no longer exists; choose a deck for the new card");
        }
        return mistake.getDeck();
    }

    /** Stores the (validated or fallback) card, marks the mistake converted and links the two. */
    @Transactional
    public MistakeFlashcardResponse persistFlashcard(Long userId, Long mistakeId, Long deckId, GeneratedFlashcard generated,
                                                     boolean aiGenerated) {
        Mistake mistake = getOwned(userId, mistakeId);
        if (mistake.getStatus() == MistakeStatus.CONVERTED && mistake.getCardId() != null
                && cardRepository.existsById(mistake.getCardId())) {
            throw new ConflictException("This mistake already has a flashcard");
        }
        List<String> tags = generated.tags().contains(MISTAKE_TAG)
                ? generated.tags()
                : concat(generated.tags(), MISTAKE_TAG);
        CardRequest request = new CardRequest(generated.question(), generated.answer(), generated.explanation(),
                generated.topic() == null ? mistake.getTopic() : generated.topic(), tags);
        Card card = cardService.createCard(userId, deckId, request, CardOrigin.MISTAKE, mistake.getId());
        mistake.convert(card.getId(), clock.instant());
        log.info("User {} converted mistake {} into card {} (ai={})", userId, mistakeId, card.getId(), aiGenerated);
        return new MistakeFlashcardResponse(MistakeResponse.from(mistake), CardResponse.from(card), aiGenerated);
    }

    private static List<String> concat(List<String> tags, String extra) {
        List<String> result = new java.util.ArrayList<>(tags);
        result.add(extra);
        return result;
    }
}
