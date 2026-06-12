package com.ragstudy.note.dal.dataobject;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "note_sync_tasks")
public class NoteSyncTaskEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String knowledgeBaseId;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(nullable = false)
    private int totalNotes;

    @Column(nullable = false)
    private int processedNotes;

    @Column(nullable = false)
    private int syncedNotes;

    @Column(nullable = false)
    private int skippedNotes;

    @Column(nullable = false)
    private int indexedChunks;

    @Column
    private String embeddingModel;

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

    protected NoteSyncTaskEntity() {
    }

    public NoteSyncTaskEntity(String id, String userId, String knowledgeBaseId, int totalNotes) {
        this.id = id;
        this.userId = userId;
        this.knowledgeBaseId = knowledgeBaseId;
        this.status = "pending";
        this.totalNotes = totalNotes;
        this.processedNotes = 0;
        this.syncedNotes = 0;
        this.skippedNotes = 0;
        this.indexedChunks = 0;
        this.embeddingModel = "none";
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

    public String getStatus() {
        return status;
    }

    public int getTotalNotes() {
        return totalNotes;
    }

    public int getProcessedNotes() {
        return processedNotes;
    }

    public int getSyncedNotes() {
        return syncedNotes;
    }

    public int getSkippedNotes() {
        return skippedNotes;
    }

    public int getIndexedChunks() {
        return indexedChunks;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
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

    public void markRunning() {
        this.status = "running";
        this.startedAt = LocalDateTime.now();
    }

    public void recordSynced(int indexedChunks, String embeddingModel) {
        this.processedNotes += 1;
        this.syncedNotes += 1;
        this.indexedChunks += indexedChunks;

        if (embeddingModel != null && !embeddingModel.isBlank() && !"none".equals(embeddingModel)) {
            this.embeddingModel = embeddingModel;
        }
    }

    public void recordSkipped() {
        this.processedNotes += 1;
        this.skippedNotes += 1;
    }

    public void markCompleted() {
        this.status = "completed";
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
