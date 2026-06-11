package com.ragstudy.clipper.controller.dto;

public record WebClipDto(
        String id,
        String knowledgeBaseId,
        String documentId,
        String url,
        String title,
        String siteName,
        String excerpt,
        String content,
        String status,
        String createdAt,
        String updatedAt
) {
}
