package com.ragstudy.knowledge.controller.dto;

public record KnowledgeDocumentDto(
        String id,
        String knowledgeBaseId,
        String title,
        String sourceType,
        String rawContent,
        String parseStatus,
        String vectorStatus,
        long chunkCount,
        String updatedAt
) {
}
