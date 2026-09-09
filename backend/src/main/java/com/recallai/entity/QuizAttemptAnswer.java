package com.recallai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "quiz_attempt_answers")
public class QuizAttemptAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false)
    private QuizAttempt attempt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private QuizQuestion question;

    /** Null when the question was skipped. */
    @Column(name = "selected_answer")
    private Short selectedAnswer;

    @Column(nullable = false)
    private boolean correct;

    protected QuizAttemptAnswer() {
        // JPA
    }

    QuizAttemptAnswer(QuizAttempt attempt, QuizQuestion question, Integer selectedAnswer, boolean correct) {
        this.attempt = attempt;
        this.question = question;
        this.selectedAnswer = selectedAnswer == null ? null : selectedAnswer.shortValue();
        this.correct = correct;
    }

    public Long getId() {
        return id;
    }

    public QuizQuestion getQuestion() {
        return question;
    }

    public Integer getSelectedAnswer() {
        return selectedAnswer == null ? null : (int) selectedAnswer;
    }

    public boolean isCorrect() {
        return correct;
    }
}
