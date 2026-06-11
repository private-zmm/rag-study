package com.ragstudy.search.controller.dto;

public record GlobalSearchResultDto(
        String id,
        String type,
        String title,
        String description,
        String targetId,
        String knowledgeBaseId,
        String updatedAt
) {
}
