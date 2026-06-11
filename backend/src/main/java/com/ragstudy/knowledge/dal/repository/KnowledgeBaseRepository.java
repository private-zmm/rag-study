package com.ragstudy.knowledge.dal.repository;

import com.ragstudy.knowledge.dal.dataobject.KnowledgeBaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBaseEntity, String> {

    List<KnowledgeBaseEntity> findAllByUserIdOrderByUpdatedAtDesc(String userId);
}
