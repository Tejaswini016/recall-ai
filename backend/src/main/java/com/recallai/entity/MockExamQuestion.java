package com.recallai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One exam question together with the answer the student gave (the exam is taken once). */
@Entity
@Table(name = "mock_exam_questions")
public class MockExamQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_id", nullable = false)
    private MockExam exam;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false, length = 20)
    private ExamQuestionType type;

    @Column(nullable = false, columnDefinition = "text")
    private String question;

    /** Four options for MCQ, ["True", "False"] for true/false, empty for short answers. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private List<String> options = new ArrayList<>();

    @Column(name = "correct_option")
    private Short correctOption;

    @Column(name = "correct_answer", columnDefinition = "text")
    private String correctAnswer;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "acceptable_answers", nullable = false, columnDefinition = "text[]")
    private List<String> acceptableAnswers = new ArrayList<>();

    @Column(nullable = false, columnDefinition = "text")
    private String explanation;

    @Column(length = 150)
    private String topic;

    @Column(name = "selected_option")
    private Short selectedOption;

    @Column(name = "answer_text", columnDefinition = "text")
    private String answerText;

    /** Null until the exam is submitted. */
    @Column
    private Boolean correct;

    protected MockExamQuestion() {
        // JPA
    }

    MockExamQuestion(MockExam exam, int sortOrder, ExamQuestionType type, String question, List<String> options,
                     Integer correctOption, String correctAnswer, List<String> acceptableAnswers, String explanation,
                     String topic) {
        this.exam = exam;
        this.sortOrder = sortOrder;
        this.type = type;
        this.question = question;
        this.options = new ArrayList<>(options);
        this.correctOption = correctOption == null ? null : correctOption.shortValue();
        this.correctAnswer = correctAnswer;
        this.acceptableAnswers = new ArrayList<>(acceptableAnswers);
        this.explanation = explanation;
        this.topic = topic;
    }

    public void answer(Integer selectedOption, String answerText, boolean correct) {
        this.selectedOption = selectedOption == null ? null : selectedOption.shortValue();
        this.answerText = answerText;
        this.correct = correct;
    }

    /** The correct answer as text, whatever the question type. */
    public String correctAnswerText() {
        if (type == ExamQuestionType.SHORT_ANSWER || correctOption == null) {
            return correctAnswer == null ? "" : correctAnswer;
        }
        return options.get(correctOption);
    }

    /** The given answer as text, or null when the question was skipped. */
    public String givenAnswerText() {
        if (type == ExamQuestionType.SHORT_ANSWER) {
            return answerText == null || answerText.isBlank() ? null : answerText;
        }
        return selectedOption == null || selectedOption < 0 || selectedOption >= options.size()
                ? null
                : options.get(selectedOption);
    }

    public Long getId() {
        return id;
    }

    public MockExam getExam() {
        return exam;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public ExamQuestionType getType() {
        return type;
    }

    public String getQuestion() {
        return question;
    }

    public List<String> getOptions() {
        return List.copyOf(options);
    }

    public Integer getCorrectOption() {
        return correctOption == null ? null : (int) correctOption;
    }

    public String getCorrectAnswer() {
        return correctAnswer;
    }

    public List<String> getAcceptableAnswers() {
        return List.copyOf(acceptableAnswers);
    }

    public String getExplanation() {
        return explanation;
    }

    public String getTopic() {
        return topic;
    }

    public Integer getSelectedOption() {
        return selectedOption == null ? null : (int) selectedOption;
    }

    public String getAnswerText() {
        return answerText;
    }

    public Boolean getCorrect() {
        return correct;
    }
}
