package com.recallai.service;

import com.recallai.ai.AiGenerationResult;
import com.recallai.ai.AiGenerationService;
import com.recallai.ai.AiProperties;
import com.recallai.ai.GeneratedExamQuestion;
import com.recallai.config.MaterialProperties;
import com.recallai.dto.CreateMockExamRequest;
import com.recallai.dto.MockExamResponse;
import com.recallai.dto.MockExamResultResponse;
import com.recallai.dto.MockExamStatsResponse;
import com.recallai.dto.MockExamSubmitRequest;
import com.recallai.dto.MockExamSummaryResponse;
import com.recallai.entity.Card;
import com.recallai.entity.Deck;
import com.recallai.entity.ExamQuestionType;
import com.recallai.entity.Mistake;
import com.recallai.entity.MockExam;
import com.recallai.entity.MockExamQuestion;
import com.recallai.entity.MockExamStatus;
import com.recallai.exception.ApiException;
import com.recallai.exception.ConflictException;
import com.recallai.exception.ErrorCode;
import com.recallai.exception.ResourceNotFoundException;
import com.recallai.repository.CardRepository;
import com.recallai.repository.MockExamRepository;
import com.recallai.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Mock exams: generated from the student's cards (topic, deck or most recent), taken once against
 * a clock, graded locally (option index for MCQ and true/false, {@link ShortAnswerGrader} for
 * short answers) and fed back into mistakes and topic insight.
 */
@Service
public class MockExamService {

    private static final Logger log = LoggerFactory.getLogger(MockExamService.class);
    static final int MAX_MATERIAL_CARDS = 60;
    /** Late submissions within this window are still graded, but flagged as timed out. */
    static final Duration GRACE = Duration.ofSeconds(60);
    static final int STRONG_TOPIC_PERCENT = 70;
    private static final int RECENT = 5;

    private final MockExamRepository examRepository;
    private final CardRepository cardRepository;
    private final UserRepository userRepository;
    private final DeckService deckService;
    private final QuizGenerationService quizGenerationService;
    private final AiGenerationService aiGenerationService;
    private final AiProperties aiProperties;
    private final MaterialProperties materialProperties;
    private final MistakeService mistakeService;
    private final Clock clock;
    private final TransactionTemplate writeTx;

    public MockExamService(MockExamRepository examRepository, CardRepository cardRepository,
                           UserRepository userRepository, DeckService deckService,
                           QuizGenerationService quizGenerationService, AiGenerationService aiGenerationService,
                           AiProperties aiProperties, MaterialProperties materialProperties,
                           MistakeService mistakeService, PlatformTransactionManager transactionManager, Clock clock) {
        this.examRepository = examRepository;
        this.cardRepository = cardRepository;
        this.userRepository = userRepository;
        this.deckService = deckService;
        this.quizGenerationService = quizGenerationService;
        this.aiGenerationService = aiGenerationService;
        this.aiProperties = aiProperties;
        this.materialProperties = materialProperties;
        this.mistakeService = mistakeService;
        this.clock = clock;
        this.writeTx = new TransactionTemplate(transactionManager);
    }

    /** Generates the questions (model call outside any transaction) and starts the clock. */
    public MockExamResponse create(Long userId, CreateMockExamRequest request) {
        Set<ExamQuestionType> types = request.questionTypes() == null || request.questionTypes().isEmpty()
                ? EnumSet.allOf(ExamQuestionType.class)
                : EnumSet.copyOf(request.questionTypes());
        String topic = DeckService.blankToNull(request.topic());
        Deck deck = request.deckId() == null ? null : deckService.getOwnedDeck(userId, request.deckId());
        List<Card> cards = material(userId, topic, deck);
        String material = quizGenerationService.materialFromCards(cards);
        List<String> chunks = TextChunker.chunk(material, aiProperties.maxInputChars());
        if (chunks.size() > materialProperties.maxChunks()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Too much material for one exam; narrow the topic");
        }
        List<Integer> shares = GenerationPlanner.shares(chunks, request.questionCount(), CreateMockExamRequest.MAX_QUESTIONS);

        List<GeneratedExamQuestion> merged = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < chunks.size(); i++) {
            AiGenerationResult<GeneratedExamQuestion> result = aiGenerationService.generateMockExam(
                    chunks.get(i), shares.get(i), request.difficulty(), types);
            for (GeneratedExamQuestion question : result.items()) {
                if (seen.add(question.question().toLowerCase(Locale.ROOT))) {
                    merged.add(question);
                }
            }
        }
        List<GeneratedExamQuestion> selected = merged.size() > request.questionCount()
                ? merged.subList(0, request.questionCount()) : merged;
        String title = DeckService.blankToNull(request.title()) != null
                ? request.title().strip()
                : "Mock exam: " + (topic != null ? topic : deck != null ? deck.getName() : "all cards");
        Deck examDeck = deck != null ? deck : QuizGenerationService.mostCommonDeck(cards);

        MockExam exam = writeTx.execute(status -> {
            MockExam created = new MockExam(userRepository.getReferenceById(userId), examDeck, title, topic,
                    request.difficulty(), request.durationMinutes(), clock.instant());
            for (GeneratedExamQuestion q : selected) {
                created.addQuestion(q.type(), q.question(), q.options(), q.correctOption(), q.correctAnswer(),
                        q.acceptableAnswers(), q.explanation(), q.topic());
            }
            return examRepository.save(created);
        });
        log.info("User {} started mock exam {} ({} questions, {} min, {})", userId, exam.getId(),
                exam.getTotalQuestions(), exam.getDurationMinutes(), exam.getDifficulty());
        return toResponse(exam);
    }

    private List<Card> material(Long userId, String topic, Deck deck) {
        List<Card> cards;
        if (topic != null) {
            cards = cardRepository.findByTopic(userId, topic, PageRequest.of(0, MAX_MATERIAL_CARDS));
            if (deck != null) {
                cards = cards.stream().filter(c -> c.getDeck().getId().equals(deck.getId())).toList();
            }
        } else if (deck != null) {
            cards = cardRepository.findByDeckIdOrderByIdAsc(deck.getId());
            if (cards.size() > MAX_MATERIAL_CARDS) {
                cards = cards.subList(0, MAX_MATERIAL_CARDS);
            }
        } else {
            cards = cardRepository.search(userId, null, null, null, null, PageRequest.of(0, MAX_MATERIAL_CARDS))
                    .getContent();
        }
        if (cards.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, topic != null
                    ? "No cards carry the topic \"" + topic + "\"; add or generate some first"
                    : "There are no cards to build an exam from yet");
        }
        return cards;
    }

    @Transactional(readOnly = true)
    public MockExamResponse get(Long userId, Long examId) {
        return toResponse(getOwned(userId, examId));
    }

    @Transactional
    public MockExamResultResponse submit(Long userId, Long examId, MockExamSubmitRequest request) {
        MockExam exam = getOwned(userId, examId);
        if (exam.getStatus() == MockExamStatus.SUBMITTED) {
            throw new ConflictException("This exam was already submitted");
        }
        Instant now = clock.instant();
        boolean timedOut = now.isAfter(exam.expiresAt());
        if (now.isAfter(exam.expiresAt().plus(GRACE))) {
            log.info("Mock exam {} submitted {}s after its deadline; graded but flagged", examId,
                    Duration.between(exam.expiresAt(), now).getSeconds());
        }
        Map<Long, MockExamSubmitRequest.Answer> byQuestion = new HashMap<>();
        Set<Long> valid = new HashSet<>();
        for (MockExamQuestion question : exam.getQuestions()) {
            valid.add(question.getId());
        }
        for (MockExamSubmitRequest.Answer answer : request.answers()) {
            if (!valid.contains(answer.questionId())) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                        "Question " + answer.questionId() + " does not belong to this exam");
            }
            if (byQuestion.put(answer.questionId(), answer) != null) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                        "Question " + answer.questionId() + " was answered more than once");
            }
        }
        int score = 0;
        for (MockExamQuestion question : exam.getQuestions()) {
            MockExamSubmitRequest.Answer answer = byQuestion.get(question.getId());
            boolean correct = grade(question, answer);
            question.answer(answer == null ? null : answer.selectedOption(),
                    answer == null ? null : DeckService.blankToNull(answer.answerText()), correct);
            if (correct) {
                score++;
            }
        }
        exam.submit(score, now, timedOut);
        mistakeService.recordExamMistakes(userId, exam);
        log.info("User {} submitted mock exam {} scoring {}/{} in {}s{}", userId, examId, score,
                exam.getTotalQuestions(), exam.getTimeTakenSeconds(), timedOut ? " (timed out)" : "");
        return toResult(exam, mistakeService.forExamQuestions(userId, valid));
    }

    static boolean grade(MockExamQuestion question, MockExamSubmitRequest.Answer answer) {
        if (answer == null) {
            return false;
        }
        if (question.getType() == ExamQuestionType.SHORT_ANSWER) {
            return ShortAnswerGrader.isCorrect(answer.answerText(), question.getCorrectAnswer(),
                    question.getAcceptableAnswers());
        }
        return answer.selectedOption() != null && question.getCorrectOption() != null
                && answer.selectedOption().intValue() == question.getCorrectOption();
    }

    @Transactional(readOnly = true)
    public MockExamResultResponse results(Long userId, Long examId) {
        MockExam exam = getOwned(userId, examId);
        if (exam.getStatus() != MockExamStatus.SUBMITTED) {
            throw new ConflictException("This exam has not been submitted yet");
        }
        Set<Long> ids = new HashSet<>();
        exam.getQuestions().forEach(q -> ids.add(q.getId()));
        return toResult(exam, mistakeService.forExamQuestions(userId, ids));
    }

    @Transactional(readOnly = true)
    public List<MockExamSummaryResponse> list(Long userId, int limit) {
        return examRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId, PageRequest.of(0, limit)).stream()
                .map(MockExamSummaryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public MockExamStatsResponse stats(Long userId) {
        List<MockExamSummaryResponse> recent = list(userId, RECENT);
        Integer latest = recent.stream().filter(e -> e.percent() != null).map(MockExamSummaryResponse::percent)
                .findFirst().orElse(null);
        Double average = examRepository.averagePercent(userId, MockExamStatus.SUBMITTED);
        Double best = examRepository.bestPercent(userId, MockExamStatus.SUBMITTED);
        return new MockExamStatsResponse(examRepository.countByUserId(userId),
                examRepository.countByUserIdAndStatus(userId, MockExamStatus.SUBMITTED),
                average == null ? null : (int) Math.round(average),
                best == null ? null : (int) Math.round(best), latest, recent);
    }

    @Transactional
    public void delete(Long userId, Long examId) {
        examRepository.delete(getOwned(userId, examId));
        log.info("User {} deleted mock exam {}", userId, examId);
    }

    private MockExam getOwned(Long userId, Long examId) {
        return examRepository.findWithQuestions(examId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Mock exam", examId));
    }

    private MockExamResponse toResponse(MockExam exam) {
        long remaining = Math.max(0, Duration.between(clock.instant(), exam.expiresAt()).getSeconds());
        return new MockExamResponse(exam.getId(), exam.getTitle(), exam.getTopic(),
                exam.getDeck() == null ? null : exam.getDeck().getId(), exam.getDifficulty(),
                exam.getDurationMinutes(), exam.getStatus(), exam.getStartedAt(), exam.expiresAt(),
                exam.getStatus() == MockExamStatus.SUBMITTED ? 0 : remaining, exam.getTotalQuestions(),
                exam.getQuestions().stream().map(q -> new MockExamResponse.Question(q.getId(), q.getSortOrder(),
                        q.getType(), q.getQuestion(), q.getOptions(), q.getTopic())).toList());
    }

    private MockExamResultResponse toResult(MockExam exam, Map<Long, Mistake> mistakes) {
        Map<String, int[]> byTopic = new LinkedHashMap<>();
        List<MockExamResultResponse.QuestionResult> questions = new ArrayList<>();
        int skipped = 0;
        for (MockExamQuestion q : exam.getQuestions()) {
            boolean correct = Boolean.TRUE.equals(q.getCorrect());
            boolean wasSkipped = q.givenAnswerText() == null;
            if (wasSkipped) {
                skipped++;
            }
            String topic = q.getTopic() != null ? q.getTopic() : exam.getTopic() != null ? exam.getTopic() : "General";
            int[] tally = byTopic.computeIfAbsent(topic.strip(), t -> new int[2]);
            tally[1]++;
            if (correct) {
                tally[0]++;
            }
            Mistake mistake = mistakes.get(q.getId());
            questions.add(new MockExamResultResponse.QuestionResult(q.getId(), q.getSortOrder(), q.getType(),
                    q.getQuestion(), q.getOptions(), q.getTopic(), q.getSelectedOption(), q.getAnswerText(),
                    q.getCorrectOption(), q.getType() == ExamQuestionType.SHORT_ANSWER ? q.getCorrectAnswer() : q.correctAnswerText(),
                    q.getAcceptableAnswers(), correct, wasSkipped, q.getExplanation(),
                    mistake == null ? null : mistake.getId(), mistake == null ? null : mistake.getStatus(),
                    mistake == null ? null : mistake.getCardId()));
        }
        List<MockExamResultResponse.TopicResult> topics = new ArrayList<>();
        List<String> strong = new ArrayList<>();
        List<String> weak = new ArrayList<>();
        for (Map.Entry<String, int[]> entry : byTopic.entrySet()) {
            int percent = TopicCategorizer.percent(entry.getValue()[0], entry.getValue()[1]);
            boolean isStrong = percent >= STRONG_TOPIC_PERCENT;
            topics.add(new MockExamResultResponse.TopicResult(entry.getKey(), entry.getValue()[0], entry.getValue()[1],
                    percent, isStrong));
            (isStrong ? strong : weak).add(entry.getKey());
        }
        topics.sort((a, b) -> Integer.compare(a.percent(), b.percent()));
        int correctCount = exam.getScore();
        return new MockExamResultResponse(exam.getId(), exam.getTitle(), exam.getTopic(), exam.getDifficulty(),
                exam.getScore(), exam.getTotalQuestions(), exam.percent(), correctCount,
                exam.getTotalQuestions() - correctCount - skipped, skipped, exam.getDurationMinutes(),
                exam.getTimeTakenSeconds(), exam.isTimedOut(), exam.getStartedAt(), exam.getSubmittedAt(), topics,
                strong, weak, questions);
    }
}
