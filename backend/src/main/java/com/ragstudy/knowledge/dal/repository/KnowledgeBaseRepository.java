package com.ragstudy.knowledge.dal.repository;

import com.ragstudy.knowledge.dal.dataobject.KnowledgeBaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBaseEntity, String> {

    List<KnowledgeBaseEntity> findAllByUserIdOrderByUpdatedAtDesc(String userId);

    Optional<KnowledgeBaseEntity> findByIdAndUserId(String id, String userId);
}
