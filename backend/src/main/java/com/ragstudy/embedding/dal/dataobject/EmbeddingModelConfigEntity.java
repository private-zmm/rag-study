package com.ragstudy.embedding.dal.dataobject;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "embedding_model_configs")
public class EmbeddingModelConfigEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String providerType;

    @Column(nullable = false)
    private String baseUrl;

    @Column(nullable = false, columnDefinition = "text")
    private String apiKey;

    @Column(nullable = false)
    private String model;

    @Column(nullable = false)
    private int dimensions;

    @Column(nullable = false)
    private boolean defaultModel;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected EmbeddingModelConfigEntity() {
    }

    public EmbeddingModelConfigEntity(
            String id,
            String userId,
            String name,
            String providerType,
            String baseUrl,
            String apiKey,
            String model,
            int dimensions,
            boolean defaultModel,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.id = id;
        this.userId = userId;
        this.name = name;
        this.providerType = providerType;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.dimensions = dimensions;
        this.defaultModel = defaultModel;
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

    public String getProviderType() {
        return providerType;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getModel() {
        return model;
    }

    public int getDimensions() {
        return dimensions;
    }

    public boolean isDefaultModel() {
        return defaultModel;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void update(String name, String providerType, String baseUrl, String apiKey, String model, int dimensions, boolean defaultModel) {
        this.name = name;
        this.providerType = providerType;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.dimensions = dimensions;
        this.defaultModel = defaultModel;
        this.updatedAt = LocalDateTime.now();
    }

    public void setDefaultModel(boolean defaultModel) {
        this.defaultModel = defaultModel;
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
