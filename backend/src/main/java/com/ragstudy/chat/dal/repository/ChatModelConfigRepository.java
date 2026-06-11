package com.ragstudy.chat.dal.repository;

import com.ragstudy.chat.dal.dataobject.ChatModelConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ChatModelConfigRepository extends JpaRepository<ChatModelConfigEntity, String> {

    @Query("select config from ChatModelConfigEntity config where config.userId = :userId order by config.defaultModel desc, config.updatedAt desc")
    List<ChatModelConfigEntity> findAllByUserIdOrderByDefaultModelDescUpdatedAtDesc(String userId);

    Optional<ChatModelConfigEntity> findFirstByUserIdAndDefaultModelTrue(String userId);
}
