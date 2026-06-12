package com.ragstudy.note.convert;

import com.ragstudy.note.controller.dto.NoteKnowledgeSyncTaskDto;
import com.ragstudy.note.dal.dataobject.NoteSyncTaskEntity;

public final class NoteKnowledgeSyncTaskConvert {

    private NoteKnowledgeSyncTaskConvert() {
    }

    public static NoteKnowledgeSyncTaskDto toDto(NoteSyncTaskEntity task) {
        return new NoteKnowledgeSyncTaskDto(
                task.getId(),
                task.getKnowledgeBaseId(),
                task.getStatus(),
                task.getTotalNotes(),
                task.getProcessedNotes(),
                task.getSyncedNotes(),
                task.getSkippedNotes(),
                task.getIndexedChunks(),
                task.getEmbeddingModel(),
                task.getErrorMessage(),
                task.getCreatedAt(),
                task.getUpdatedAt(),
                task.getStartedAt(),
                task.getFinishedAt()
        );
    }
}
