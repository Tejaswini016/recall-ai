package com.recallai.entity;

import com.recallai.scheduler.AdaptiveDifficultyService;
import com.recallai.scheduler.DifficultyTier;
import com.recallai.scheduler.Sm2State;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * A flashcard together with its SM-2 scheduling state. Content fields are edited through
 * {@link #updateContent}; scheduling fields change only through {@link #applySchedule},
 * which the review service calls with the output of the SM-2 algorithm.
 */
@Entity
@Table(name = "cards")
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deck_id", nullable = false)
    private Deck deck;

    @Column(nullable = false, columnDefinition = "text")
    private String question;

    @Column(nullable = false, columnDefinition = "text")
    private String answer;

    @Column(columnDefinition = "text")
    private String explanation;

    @Column(length = 150)
    private String topic;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private List<String> tags = new ArrayList<>();

    @Column(name = "ease_factor", nullable = false, precision = 4, scale = 2)
    private BigDecimal easeFactor = Sm2State.INITIAL_EASE_FACTOR;

    /** Days until the next review after the last successful one. */
    @Column(name = "interval", nullable = false)
    private int intervalDays = 0;

    @Column(nullable = false)
    private int repetitions = 0;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    /** Adaptive difficulty (phase 2): the tier and the counters that move it. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DifficultyTier difficulty = DifficultyTier.MEDIUM;

    @Column(name = "success_streak", nullable = false)
    private int successStreak = 0;

    @Column(name = "lapse_count", nullable = false)
    private int lapseCount = 0;

    @Column(name = "total_reviews", nullable = false)
    private int totalReviews = 0;

    @Column(name = "avg_response_ms")
    private Integer avgResponseMs;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Card() {
        // JPA
    }

    public Card(Deck deck, String question, String answer, String explanation, String topic,
                List<String> tags, LocalDate dueDate) {
        this.deck = deck;
        this.question = question;
        this.answer = answer;
        this.explanation = explanation;
        this.topic = topic;
        this.tags = new ArrayList<>(tags);
        this.dueDate = dueDate;
    }

    public void updateContent(String question, String answer, String explanation, String topic,
                              List<String> tags) {
        this.question = question;
        this.answer = answer;
        this.explanation = explanation;
        this.topic = topic;
        this.tags = new ArrayList<>(tags);
    }

    public void applySchedule(BigDecimal easeFactor, int intervalDays, int repetitions, LocalDate dueDate) {
        this.easeFactor = easeFactor;
        this.intervalDays = intervalDays;
        this.repetitions = repetitions;
        this.dueDate = dueDate;
    }

    public AdaptiveDifficultyService.State adaptiveState() {
        return new AdaptiveDifficultyService.State(difficulty, successStreak, lapseCount, totalReviews, avgResponseMs);
    }

    public void applyAdaptive(AdaptiveDifficultyService.Outcome outcome) {
        this.difficulty = outcome.tier();
        this.successStreak = outcome.successStreak();
        this.lapseCount = outcome.lapseCount();
        this.totalReviews = outcome.totalReviews();
        this.avgResponseMs = outcome.avgResponseMs();
    }

    public Long getId() {
        return id;
    }

    public Deck getDeck() {
        return deck;
    }

    public String getQuestion() {
        return question;
    }

    public String getAnswer() {
        return answer;
    }

    public String getExplanation() {
        return explanation;
    }

    public String getTopic() {
        return topic;
    }

    public List<String> getTags() {
        return List.copyOf(tags);
    }

    public BigDecimal getEaseFactor() {
        return easeFactor;
    }

    public int getIntervalDays() {
        return intervalDays;
    }

    public int getRepetitions() {
        return repetitions;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public DifficultyTier getDifficulty() {
        return difficulty;
    }

    public int getSuccessStreak() {
        return successStreak;
    }

    public int getLapseCount() {
        return lapseCount;
    }

    public int getTotalReviews() {
        return totalReviews;
    }

    public Integer getAvgResponseMs() {
        return avgResponseMs;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
