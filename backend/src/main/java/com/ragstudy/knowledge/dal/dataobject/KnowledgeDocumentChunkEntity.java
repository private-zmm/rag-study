package com.ragstudy.knowledge.dal.dataobject;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "knowledge_document_chunks")
public class KnowledgeDocumentChunkEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String documentId;

    @Column(nullable = false)
    private String knowledgeBaseId;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private int chunkIndex;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(nullable = false)
    private int tokenCount;

    @Column
    private String vectorId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected KnowledgeDocumentChunkEntity() {
    }

    public KnowledgeDocumentChunkEntity(
            String id,
            String documentId,
            String knowledgeBaseId,
            String userId,
            int chunkIndex,
            String content,
            int tokenCount,
            String vectorId,
            LocalDateTime createdAt
    ) {
        this.id = id;
        this.documentId = documentId;
        this.knowledgeBaseId = knowledgeBaseId;
        this.userId = userId;
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.tokenCount = tokenCount;
        this.vectorId = vectorId;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getDocumentId() {
        return documentId;
    }

    public String getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public String getUserId() {
        return userId;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public String getContent() {
        return content;
    }

    public int getTokenCount() {
        return tokenCount;
    }

    public String getVectorId() {
        return vectorId;
    }

    public void setVectorId(String vectorId) {
        this.vectorId = vectorId;
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
