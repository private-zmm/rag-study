package com.ragstudy.knowledge.service;

import com.ragstudy.knowledge.controller.dto.KnowledgeIndexResultDto;
import com.ragstudy.knowledge.controller.dto.KnowledgeSearchResultDto;
import com.ragstudy.knowledge.dal.dataobject.KnowledgeBaseEntity;
import com.ragstudy.knowledge.dal.dataobject.KnowledgeDocumentChunkEntity;
import com.ragstudy.knowledge.dal.dataobject.KnowledgeDocumentEntity;
import com.ragstudy.knowledge.dal.repository.KnowledgeBaseRepository;
import com.ragstudy.knowledge.dal.repository.KnowledgeDocumentChunkRepository;
import com.ragstudy.knowledge.dal.repository.KnowledgeDocumentRepository;
import com.ragstudy.knowledge.framework.QdrantVectorService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class KnowledgeVectorService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeDocumentChunkRepository chunkRepository;
    private final QdrantVectorService qdrantVectorService;

    public KnowledgeVectorService(
            KnowledgeBaseRepository knowledgeBaseRepository,
            KnowledgeDocumentRepository documentRepository,
            KnowledgeDocumentChunkRepository chunkRepository,
            QdrantVectorService qdrantVectorService
    ) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.qdrantVectorService = qdrantVectorService;
    }

    @Transactional
    public KnowledgeIndexResultDto rebuildIndex(String userId, String knowledgeBaseId) {
        KnowledgeBaseEntity knowledgeBase = requireOwnedKnowledgeBase(userId, knowledgeBaseId);
        List<KnowledgeDocumentChunkEntity> chunks =
                chunkRepository.findAllByKnowledgeBaseIdAndUserIdOrderByDocumentIdAscChunkIndexAsc(knowledgeBaseId, userId);
        String embeddingModel = rebuildChunks(chunks);

        long documentCount = documentRepository.countByKnowledgeBaseIdAndUserId(knowledgeBaseId, userId);
        String vectorStatus = chunks.isEmpty() ? "empty" : "ready";
        syncDocumentVectorStatus(userId, knowledgeBaseId, vectorStatus);
        knowledgeBase.updateStats(Math.toIntExact(documentCount), chunks.size(), vectorStatus);
        knowledgeBaseRepository.save(knowledgeBase);

        return new KnowledgeIndexResultDto(knowledgeBaseId, chunks.size(), embeddingModel);
    }

    @Transactional
    public KnowledgeIndexResultDto rebuildDocumentIndex(String userId, String knowledgeBaseId, String documentId) {
        KnowledgeBaseEntity knowledgeBase = requireOwnedKnowledgeBase(userId, knowledgeBaseId);
        KnowledgeDocumentEntity document = requireOwnedDocument(userId, knowledgeBaseId, documentId);
        List<KnowledgeDocumentChunkEntity> chunks = chunkRepository.findAllByDocumentIdAndUserIdOrderByChunkIndexAsc(documentId, userId);
        String embeddingModel = rebuildChunks(chunks);

        document.setVectorStatus(chunks.isEmpty() ? "pending" : "ready");
        documentRepository.save(document);

        long documentCount = documentRepository.countByKnowledgeBaseIdAndUserId(knowledgeBaseId, userId);
        long chunkCount = chunkRepository.countByKnowledgeBaseIdAndUserId(knowledgeBaseId, userId);
        String knowledgeBaseVectorStatus = chunkCount == 0 ? "empty" : "ready";
        knowledgeBase.updateStats(Math.toIntExact(documentCount), Math.toIntExact(chunkCount), knowledgeBaseVectorStatus);
        knowledgeBaseRepository.save(knowledgeBase);

        return new KnowledgeIndexResultDto(knowledgeBaseId, chunks.size(), embeddingModel);
    }

    @Transactional(readOnly = true)
    public List<KnowledgeSearchResultDto> search(String userId, String knowledgeBaseId, String query, int limit) {
        requireOwnedKnowledgeBase(userId, knowledgeBaseId);
        int normalizedLimit = Math.max(1, Math.min(limit, 20));

        return qdrantVectorService.search(userId, knowledgeBaseId, query, normalizedLimit)
                .stream()
                .map(result -> new KnowledgeSearchResultDto(
                        result.chunkId(),
                        result.documentId(),
                        result.content(),
                        result.score()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<KnowledgeSearchResultDto> listContextChunks(String userId, String knowledgeBaseId, int limit) {
        requireOwnedKnowledgeBase(userId, knowledgeBaseId);
        int normalizedLimit = Math.max(1, Math.min(limit, 20));

        return chunkRepository.findAllByKnowledgeBaseIdAndUserIdOrderByDocumentIdAscChunkIndexAsc(knowledgeBaseId, userId)
                .stream()
                .limit(normalizedLimit)
                .map(chunk -> new KnowledgeSearchResultDto(
                        chunk.getId(),
                        chunk.getDocumentId(),
                        chunk.getContent(),
                        0
                ))
                .toList();
    }

    private KnowledgeBaseEntity requireOwnedKnowledgeBase(String userId, String knowledgeBaseId) {
        KnowledgeBaseEntity knowledgeBase = knowledgeBaseRepository.findById(knowledgeBaseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "知识库不存在"));

        if (!knowledgeBase.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "知识库不存在");
        }

        return knowledgeBase;
    }

    private KnowledgeDocumentEntity requireOwnedDocument(String userId, String knowledgeBaseId, String documentId) {
        KnowledgeDocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "文档不存在"));

        if (!document.getUserId().equals(userId) || !document.getKnowledgeBaseId().equals(knowledgeBaseId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文档不存在");
        }

        return document;
    }

    private String rebuildChunks(List<KnowledgeDocumentChunkEntity> chunks) {
        String embeddingModel = "none";

        for (KnowledgeDocumentChunkEntity chunk : chunks) {
            QdrantVectorService.UpsertResult upsertResult = qdrantVectorService.upsertChunkWithModel(chunk);
            chunk.setVectorId(upsertResult.vectorId());
            embeddingModel = upsertResult.embeddingModel();
            chunkRepository.save(chunk);
        }

        return embeddingModel;
    }

    private void syncDocumentVectorStatus(String userId, String knowledgeBaseId, String vectorStatus) {
        if ("empty".equals(vectorStatus)) {
            return;
        }

        List<KnowledgeDocumentEntity> documents =
                documentRepository.findAllByKnowledgeBaseIdAndUserIdOrderByUpdatedAtDesc(knowledgeBaseId, userId);

        documents.forEach(document -> document.setVectorStatus(vectorStatus));
        documentRepository.saveAll(documents);
    }
}
