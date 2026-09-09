package com.recallai.dto;

import com.recallai.entity.QuizQuestion;
import java.util.List;

/** A question as shown while taking the quiz: the correct answer and explanation are withheld. */
public record QuizQuestionResponse(Long id, String question, List<String> options) {

    public static QuizQuestionResponse from(QuizQuestion question) {
        return new QuizQuestionResponse(question.getId(), question.getQuestion(), question.getOptions());
    }
}
