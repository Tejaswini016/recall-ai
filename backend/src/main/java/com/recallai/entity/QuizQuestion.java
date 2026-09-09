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
import java.util.List;

/** One multiple-choice question. Options are stored in four columns; {@link #getOptions()} presents them as a list. */
@Entity
@Table(name = "quiz_questions")
public class QuizQuestion {

    public static final int OPTION_COUNT = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @Column(nullable = false, columnDefinition = "text")
    private String question;

    @Column(name = "option_a", nullable = false, columnDefinition = "text")
    private String optionA;

    @Column(name = "option_b", nullable = false, columnDefinition = "text")
    private String optionB;

    @Column(name = "option_c", nullable = false, columnDefinition = "text")
    private String optionC;

    @Column(name = "option_d", nullable = false, columnDefinition = "text")
    private String optionD;

    /** Index 0..3 into the options. */
    @Column(name = "correct_answer", nullable = false)
    private short correctAnswer;

    @Column(nullable = false, columnDefinition = "text")
    private String explanation;

    protected QuizQuestion() {
        // JPA
    }

    QuizQuestion(Quiz quiz, String question, List<String> options, int correctAnswer, String explanation) {
        if (options.size() != OPTION_COUNT) {
            throw new IllegalArgumentException("A quiz question needs exactly " + OPTION_COUNT + " options");
        }
        if (correctAnswer < 0 || correctAnswer >= OPTION_COUNT) {
            throw new IllegalArgumentException("correctAnswer must be between 0 and " + (OPTION_COUNT - 1));
        }
        this.quiz = quiz;
        this.question = question;
        this.optionA = options.get(0);
        this.optionB = options.get(1);
        this.optionC = options.get(2);
        this.optionD = options.get(3);
        this.correctAnswer = (short) correctAnswer;
        this.explanation = explanation;
    }

    public Long getId() {
        return id;
    }

    public Quiz getQuiz() {
        return quiz;
    }

    public String getQuestion() {
        return question;
    }

    public List<String> getOptions() {
        return List.of(optionA, optionB, optionC, optionD);
    }

    public int getCorrectAnswer() {
        return correctAnswer;
    }

    public String getExplanation() {
        return explanation;
    }
}
