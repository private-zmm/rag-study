package com.ragstudy.note.controller.dto;

public record NoteKnowledgeSyncResponse(
        String taskId,
        String knowledgeBaseId,
        String status,
        int totalNotes,
        int syncedNotes,
        int skippedNotes,
        int indexedChunks,
        String embeddingModel
) {
}
