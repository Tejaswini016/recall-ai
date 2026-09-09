package com.recallai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recallai.dto.QuizAttemptRequest.AnswerSubmission;
import com.recallai.entity.Deck;
import com.recallai.entity.Quiz;
import com.recallai.entity.QuizQuestion;
import com.recallai.entity.User;
import com.recallai.exception.ApiException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class QuizScorerTest {

    private List<QuizQuestion> questions;

    @BeforeEach
    void setUp() {
        Quiz quiz = new Quiz(new Deck(new User("Ada", "ada@example.com", "hash"), "Deck", null, null, List.of()),
                "Quiz");
        quiz.addQuestion("Q1", List.of("a", "b", "c", "d"), 0, "E1");
        quiz.addQuestion("Q2", List.of("a", "b", "c", "d"), 1, "E2");
        quiz.addQuestion("Q3", List.of("a", "b", "c", "d"), 2, "E3");
        questions = quiz.getQuestions();
        for (int i = 0; i < questions.size(); i++) {
            ReflectionTestUtils.setField(questions.get(i), "id", (long) (i + 1));
        }
    }

    @Test
    void countsCorrectAnswersAndGradesEveryQuestionInQuizOrder() {
        QuizScorer.Result result = QuizScorer.score(questions, List.of(
                new AnswerSubmission(3L, 2),   // correct, submitted out of order
                new AnswerSubmission(1L, 0),   // correct
                new AnswerSubmission(2L, 3))); // wrong

        assertThat(result.score()).isEqualTo(2);
        assertThat(result.total()).isEqualTo(3);
        assertThat(result.answers()).extracting(a -> a.question().getId()).containsExactly(1L, 2L, 3L);
        assertThat(result.answers()).extracting(QuizScorer.GradedAnswer::correct).containsExactly(true, false, true);
        assertThat(result.answers().get(1).selectedAnswer()).isEqualTo(3);
    }

    @Test
    void skippedQuestionsAreWrongButStillReported() {
        QuizScorer.Result result = QuizScorer.score(questions, List.of(new AnswerSubmission(2L, 1)));

        assertThat(result.score()).isEqualTo(1);
        assertThat(result.total()).isEqualTo(3);
        assertThat(result.answers().get(0).selectedAnswer()).isNull();
        assertThat(result.answers().get(0).correct()).isFalse();
        assertThat(result.answers().get(2).selectedAnswer()).isNull();
    }

    @Test
    void explicitlyNullSelectionCountsAsSkipped() {
        QuizScorer.Result result = QuizScorer.score(questions, List.of(new AnswerSubmission(1L, null)));

        assertThat(result.score()).isZero();
    }

    @Test
    void emptySubmissionScoresZero() {
        QuizScorer.Result result = QuizScorer.score(questions, List.of());

        assertThat(result.score()).isZero();
        assertThat(result.total()).isEqualTo(3);
    }

    @Test
    void rejectsAnswersForQuestionsOutsideTheQuiz() {
        assertThatThrownBy(() -> QuizScorer.score(questions, List.of(new AnswerSubmission(99L, 0))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("does not belong to this quiz");
    }

    @Test
    void rejectsDuplicateAnswersForTheSameQuestion() {
        assertThatThrownBy(() -> QuizScorer.score(questions,
                List.of(new AnswerSubmission(1L, 0), new AnswerSubmission(1L, 1))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("more than once");
    }
}
