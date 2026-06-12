package com.ragstudy.knowledge.service;

import com.ragstudy.knowledge.controller.dto.KnowledgeBaseDto;
import com.ragstudy.knowledge.controller.dto.KnowledgeBaseRequest;
import com.ragstudy.knowledge.convert.KnowledgeBaseConvert;
import com.ragstudy.knowledge.dal.dataobject.KnowledgeBaseEntity;
import com.ragstudy.knowledge.dal.repository.KnowledgeBaseRepository;
import com.ragstudy.knowledge.dal.repository.KnowledgeDocumentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class KnowledgeBaseService {

    private final KnowledgeBaseRepository repository;
    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeChunkService chunkService;

    public KnowledgeBaseService(
            KnowledgeBaseRepository repository,
            KnowledgeDocumentRepository documentRepository,
            KnowledgeChunkService chunkService
    ) {
        this.repository = repository;
        this.documentRepository = documentRepository;
        this.chunkService = chunkService;
    }

    @Transactional(readOnly = true)
    public List<KnowledgeBaseDto> listKnowledgeBases(String userId) {
        return repository.findAllByUserIdOrderByUpdatedAtDesc(userId)
                .stream()
                .map(KnowledgeBaseConvert::toDto)
                .toList();
    }

    @Transactional
    public KnowledgeBaseDto createKnowledgeBase(String userId, KnowledgeBaseRequest request) {
        LocalDateTime now = LocalDateTime.now();
        KnowledgeBaseEntity knowledgeBase = new KnowledgeBaseEntity(
                UUID.randomUUID().toString(),
                userId,
                request.name().trim(),
                normalizeDescription(request.description()),
                0,
                0,
                "empty",
                now,
                now
        );

        return KnowledgeBaseConvert.toDto(repository.save(knowledgeBase));
    }

    @Transactional
    public KnowledgeBaseDto updateKnowledgeBase(String userId, String id, KnowledgeBaseRequest request) {
        KnowledgeBaseEntity knowledgeBase = requireOwnedKnowledgeBase(userId, id);
        knowledgeBase.update(request.name().trim(), normalizeDescription(request.description()));
        return KnowledgeBaseConvert.toDto(repository.save(knowledgeBase));
    }

    @Transactional
    public void deleteKnowledgeBase(String userId, String id) {
        KnowledgeBaseEntity knowledgeBase = requireOwnedKnowledgeBase(userId, id);
        chunkService.deleteKnowledgeBaseChunks(knowledgeBase.getId(), userId);
        documentRepository.deleteAllByKnowledgeBaseIdAndUserId(knowledgeBase.getId(), userId);
        repository.delete(knowledgeBase);
    }

    private KnowledgeBaseEntity requireOwnedKnowledgeBase(String userId, String id) {
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "知识库不存在"));
    }

    private String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return "";
        }

        return description.trim();
    }
}
