package com.ragstudy.knowledge.controller.dto;

public record KnowledgeIndexResultDto(
        String knowledgeBaseId,
        int indexedChunks,
        String embeddingModel
) {
}
