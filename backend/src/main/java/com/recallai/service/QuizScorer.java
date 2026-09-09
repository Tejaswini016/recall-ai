package com.recallai.service;

import com.recallai.dto.QuizAttemptRequest;
import com.recallai.entity.QuizQuestion;
import com.recallai.exception.ApiException;
import com.recallai.exception.ErrorCode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic quiz scoring. Pure: takes the quiz's questions and the submitted answers,
 * validates the submission against the quiz, and produces one graded entry per question in
 * quiz order. Skipped questions are wrong.
 */
public final class QuizScorer {

    private QuizScorer() {
    }

    public record GradedAnswer(QuizQuestion question, Integer selectedAnswer, boolean correct) {
    }

    public record Result(List<GradedAnswer> answers, int score) {

        public int total() {
            return answers.size();
        }
    }

    public static Result score(List<QuizQuestion> questions, List<QuizAttemptRequest.AnswerSubmission> submissions) {
        Map<Long, Integer> selectedByQuestion = new HashMap<>();
        Set<Long> seen = new HashSet<>();
        Set<Long> valid = new HashSet<>();
        for (QuizQuestion question : questions) {
            valid.add(question.getId());
        }
        for (QuizAttemptRequest.AnswerSubmission submission : submissions) {
            if (!valid.contains(submission.questionId())) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                        "Question " + submission.questionId() + " does not belong to this quiz");
            }
            if (!seen.add(submission.questionId())) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                        "Question " + submission.questionId() + " was answered more than once");
            }
            selectedByQuestion.put(submission.questionId(), submission.selectedAnswer());
        }

        List<GradedAnswer> graded = new ArrayList<>();
        int score = 0;
        for (QuizQuestion question : questions) {
            Integer selected = selectedByQuestion.get(question.getId());
            boolean correct = selected != null && selected == question.getCorrectAnswer();
            if (correct) {
                score++;
            }
            graded.add(new GradedAnswer(question, selected, correct));
        }
        return new Result(List.copyOf(graded), score);
    }
}
