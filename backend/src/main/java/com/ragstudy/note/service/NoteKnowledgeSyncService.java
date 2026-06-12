package com.ragstudy.note.service;

import com.ragstudy.knowledge.controller.dto.KnowledgeIndexResultDto;
import com.ragstudy.knowledge.dal.dataobject.KnowledgeDocumentEntity;
import com.ragstudy.knowledge.service.KnowledgeDocumentService;
import com.ragstudy.knowledge.service.KnowledgeVectorService;
import com.ragstudy.note.controller.dto.NoteKnowledgeSyncRequest;
import com.ragstudy.note.controller.dto.NoteKnowledgeSyncResponse;
import com.ragstudy.note.controller.dto.NoteKnowledgeSyncTaskDto;
import com.ragstudy.note.convert.NoteKnowledgeSyncTaskConvert;
import com.ragstudy.note.dal.dataobject.NoteEntity;
import com.ragstudy.note.dal.dataobject.NoteSyncTaskEntity;
import com.ragstudy.note.dal.repository.NoteRepository;
import com.ragstudy.note.dal.repository.NoteSyncTaskRepository;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class NoteKnowledgeSyncService {

    private final NoteRepository noteRepository;
    private final NoteSyncTaskRepository taskRepository;
    private final KnowledgeDocumentService knowledgeDocumentService;
    private final KnowledgeVectorService knowledgeVectorService;
    private final TaskExecutor applicationTaskExecutor;

    public NoteKnowledgeSyncService(
            NoteRepository noteRepository,
            NoteSyncTaskRepository taskRepository,
            KnowledgeDocumentService knowledgeDocumentService,
            KnowledgeVectorService knowledgeVectorService,
            TaskExecutor applicationTaskExecutor
    ) {
        this.noteRepository = noteRepository;
        this.taskRepository = taskRepository;
        this.knowledgeDocumentService = knowledgeDocumentService;
        this.knowledgeVectorService = knowledgeVectorService;
        this.applicationTaskExecutor = applicationTaskExecutor;
    }

    @Transactional
    public NoteKnowledgeSyncResponse syncNotes(String userId, NoteKnowledgeSyncRequest request) {
        NoteSyncTaskEntity task = taskRepository.save(new NoteSyncTaskEntity(
                UUID.randomUUID().toString(),
                userId,
                request.knowledgeBaseId(),
                request.noteIds().size()
        ));

        List<String> noteIds = List.copyOf(request.noteIds());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                applicationTaskExecutor.execute(() -> runSyncTask(task.getId(), userId, request.knowledgeBaseId(), noteIds));
            }
        });

        return toResponse(task);
    }

    @Transactional(readOnly = true)
    public NoteKnowledgeSyncTaskDto getTask(String userId, String taskId) {
        return taskRepository.findByIdAndUserId(taskId, userId)
                .map(NoteKnowledgeSyncTaskConvert::toDto)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "同步任务不存在"));
    }

    private void runSyncTask(String taskId, String userId, String knowledgeBaseId, List<String> noteIds) {
        try {
            markTaskRunning(userId, taskId);

            for (String noteId : noteIds) {
                syncOneNote(userId, taskId, knowledgeBaseId, noteId);
            }

            markTaskCompleted(userId, taskId);
        } catch (Exception exception) {
            markTaskFailed(userId, taskId, rootCauseMessage(exception));
        }
    }

    private void markTaskRunning(String userId, String taskId) {
        NoteSyncTaskEntity task = requireOwnedTask(userId, taskId);
        task.markRunning();
        taskRepository.save(task);
    }

    private void syncOneNote(String userId, String taskId, String knowledgeBaseId, String noteId) {
        NoteSyncTaskEntity task = requireOwnedTask(userId, taskId);
        NoteEntity note = noteRepository.findByIdAndUserId(noteId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "笔记不存在"));
        String rawContent = buildKnowledgeDocumentContent(note);
        String contentHash = sha256(rawContent);

        if (knowledgeDocumentService.isNoteDocumentContentUnchanged(userId, knowledgeBaseId, note.getId(), contentHash)) {
            task.recordSkipped();
            taskRepository.save(task);
            return;
        }

        KnowledgeDocumentEntity document = knowledgeDocumentService.syncNoteDocumentIfChanged(
                userId,
                knowledgeBaseId,
                note.getId(),
                displayName(note.getTitle()),
                rawContent,
                contentHash
        );
        KnowledgeIndexResultDto indexResult = knowledgeVectorService.rebuildDocumentIndex(userId, knowledgeBaseId, document.getId());

        task.recordSynced(indexResult.indexedChunks(), indexResult.embeddingModel());
        taskRepository.save(task);
    }

    private void markTaskCompleted(String userId, String taskId) {
        NoteSyncTaskEntity task = requireOwnedTask(userId, taskId);
        task.markCompleted();
        taskRepository.save(task);
    }

    private void markTaskFailed(String userId, String taskId, String errorMessage) {
        NoteSyncTaskEntity task = requireOwnedTask(userId, taskId);
        task.markFailed(errorMessage);
        taskRepository.save(task);
    }

    private NoteSyncTaskEntity requireOwnedTask(String userId, String taskId) {
        return taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "同步任务不存在"));
    }

    private NoteKnowledgeSyncResponse toResponse(NoteSyncTaskEntity task) {
        return new NoteKnowledgeSyncResponse(
                task.getId(),
                task.getKnowledgeBaseId(),
                task.getStatus(),
                task.getTotalNotes(),
                task.getSyncedNotes(),
                task.getSkippedNotes(),
                task.getIndexedChunks(),
                task.getEmbeddingModel()
        );
    }

    private String buildKnowledgeDocumentContent(NoteEntity note) {
        String content = sanitizeText(note.getContent()).trim();

        if (content.startsWith("#")) {
            return content;
        }

        return "# " + displayName(note.getTitle()) + "\n\n" + content;
    }

    private String displayName(String title) {
        List<String> parts = Arrays.stream(sanitizeText(title).replace("\\", "/").split("/"))
                .map(String::trim)
                .filter(part -> !part.isBlank())
                .toList();

        if (parts.isEmpty()) {
            return sanitizeText(title).trim();
        }

        return parts.get(parts.size() - 1);
    }

    private String sanitizeText(String value) {
        return value.replace("\u0000", "");
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable rootCause = throwable;

        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }

        if (rootCause.getMessage() != null && !rootCause.getMessage().isBlank()) {
            return rootCause.getMessage();
        }

        return throwable.getClass().getSimpleName();
    }
}
