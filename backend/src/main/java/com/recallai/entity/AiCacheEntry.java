package com.recallai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One validated model response, addressed by content rather than by user. Rows are only
 * ever written after validation succeeds, so reading the cache never needs re-validation.
 */
@Entity
@Table(name = "ai_cache")
public class AiCacheEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "operation_type", nullable = false, length = 50)
    private String operationType;

    @Column(nullable = false, length = 100)
    private String model;

    @Column(name = "prompt_version", nullable = false, length = 20)
    private String promptVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_json", nullable = false, columnDefinition = "jsonb")
    private String responseJson;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AiCacheEntry() {
        // JPA
    }

    public AiCacheEntry(String contentHash, String operationType, String model, String promptVersion,
                        String responseJson) {
        this.contentHash = contentHash;
        this.operationType = operationType;
        this.model = model;
        this.promptVersion = promptVersion;
        this.responseJson = responseJson;
    }

    public Long getId() {
        return id;
    }

    public String getContentHash() {
        return contentHash;
    }

    public String getOperationType() {
        return operationType;
    }

    public String getModel() {
        return model;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public String getResponseJson() {
        return responseJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
