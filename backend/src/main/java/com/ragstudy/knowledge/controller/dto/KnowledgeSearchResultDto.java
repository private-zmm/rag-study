package com.ragstudy.knowledge.controller.dto;

public record KnowledgeSearchResultDto(
        String chunkId,
        String documentId,
        String titlePath,
        String heading,
        String content,
        double score
) {
}
