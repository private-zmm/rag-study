package com.ragstudy.knowledge.dal.repository;

import com.ragstudy.knowledge.dal.dataobject.KnowledgeDocumentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocumentEntity, String> {

    List<KnowledgeDocumentEntity> findAllByKnowledgeBaseIdAndUserIdOrderByUpdatedAtDesc(String knowledgeBaseId, String userId);

    Page<KnowledgeDocumentEntity> findAllByKnowledgeBaseIdAndUserId(String knowledgeBaseId, String userId, Pageable pageable);

    @Query("""
            select document from KnowledgeDocumentEntity document
            where document.userId = :userId
              and (
                lower(document.title) like lower(concat('%', :query, '%'))
                or lower(document.rawContent) like lower(concat('%', :query, '%'))
              )
            order by document.updatedAt desc
            """)
    List<KnowledgeDocumentEntity> searchByUserId(@Param("userId") String userId, @Param("query") String query, Pageable pageable);

    Optional<KnowledgeDocumentEntity> findByKnowledgeBaseIdAndUserIdAndSourceTypeAndSourceId(
            String knowledgeBaseId,
            String userId,
            String sourceType,
            String sourceId
    );

    long countByKnowledgeBaseIdAndUserId(String knowledgeBaseId, String userId);

    void deleteAllByKnowledgeBaseIdAndUserId(String knowledgeBaseId, String userId);
}
