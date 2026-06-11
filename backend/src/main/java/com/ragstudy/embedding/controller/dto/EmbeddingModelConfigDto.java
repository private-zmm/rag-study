package com.ragstudy.embedding.controller.dto;

public record EmbeddingModelConfigDto(
        String id,
        String name,
        String providerType,
        String baseUrl,
        String model,
        int dimensions,
        boolean defaultModel
) {
}
