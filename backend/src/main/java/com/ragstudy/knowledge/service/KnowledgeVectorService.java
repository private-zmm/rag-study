package com.ragstudy.knowledge.service;

import com.ragstudy.knowledge.controller.dto.KnowledgeIndexResultDto;
import com.ragstudy.knowledge.controller.dto.KnowledgeSearchResultDto;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Compatibility facade kept while callers migrate to KnowledgeIndexService and
 * KnowledgeRetrievalService.
 */
@Service
public class KnowledgeVectorService {

    private final KnowledgeIndexService indexService;
    private final KnowledgeRetrievalService retrievalService;

    public KnowledgeVectorService(KnowledgeIndexService indexService, KnowledgeRetrievalService retrievalService) {
        this.indexService = indexService;
        this.retrievalService = retrievalService;
    }

    public KnowledgeIndexResultDto rebuildIndex(String userId, String knowledgeBaseId) {
        return indexService.rebuildIndex(userId, knowledgeBaseId);
    }

    public KnowledgeIndexResultDto rebuildDocumentIndex(String userId, String knowledgeBaseId, String documentId) {
        return indexService.rebuildDocumentIndex(userId, knowledgeBaseId, documentId);
    }

    public List<KnowledgeSearchResultDto> search(String userId, String knowledgeBaseId, String query, int limit) {
        return retrievalService.search(userId, knowledgeBaseId, query, limit);
    }

    public List<KnowledgeSearchResultDto> listContextChunks(String userId, String knowledgeBaseId, int limit) {
        return retrievalService.listContextChunks(userId, knowledgeBaseId, limit);
    }
}
