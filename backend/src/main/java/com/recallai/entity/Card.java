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

    public static final BigDecimal INITIAL_EASE_FACTOR = new BigDecimal("2.50");

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
    private BigDecimal easeFactor = INITIAL_EASE_FACTOR;

    /** Days until the next review after the last successful one. */
    @Column(name = "interval", nullable = false)
    private int intervalDays = 0;

    @Column(nullable = false)
    private int repetitions = 0;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
