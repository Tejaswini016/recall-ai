package com.recallai.entity;

import com.recallai.scheduler.ReviewResult;
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

/**
 * An immutable record of one review: what the user scored and how SM-2 moved the card.
 * Analytics, streaks and weak-topic detection are all derived from these rows.
 */
@Entity
@Table(name = "review_history")
public class ReviewHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "card_id", nullable = false)
    private Card card;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "quality_score", nullable = false)
    private short qualityScore;

    @Column(name = "previous_interval", nullable = false)
    private int previousInterval;

    @Column(name = "new_interval", nullable = false)
    private int newInterval;

    @Column(name = "previous_ease_factor", nullable = false, precision = 4, scale = 2)
    private BigDecimal previousEaseFactor;

    @Column(name = "new_ease_factor", nullable = false, precision = 4, scale = 2)
    private BigDecimal newEaseFactor;

    @Column(name = "reviewed_at", nullable = false, updatable = false)
    private Instant reviewedAt;

    protected ReviewHistory() {
        // JPA
    }

    public ReviewHistory(Card card, User user, ReviewResult result, Instant reviewedAt) {
        this.card = card;
        this.user = user;
        this.qualityScore = (short) result.quality();
        this.previousInterval = result.previousInterval();
        this.newInterval = result.newInterval();
        this.previousEaseFactor = result.previousEaseFactor();
        this.newEaseFactor = result.newEaseFactor();
        this.reviewedAt = reviewedAt;
    }

    public Long getId() {
        return id;
    }

    public Card getCard() {
        return card;
    }

    public User getUser() {
        return user;
    }

    public int getQualityScore() {
        return qualityScore;
    }

    public int getPreviousInterval() {
        return previousInterval;
    }

    public int getNewInterval() {
        return newInterval;
    }

    public BigDecimal getPreviousEaseFactor() {
        return previousEaseFactor;
    }

    public BigDecimal getNewEaseFactor() {
        return newEaseFactor;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }
}
