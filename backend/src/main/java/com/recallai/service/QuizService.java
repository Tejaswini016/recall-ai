package com.recallai.service;

import com.recallai.ai.GeneratedQuizQuestion;
import com.recallai.dto.PageResponse;
import com.recallai.dto.QuizAttemptRequest;
import com.recallai.dto.QuizAttemptResponse;
import com.recallai.dto.QuizAttemptSummaryResponse;
import com.recallai.dto.QuizQuestionResponse;
import com.recallai.dto.QuizResponse;
import com.recallai.dto.QuizSummaryResponse;
import com.recallai.entity.Quiz;
import com.recallai.entity.QuizAttempt;
import com.recallai.entity.QuizAttemptAnswer;
import com.recallai.exception.ResourceNotFoundException;
import com.recallai.repository.QuizAttemptRepository;
import com.recallai.repository.QuizRepository;
import com.recallai.repository.QuizStats;
import com.recallai.repository.UserRepository;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuizService {

    private static final Logger log = LoggerFactory.getLogger(QuizService.class);

    private final QuizRepository quizRepository;
    private final QuizAttemptRepository attemptRepository;
    private final UserRepository userRepository;
    private final DeckService deckService;
    private final Clock clock;

    public QuizService(QuizRepository quizRepository, QuizAttemptRepository attemptRepository,
                       UserRepository userRepository, DeckService deckService, Clock clock) {
        this.quizRepository = quizRepository;
        this.attemptRepository = attemptRepository;
        this.userRepository = userRepository;
        this.deckService = deckService;
        this.clock = clock;
    }

    @Transactional
    public QuizResponse store(Long userId, Long deckId, String title, List<GeneratedQuizQuestion> questions) {
        Quiz quiz = quizRepository.save(QuizGenerationService.newQuiz(
                deckService.getOwnedDeck(userId, deckId), title, questions));
        return toResponse(quiz, 0, null);
    }

    @Transactional(readOnly = true)
    public QuizResponse get(Long userId, Long quizId) {
        Quiz quiz = getOwnedQuiz(userId, quizId);
        QuizStats stats = statsFor(List.of(quiz.getId()), userId).get(quiz.getId());
        return toResponse(quiz, stats == null ? 0 : stats.getAttemptCount(), bestPercent(stats));
    }

    @Transactional(readOnly = true)
    public PageResponse<QuizSummaryResponse> list(Long userId, Long deckId, Pageable pageable) {
        if (deckId != null) {
            deckService.getOwnedDeck(userId, deckId);
        }
        Page<Quiz> page = deckId == null
                ? quizRepository.findByDeckUserIdOrderByCreatedAtDescIdDesc(userId, pageable)
                : quizRepository.findByDeckIdAndDeckUserIdOrderByCreatedAtDescIdDesc(deckId, userId, pageable);
        List<Long> ids = page.getContent().stream().map(Quiz::getId).toList();
        Map<Long, QuizStats> stats = statsFor(ids, userId);
        Map<Long, Long> questionCounts = ids.isEmpty() ? Map.of() : quizRepository.countQuestions(ids).stream()
                .collect(Collectors.toMap(QuizRepository.QuestionCount::getQuizId,
                        QuizRepository.QuestionCount::getQuestionCount));
        return PageResponse.from(page, quiz -> {
            QuizStats quizStats = stats.get(quiz.getId());
            return new QuizSummaryResponse(quiz.getId(), quiz.getDeck().getId(), quiz.getDeck().getName(),
                    quiz.getTitle(), questionCounts.getOrDefault(quiz.getId(), 0L).intValue(),
                    quizStats == null ? 0 : quizStats.getAttemptCount(), bestPercent(quizStats), quiz.getCreatedAt());
        });
    }

    @Transactional
    public void delete(Long userId, Long quizId) {
        Quiz quiz = getOwnedQuiz(userId, quizId);
        quizRepository.delete(quiz);
        log.info("User {} deleted quiz {}", userId, quizId);
    }

    @Transactional
    public QuizAttemptResponse submitAttempt(Long userId, Long quizId, QuizAttemptRequest request) {
        Quiz quiz = getOwnedQuiz(userId, quizId);
        QuizScorer.Result result = QuizScorer.score(quiz.getQuestions(), request.answers());

        QuizAttempt attempt = new QuizAttempt(quiz, userRepository.getReferenceById(userId), result.score(),
                result.total(), clock.instant(), request.durationSeconds());
        for (QuizScorer.GradedAnswer graded : result.answers()) {
            attempt.addAnswer(graded.question(), graded.selectedAnswer(), graded.correct());
        }
        attempt = attemptRepository.save(attempt);
        log.info("User {} completed quiz {} scoring {}/{}", userId, quizId, result.score(), result.total());
        return toAttemptResponse(attempt);
    }

    @Transactional(readOnly = true)
    public List<QuizAttemptSummaryResponse> listAttempts(Long userId, Long quizId) {
        getOwnedQuiz(userId, quizId);
        return attemptRepository.findByQuizIdAndUserIdOrderByCompletedAtDescIdDesc(quizId, userId)
                .stream().map(QuizAttemptSummaryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public QuizAttemptResponse getAttempt(Long userId, Long quizId, Long attemptId) {
        return attemptRepository.findDetail(attemptId, quizId, userId)
                .map(QuizService::toAttemptResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz attempt", attemptId));
    }

    private Quiz getOwnedQuiz(Long userId, Long quizId) {
        return quizRepository.findByIdAndDeckUserId(quizId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz", quizId));
    }

    private Map<Long, QuizStats> statsFor(List<Long> quizIds, Long userId) {
        if (quizIds.isEmpty()) {
            return Map.of();
        }
        return quizRepository.findStats(quizIds, userId).stream()
                .collect(Collectors.toMap(QuizStats::getQuizId, Function.identity()));
    }

    private static Integer bestPercent(QuizStats stats) {
        return stats == null || stats.getBestPercent() == null ? null : (int) Math.round(stats.getBestPercent());
    }

    private static QuizResponse toResponse(Quiz quiz, long attemptCount, Integer bestPercent) {
        List<QuizQuestionResponse> questions = quiz.getQuestions().stream().map(QuizQuestionResponse::from).toList();
        return new QuizResponse(quiz.getId(), quiz.getDeck().getId(), quiz.getDeck().getName(), quiz.getTitle(),
                questions.size(), attemptCount, bestPercent, quiz.getCreatedAt(), questions);
    }

    private static QuizAttemptResponse toAttemptResponse(QuizAttempt attempt) {
        List<QuizAttemptResponse.QuestionResult> results = attempt.getAnswers().stream()
                .map(QuizService::toQuestionResult)
                .toList();
        int correct = attempt.getScore();
        return new QuizAttemptResponse(attempt.getId(), attempt.getQuiz().getId(), attempt.getQuiz().getTitle(),
                attempt.getScore(), attempt.getTotalQuestions(), attempt.percent(), correct,
                attempt.getTotalQuestions() - correct, attempt.getCompletedAt(), attempt.getDurationSeconds(), results);
    }

    private static QuizAttemptResponse.QuestionResult toQuestionResult(QuizAttemptAnswer answer) {
        return new QuizAttemptResponse.QuestionResult(
                answer.getQuestion().getId(),
                answer.getQuestion().getQuestion(),
                answer.getQuestion().getOptions(),
                answer.getSelectedAnswer(),
                answer.getQuestion().getCorrectAnswer(),
                answer.isCorrect(),
                answer.getQuestion().getExplanation());
    }
}
