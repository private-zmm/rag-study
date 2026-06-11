package com.ragstudy.chat.dal.dataobject;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_conversations")
public class ChatConversationEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String title;

    @Column
    private String modelConfigId;

    @Column
    private String knowledgeBaseId;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean archived = false;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected ChatConversationEntity() {
    }

    public ChatConversationEntity(
            String id,
            String userId,
            String title,
            String modelConfigId,
            String knowledgeBaseId,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.id = id;
        this.userId = userId;
        this.title = title;
        this.modelConfigId = modelConfigId;
        this.knowledgeBaseId = knowledgeBaseId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getTitle() {
        return title;
    }

    public String getModelConfigId() {
        return modelConfigId;
    }

    public String getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public boolean isArchived() {
        return archived;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    public void rename(String title) {
        this.title = title;
        this.updatedAt = LocalDateTime.now();
    }

    public void archive() {
        this.archived = true;
        this.updatedAt = LocalDateTime.now();
    }

    public void restore() {
        this.archived = false;
        this.updatedAt = LocalDateTime.now();
    }

    public void bindKnowledgeBase(String knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
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
