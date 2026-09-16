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
import java.time.LocalDate;

@Entity
@Table(name = "study_plan_tasks")
public class StudyPlanTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private StudyPlan plan;

    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 20)
    private StudyTaskType type;

    @Column(length = 150)
    private String topic;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private int minutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StudyTaskStatus status = StudyTaskStatus.PENDING;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected StudyPlanTask() {
        // JPA
    }

    StudyPlanTask(StudyPlan plan, LocalDate scheduledDate, int sortOrder, StudyTaskType type, String topic,
                  String title, String description, int minutes) {
        this.plan = plan;
        this.scheduledDate = scheduledDate;
        this.sortOrder = sortOrder;
        this.type = type;
        this.topic = topic;
        this.title = title;
        this.description = description;
        this.minutes = minutes;
    }

    public void setStatus(StudyTaskStatus status, Instant now) {
        this.status = status;
        this.completedAt = status == StudyTaskStatus.PENDING ? null : now;
    }

    public Long getId() {
        return id;
    }

    public StudyPlan getPlan() {
        return plan;
    }

    public LocalDate getScheduledDate() {
        return scheduledDate;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public StudyTaskType getType() {
        return type;
    }

    public String getTopic() {
        return topic;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public int getMinutes() {
        return minutes;
    }

    public StudyTaskStatus getStatus() {
        return status;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
