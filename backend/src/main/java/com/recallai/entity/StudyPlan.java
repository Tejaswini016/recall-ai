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
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/** An exam and the plan of dated tasks leading up to it. */
@Entity
@Table(name = "study_plans")
public class StudyPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "exam_name", nullable = false, length = 150)
    private String examName;

    @Column(name = "exam_date", nullable = false)
    private LocalDate examDate;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private List<String> topics = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "knowledge_level", nullable = false, length = 20)
    private KnowledgeLevel knowledgeLevel;

    @Column(name = "minutes_per_day", nullable = false)
    private int minutesPerDay;

    /** ISO day numbers, Monday = 1. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "preferred_days", nullable = false, columnDefinition = "integer[]")
    private List<Integer> preferredDays = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StudyPlanStatus status = StudyPlanStatus.ACTIVE;

    @Column(columnDefinition = "text")
    private String summary;

    /** JSON array of {topic, advice}; validated model output or empty. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "topic_advice", nullable = false, columnDefinition = "jsonb")
    private String topicAdvice = "[]";

    @Column(name = "ai_generated", nullable = false)
    private boolean aiGenerated;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("scheduledDate ASC, sortOrder ASC, id ASC")
    private List<StudyPlanTask> tasks = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StudyPlan() {
        // JPA
    }

    public StudyPlan(User user, String examName, LocalDate examDate, List<String> topics, KnowledgeLevel knowledgeLevel,
                     int minutesPerDay, List<Integer> preferredDays, Instant now) {
        this.user = user;
        this.examName = examName;
        this.examDate = examDate;
        this.topics = new ArrayList<>(topics);
        this.knowledgeLevel = knowledgeLevel;
        this.minutesPerDay = minutesPerDay;
        this.preferredDays = new ArrayList<>(preferredDays);
        this.generatedAt = now;
    }

    public StudyPlanTask addTask(LocalDate date, int sortOrder, StudyTaskType type, String topic, String title,
                                 String description, int minutes) {
        StudyPlanTask task = new StudyPlanTask(this, date, sortOrder, type, topic, title, description, minutes);
        tasks.add(task);
        return task;
    }

    public void removeTask(StudyPlanTask task) {
        tasks.remove(task);
    }

    public void setAdvice(String summary, String topicAdviceJson, boolean aiGenerated, Instant now) {
        this.summary = summary;
        this.topicAdvice = topicAdviceJson;
        this.aiGenerated = aiGenerated;
        this.generatedAt = now;
    }

    public void setStatus(StudyPlanStatus status) {
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getExamName() {
        return examName;
    }

    public LocalDate getExamDate() {
        return examDate;
    }

    public List<String> getTopics() {
        return List.copyOf(topics);
    }

    public KnowledgeLevel getKnowledgeLevel() {
        return knowledgeLevel;
    }

    public int getMinutesPerDay() {
        return minutesPerDay;
    }

    public List<Integer> getPreferredDays() {
        return List.copyOf(preferredDays);
    }

    public StudyPlanStatus getStatus() {
        return status;
    }

    public String getSummary() {
        return summary;
    }

    public String getTopicAdvice() {
        return topicAdvice;
    }

    public boolean isAiGenerated() {
        return aiGenerated;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    /** Live view, ordered by date; callers must not mutate it. */
    public List<StudyPlanTask> getTasks() {
        return List.copyOf(tasks);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
