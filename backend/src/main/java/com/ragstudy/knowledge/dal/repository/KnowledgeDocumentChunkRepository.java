package com.ragstudy.knowledge.dal.repository;

import com.ragstudy.knowledge.dal.dataobject.KnowledgeDocumentChunkEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface KnowledgeDocumentChunkRepository extends JpaRepository<KnowledgeDocumentChunkEntity, String> {

    List<KnowledgeDocumentChunkEntity> findAllByKnowledgeBaseIdAndUserIdOrderByDocumentIdAscChunkIndexAsc(String knowledgeBaseId, String userId);

    List<KnowledgeDocumentChunkEntity> findAllByDocumentIdAndUserIdOrderByChunkIndexAsc(String documentId, String userId);

    @Query("""
            select chunk from KnowledgeDocumentChunkEntity chunk
            where chunk.knowledgeBaseId = :knowledgeBaseId
              and chunk.userId = :userId
              and (
                lower(coalesce(chunk.titlePath, '')) like lower(concat('%', :query, '%'))
                or lower(coalesce(chunk.heading, '')) like lower(concat('%', :query, '%'))
                or lower(chunk.content) like lower(concat('%', :query, '%'))
              )
            order by chunk.documentId asc, chunk.chunkIndex asc
            """)
    List<KnowledgeDocumentChunkEntity> searchByKeyword(
            @Param("userId") String userId,
            @Param("knowledgeBaseId") String knowledgeBaseId,
            @Param("query") String query,
            Pageable pageable
    );

    long countByKnowledgeBaseIdAndUserId(String knowledgeBaseId, String userId);

    long countByDocumentIdAndUserId(String documentId, String userId);

    void deleteAllByDocumentIdAndUserId(String documentId, String userId);

    void deleteAllByKnowledgeBaseIdAndUserId(String knowledgeBaseId, String userId);
}
