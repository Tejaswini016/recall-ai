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
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "quizzes")
public class Quiz {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deck_id", nullable = false)
    private Deck deck;

    @Column(nullable = false, length = 200)
    private String title;

    @OneToMany(mappedBy = "quiz", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<QuizQuestion> questions = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Quiz() {
        // JPA
    }

    public Quiz(Deck deck, String title) {
        this.deck = deck;
        this.title = title;
    }

    public void addQuestion(String question, List<String> options, int correctAnswer, String explanation) {
        addQuestion(question, options, correctAnswer, explanation, null);
    }

    public void addQuestion(String question, List<String> options, int correctAnswer, String explanation,
                            String topic) {
        questions.add(new QuizQuestion(this, question, options, correctAnswer, explanation, topic));
    }

    public Long getId() {
        return id;
    }

    public Deck getDeck() {
        return deck;
    }

    public String getTitle() {
        return title;
    }

    public List<QuizQuestion> getQuestions() {
        return List.copyOf(questions);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
