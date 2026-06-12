package com.ragstudy.knowledge.dal.dataobject;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "knowledge_documents")
public class KnowledgeDocumentEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String knowledgeBaseId;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String sourceType;

    @Column
    private String sourceId;

    @Column
    private String fileName;

    @Column
    private String mimeType;

    @Column
    private String storagePath;

    @Column(nullable = false, columnDefinition = "text")
    private String rawContent;

    @Column(nullable = false)
    private String parseStatus;

    @Column(nullable = false)
    private String vectorStatus;

    @Column(length = 64)
    private String contentHash;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected KnowledgeDocumentEntity() {
    }

    public KnowledgeDocumentEntity(
            String id,
            String knowledgeBaseId,
            String userId,
            String title,
            String sourceType,
            String sourceId,
            String fileName,
            String mimeType,
            String storagePath,
            String rawContent,
            String parseStatus,
            String vectorStatus,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.id = id;
        this.knowledgeBaseId = knowledgeBaseId;
        this.userId = userId;
        this.title = title;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.fileName = fileName;
        this.mimeType = mimeType;
        this.storagePath = storagePath;
        this.rawContent = rawContent;
        this.parseStatus = parseStatus;
        this.vectorStatus = vectorStatus;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public String getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public String getUserId() {
        return userId;
    }

    public String getTitle() {
        return title;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getSourceId() {
        return sourceId;
    }

    public String getFileName() {
        return fileName;
    }

    public String getMimeType() {
        return mimeType;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public String getRawContent() {
        return rawContent;
    }

    public String getParseStatus() {
        return parseStatus;
    }

    public String getVectorStatus() {
        return vectorStatus;
    }

    public String getContentHash() {
        return contentHash;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void update(String title, String rawContent) {
        this.title = title;
        this.rawContent = rawContent;
        this.parseStatus = "parsed";
        this.vectorStatus = "pending";
        this.updatedAt = LocalDateTime.now();
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public void setVectorStatus(String vectorStatus) {
        this.vectorStatus = vectorStatus;
        this.updatedAt = LocalDateTime.now();
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
