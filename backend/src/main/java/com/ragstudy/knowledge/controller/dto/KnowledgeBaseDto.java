package com.ragstudy.knowledge.controller.dto;

public record KnowledgeBaseDto(
        String id,
        String name,
        String description,
        int documentCount,
        int chunkCount,
        String vectorStatus
) {
}
