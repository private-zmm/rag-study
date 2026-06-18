package com.ragstudy.clipper.controller.dto;

public record ClipperVideoTaskDto(
        String id,
        String knowledgeBaseId,
        String url,
        String platform,
        String status,
        String title,
        String documentId,
        String errorMessage,
        String createdAt,
        String updatedAt,
        String startedAt,
        String finishedAt
) {
}
