package com.ragstudy.embedding.dal.repository;

import com.ragstudy.embedding.dal.dataobject.EmbeddingModelConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmbeddingModelConfigRepository extends JpaRepository<EmbeddingModelConfigEntity, String> {

    List<EmbeddingModelConfigEntity> findAllByUserIdOrderByDefaultModelDescUpdatedAtDesc(String userId);

    Optional<EmbeddingModelConfigEntity> findFirstByUserIdAndDefaultModelTrue(String userId);
}
