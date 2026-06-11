package com.ragstudy.knowledge.dal.dataobject;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "knowledge_bases")
public class KnowledgeBaseEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private int documentCount;

    @Column(nullable = false)
    private int chunkCount;

    @Column(nullable = false)
    private String vectorStatus;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected KnowledgeBaseEntity() {
    }

    public KnowledgeBaseEntity(
            String id,
            String userId,
            String name,
            String description,
            int documentCount,
            int chunkCount,
            String vectorStatus,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.id = id;
        this.userId = userId;
        this.name = name;
        this.description = description;
        this.documentCount = documentCount;
        this.chunkCount = chunkCount;
        this.vectorStatus = vectorStatus;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getDocumentCount() {
        return documentCount;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public String getVectorStatus() {
        return vectorStatus;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void update(String name, String description) {
        this.name = name;
        this.description = description;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateStats(int documentCount, int chunkCount, String vectorStatus) {
        this.documentCount = documentCount;
        this.chunkCount = chunkCount;
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
