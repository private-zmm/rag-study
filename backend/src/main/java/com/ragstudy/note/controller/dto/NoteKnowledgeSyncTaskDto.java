package com.ragstudy.note.controller.dto;

import java.time.LocalDateTime;

public record NoteKnowledgeSyncTaskDto(
        String taskId,
        String knowledgeBaseId,
        String status,
        int totalNotes,
        int processedNotes,
        int syncedNotes,
        int skippedNotes,
        int indexedChunks,
        String embeddingModel,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime startedAt,
        LocalDateTime finishedAt
) {
}
