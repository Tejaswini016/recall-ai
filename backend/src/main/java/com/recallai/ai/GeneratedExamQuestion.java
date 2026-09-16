package com.recallai.ai;

import com.recallai.entity.ExamQuestionType;
import java.util.List;

/**
 * A validated exam question of any supported type.
 *
 * @param options           four for MCQ, ["True", "False"] for true/false, empty for short answers
 * @param correctOption     index into options; null for short answers
 * @param correctAnswer     model answer for short answers; null otherwise
 * @param acceptableAnswers alternative short answers that also count as correct
 */
public record GeneratedExamQuestion(
        ExamQuestionType type,
        String question,
        List<String> options,
        Integer correctOption,
        String correctAnswer,
        List<String> acceptableAnswers,
        String explanation,
        String topic) {
}
