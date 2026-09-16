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
import java.time.Instant;

/**
 * A question the student got wrong, kept with its text so it survives the quiz being deleted.
 * Repeat misses of the same question bump {@code occurrences} rather than adding rows.
 */
@Entity
@Table(name = "mistakes")
public class Mistake {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** The deck the question came from; null once that deck is deleted. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deck_id")
    private Deck deck;

    @Column(name = "quiz_question_id")
    private Long quizQuestionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MistakeSource source;

    @Column(nullable = false, columnDefinition = "text")
    private String question;

    @Column(name = "given_answer", columnDefinition = "text")
    private String givenAnswer;

    @Column(name = "correct_answer", nullable = false, columnDefinition = "text")
    private String correctAnswer;

    @Column(columnDefinition = "text")
    private String explanation;

    @Column(length = 150)
    private String topic;

    @Column(nullable = false)
    private int occurrences = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MistakeStatus status = MistakeStatus.OPEN;

    @Column(name = "card_id")
    private Long cardId;

    @Column(name = "first_missed_at", nullable = false, updatable = false)
    private Instant firstMissedAt;

    @Column(name = "last_missed_at", nullable = false)
    private Instant lastMissedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected Mistake() {
        // JPA
    }

    public Mistake(User user, Deck deck, MistakeSource source, Long quizQuestionId, String question,
                   String givenAnswer, String correctAnswer, String explanation, String topic, Instant now) {
        this.user = user;
        this.deck = deck;
        this.source = source;
        this.quizQuestionId = quizQuestionId;
        this.question = question;
        this.givenAnswer = givenAnswer;
        this.correctAnswer = correctAnswer;
        this.explanation = explanation;
        this.topic = topic;
        this.firstMissedAt = now;
        this.lastMissedAt = now;
    }

    /** The same question was missed again: count it and reopen a dismissed mistake. */
    public void missedAgain(String givenAnswer, Instant now) {
        this.occurrences++;
        this.lastMissedAt = now;
        this.givenAnswer = givenAnswer;
        if (status == MistakeStatus.DISMISSED) {
            status = MistakeStatus.OPEN;
            resolvedAt = null;
        }
    }

    public void convert(Long cardId, Instant now) {
        this.status = MistakeStatus.CONVERTED;
        this.cardId = cardId;
        this.resolvedAt = now;
    }

    public void dismiss(Instant now) {
        this.status = MistakeStatus.DISMISSED;
        this.resolvedAt = now;
    }

    public void reopen() {
        this.status = MistakeStatus.OPEN;
        this.resolvedAt = null;
        this.cardId = null;
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

    public Long getQuizQuestionId() {
        return quizQuestionId;
    }

    public MistakeSource getSource() {
        return source;
    }

    public String getQuestion() {
        return question;
    }

    public String getGivenAnswer() {
        return givenAnswer;
    }

    public String getCorrectAnswer() {
        return correctAnswer;
    }

    public String getExplanation() {
        return explanation;
    }

    public String getTopic() {
        return topic;
    }

    public int getOccurrences() {
        return occurrences;
    }

    public MistakeStatus getStatus() {
        return status;
    }

    public Long getCardId() {
        return cardId;
    }

    public Instant getFirstMissedAt() {
        return firstMissedAt;
    }

    public Instant getLastMissedAt() {
        return lastMissedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }
}
