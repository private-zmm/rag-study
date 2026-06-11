package com.ragstudy.note.controller.dto;

public record NoteKnowledgeSyncResponse(
        String knowledgeBaseId,
        int syncedNotes,
        int indexedChunks,
        String embeddingModel
) {
}
