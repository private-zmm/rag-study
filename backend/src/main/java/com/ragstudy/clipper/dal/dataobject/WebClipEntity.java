package com.ragstudy.clipper.dal.dataobject;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "web_clips")
public class WebClipEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String userId;

    @Column
    private String knowledgeBaseId;

    @Column
    private String documentId;

    @Column(nullable = false, columnDefinition = "text")
    private String url;

    @Column(nullable = false, columnDefinition = "text")
    private String canonicalUrl;

    @Column(nullable = false)
    private String title;

    @Column
    private String siteName;

    @Column(nullable = false, columnDefinition = "text")
    private String excerpt;

    @Column(columnDefinition = "text")
    private String content;

    @Column
    private String contentHash;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected WebClipEntity() {
    }

    public WebClipEntity(
            String id,
            String userId,
            String knowledgeBaseId,
            String documentId,
            String url,
            String canonicalUrl,
            String title,
            String siteName,
            String excerpt,
            String content,
            String contentHash,
            String status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.id = id;
        this.userId = userId;
        this.knowledgeBaseId = knowledgeBaseId;
        this.documentId = documentId;
        this.url = url;
        this.canonicalUrl = canonicalUrl;
        this.title = title;
        this.siteName = siteName;
        this.excerpt = excerpt;
        this.content = content;
        this.contentHash = contentHash;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
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

    public String getDocumentId() {
        return documentId;
    }

    public String getUrl() {
        return url;
    }

    public String getCanonicalUrl() {
        return canonicalUrl;
    }

    public String getTitle() {
        return title;
    }

    public String getSiteName() {
        return siteName;
    }

    public String getExcerpt() {
        return excerpt;
    }

    public String getContent() {
        return content;
    }

    public String getContentHash() {
        return contentHash;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
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
