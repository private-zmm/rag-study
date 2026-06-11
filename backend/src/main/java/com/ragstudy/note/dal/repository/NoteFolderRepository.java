package com.ragstudy.note.dal.repository;

import com.ragstudy.note.dal.dataobject.NoteFolderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NoteFolderRepository extends JpaRepository<NoteFolderEntity, String> {

    List<NoteFolderEntity> findAllByUserIdOrderByPathAsc(String userId);

    boolean existsByUserIdAndPath(String userId, String path);

    Optional<NoteFolderEntity> findByIdAndUserId(String id, String userId);
}
