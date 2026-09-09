package com.recallai.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "quiz_attempts")
public class QuizAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private int score;

    @Column(name = "total_questions", nullable = false)
    private int totalQuestions;

    @Column(name = "completed_at", nullable = false, updatable = false)
    private Instant completedAt;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @OneToMany(mappedBy = "attempt", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<QuizAttemptAnswer> answers = new ArrayList<>();

    protected QuizAttempt() {
        // JPA
    }

    public QuizAttempt(Quiz quiz, User user, int score, int totalQuestions, Instant completedAt,
                       Integer durationSeconds) {
        this.quiz = quiz;
        this.user = user;
        this.score = score;
        this.totalQuestions = totalQuestions;
        this.completedAt = completedAt;
        this.durationSeconds = durationSeconds;
    }

    public void addAnswer(QuizQuestion question, Integer selectedAnswer, boolean correct) {
        answers.add(new QuizAttemptAnswer(this, question, selectedAnswer, correct));
    }

    public Long getId() {
        return id;
    }

    public Quiz getQuiz() {
        return quiz;
    }

    public User getUser() {
        return user;
    }

    public int getScore() {
        return score;
    }

    public int getTotalQuestions() {
        return totalQuestions;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public List<QuizAttemptAnswer> getAnswers() {
        return List.copyOf(answers);
    }

    public int percent() {
        return totalQuestions == 0 ? 0 : (int) Math.round(score * 100.0 / totalQuestions);
    }
}
