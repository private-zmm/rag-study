package com.ragstudy.knowledge.dal.repository;

import com.ragstudy.knowledge.dal.dataobject.KnowledgeDocumentChunkEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KnowledgeDocumentChunkRepository extends JpaRepository<KnowledgeDocumentChunkEntity, String> {

    List<KnowledgeDocumentChunkEntity> findAllByKnowledgeBaseIdAndUserIdOrderByDocumentIdAscChunkIndexAsc(String knowledgeBaseId, String userId);

    List<KnowledgeDocumentChunkEntity> findAllByDocumentIdAndUserIdOrderByChunkIndexAsc(String documentId, String userId);

    long countByKnowledgeBaseIdAndUserId(String knowledgeBaseId, String userId);

    long countByDocumentIdAndUserId(String documentId, String userId);

    void deleteAllByDocumentIdAndUserId(String documentId, String userId);

    void deleteAllByKnowledgeBaseIdAndUserId(String knowledgeBaseId, String userId);
}
