package com.ragstudy.note.service;

import com.ragstudy.knowledge.controller.dto.KnowledgeIndexResultDto;
import com.ragstudy.knowledge.dal.dataobject.KnowledgeDocumentEntity;
import com.ragstudy.knowledge.service.KnowledgeDocumentService;
import com.ragstudy.knowledge.service.KnowledgeVectorService;
import com.ragstudy.note.controller.dto.NoteKnowledgeSyncRequest;
import com.ragstudy.note.controller.dto.NoteKnowledgeSyncResponse;
import com.ragstudy.note.dal.dataobject.NoteEntity;
import com.ragstudy.note.dal.repository.NoteRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class NoteKnowledgeSyncService {

    private final NoteRepository noteRepository;
    private final KnowledgeDocumentService knowledgeDocumentService;
    private final KnowledgeVectorService knowledgeVectorService;

    public NoteKnowledgeSyncService(
            NoteRepository noteRepository,
            KnowledgeDocumentService knowledgeDocumentService,
            KnowledgeVectorService knowledgeVectorService
    ) {
        this.noteRepository = noteRepository;
        this.knowledgeDocumentService = knowledgeDocumentService;
        this.knowledgeVectorService = knowledgeVectorService;
    }

    @Transactional
    public NoteKnowledgeSyncResponse syncNotes(String userId, NoteKnowledgeSyncRequest request) {
        List<KnowledgeDocumentEntity> syncedDocuments = new ArrayList<>();

        for (String noteId : request.noteIds()) {
            NoteEntity note = noteRepository.findById(noteId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "笔记不存在"));

            if (!userId.equals(note.getUserId())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "笔记不存在");
            }

            syncedDocuments.add(knowledgeDocumentService.syncNoteDocument(
                    userId,
                    request.knowledgeBaseId(),
                    note.getId(),
                    displayName(note.getTitle()),
                    buildKnowledgeDocumentContent(note)
            ));
        }

        KnowledgeIndexResultDto indexResult;

        if (syncedDocuments.size() == 1) {
            KnowledgeDocumentEntity document = syncedDocuments.get(0);
            indexResult = knowledgeVectorService.rebuildDocumentIndex(userId, request.knowledgeBaseId(), document.getId());
        } else {
            indexResult = knowledgeVectorService.rebuildIndex(userId, request.knowledgeBaseId());
        }

        return new NoteKnowledgeSyncResponse(
                request.knowledgeBaseId(),
                syncedDocuments.size(),
                indexResult.indexedChunks(),
                indexResult.embeddingModel()
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
}
