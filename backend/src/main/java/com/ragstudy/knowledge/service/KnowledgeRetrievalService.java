package com.ragstudy.knowledge.service;

import com.ragstudy.knowledge.controller.dto.KnowledgeSearchResultDto;
import com.ragstudy.knowledge.dal.repository.KnowledgeBaseRepository;
import com.ragstudy.knowledge.dal.repository.KnowledgeDocumentChunkRepository;
import com.ragstudy.knowledge.framework.QdrantVectorService;
import com.ragstudy.knowledge.framework.RagProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class KnowledgeRetrievalService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeDocumentChunkRepository chunkRepository;
    private final QdrantVectorService qdrantVectorService;
    private final RagProperties ragProperties;

    public KnowledgeRetrievalService(
            KnowledgeBaseRepository knowledgeBaseRepository,
            KnowledgeDocumentChunkRepository chunkRepository,
            QdrantVectorService qdrantVectorService,
            RagProperties ragProperties
    ) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.chunkRepository = chunkRepository;
        this.qdrantVectorService = qdrantVectorService;
        this.ragProperties = ragProperties;
    }

    @Transactional(readOnly = true)
    public List<KnowledgeSearchResultDto> search(String userId, String knowledgeBaseId, String query, int limit) {
        return search(userId, knowledgeBaseId, query, Integer.valueOf(limit));
    }

    @Transactional(readOnly = true)
    public List<KnowledgeSearchResultDto> search(String userId, String knowledgeBaseId, String query, Integer limit) {
        requireOwnedKnowledgeBase(userId, knowledgeBaseId);
        int normalizedLimit = normalizeLimit(limit);

        return qdrantVectorService.search(userId, knowledgeBaseId, query, normalizedLimit)
                .stream()
                .filter(result -> result.score() >= ragProperties.getMinScore())
                .map(result -> new KnowledgeSearchResultDto(
                        result.chunkId(),
                        result.documentId(),
                        result.titlePath(),
                        result.heading(),
                        result.content(),
                        result.score()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<KnowledgeSearchResultDto> listContextChunks(String userId, String knowledgeBaseId, int limit) {
        return listContextChunks(userId, knowledgeBaseId, Integer.valueOf(limit));
    }

    @Transactional(readOnly = true)
    public List<KnowledgeSearchResultDto> listContextChunks(String userId, String knowledgeBaseId, Integer limit) {
        requireOwnedKnowledgeBase(userId, knowledgeBaseId);
        int normalizedLimit = normalizeLimit(limit);

        return chunkRepository.findAllByKnowledgeBaseIdAndUserIdOrderByDocumentIdAscChunkIndexAsc(knowledgeBaseId, userId)
                .stream()
                .limit(normalizedLimit)
                .map(chunk -> new KnowledgeSearchResultDto(
                        chunk.getId(),
                        chunk.getDocumentId(),
                        chunk.getTitlePath(),
                        chunk.getHeading(),
                        chunk.getContent(),
                        0
                ))
                .toList();
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return ragProperties.getTopK();
        }

        return Math.max(1, Math.min(limit, 50));
    }

    private void requireOwnedKnowledgeBase(String userId, String knowledgeBaseId) {
        knowledgeBaseRepository.findByIdAndUserId(knowledgeBaseId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "知识库不存在"));
    }
}
