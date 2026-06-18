package com.ragstudy.clipper.dal.repository;

import com.ragstudy.clipper.dal.dataobject.ClipperVideoTaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClipperVideoTaskRepository extends JpaRepository<ClipperVideoTaskEntity, String> {

    Optional<ClipperVideoTaskEntity> findByIdAndUserId(String id, String userId);
}
