package com.ragstudy.clipper.dal.dataobject;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "clipper_video_tasks")
public class ClipperVideoTaskEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String knowledgeBaseId;

    @Column(nullable = false, columnDefinition = "text")
    private String url;

    @Column(nullable = false)
    private String platform;

    @Column(nullable = false)
    private String status;

    @Column
    private String title;

    @Column
    private String documentId;

    @Column(columnDefinition = "text")
    private String errorMessage;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column
    private LocalDateTime startedAt;

    @Column
    private LocalDateTime finishedAt;

    protected ClipperVideoTaskEntity() {
    }

    public ClipperVideoTaskEntity(String id, String userId, String knowledgeBaseId, String url, String platform) {
        this.id = id;
        this.userId = userId;
        this.knowledgeBaseId = knowledgeBaseId;
        this.url = url;
        this.platform = platform;
        this.status = "pending";
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public String getUrl() {
        return url;
    }

    public String getPlatform() {
        return platform;
    }

    public String getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public String getDocumentId() {
        return documentId;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void markProcessing() {
        this.status = "processing";
        this.errorMessage = null;
        this.startedAt = LocalDateTime.now();
    }

    public void markCompleted(String title, String documentId) {
        this.status = "completed";
        this.title = title;
        this.documentId = documentId;
        this.errorMessage = null;
        this.finishedAt = LocalDateTime.now();
    }

    public void markFailed(String errorMessage) {
        this.status = "failed";
        this.errorMessage = errorMessage;
        this.finishedAt = LocalDateTime.now();
    }

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();

        if (createdAt == null) {
            createdAt = now;
        }

        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
