package com.recallai.entity;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.CreationTimestamp;

/** A timed exam generated from the student's cards; taken once. */
@Entity
@Table(name = "mock_exams")
public class MockExam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deck_id")
    private Deck deck;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 150)
    private String topic;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ExamDifficulty difficulty;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MockExamStatus status = MockExamStatus.IN_PROGRESS;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "time_taken_seconds")
    private Integer timeTakenSeconds;

    @Column(name = "timed_out", nullable = false)
    private boolean timedOut;

    @Column(nullable = false)
    private int score;

    @Column(name = "total_questions", nullable = false)
    private int totalQuestions;

    @OneToMany(mappedBy = "exam", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private List<MockExamQuestion> questions = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected MockExam() {
        // JPA
    }

    public MockExam(User user, Deck deck, String title, String topic, ExamDifficulty difficulty, int durationMinutes,
                    Instant startedAt) {
        this.user = user;
        this.deck = deck;
        this.title = title;
        this.topic = topic;
        this.difficulty = difficulty;
        this.durationMinutes = durationMinutes;
        this.startedAt = startedAt;
    }

    public MockExamQuestion addQuestion(ExamQuestionType type, String question, List<String> options,
                                        Integer correctOption, String correctAnswer, List<String> acceptableAnswers,
                                        String explanation, String topic) {
        MockExamQuestion q = new MockExamQuestion(this, questions.size(), type, question, options, correctOption,
                correctAnswer, acceptableAnswers, explanation, topic);
        questions.add(q);
        totalQuestions = questions.size();
        return q;
    }

    public Instant expiresAt() {
        return startedAt.plus(Duration.ofMinutes(durationMinutes));
    }

    public void submit(int score, Instant now, boolean timedOut) {
        this.status = MockExamStatus.SUBMITTED;
        this.score = score;
        this.submittedAt = now;
        this.timeTakenSeconds = (int) Math.max(0, Duration.between(startedAt, now).getSeconds());
        this.timedOut = timedOut;
    }

    public int percent() {
        return totalQuestions == 0 ? 0 : (int) Math.round(score * 100.0 / totalQuestions);
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Deck getDeck() {
        return deck;
    }

    public String getTitle() {
        return title;
    }

    public String getTopic() {
        return topic;
    }

    public ExamDifficulty getDifficulty() {
        return difficulty;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public MockExamStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Integer getTimeTakenSeconds() {
        return timeTakenSeconds;
    }

    public boolean isTimedOut() {
        return timedOut;
    }

    public int getScore() {
        return score;
    }

    public int getTotalQuestions() {
        return totalQuestions;
    }

    public List<MockExamQuestion> getQuestions() {
        return List.copyOf(questions);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
