package com.ragstudy.knowledge.controller.dto;

public record KnowledgeSearchResultDto(
        String chunkId,
        String documentId,
        String content,
        double score
) {
}
