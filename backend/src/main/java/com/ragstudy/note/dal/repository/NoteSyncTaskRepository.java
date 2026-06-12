package com.ragstudy.note.dal.repository;

import com.ragstudy.note.dal.dataobject.NoteSyncTaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NoteSyncTaskRepository extends JpaRepository<NoteSyncTaskEntity, String> {

    Optional<NoteSyncTaskEntity> findByIdAndUserId(String id, String userId);
}
