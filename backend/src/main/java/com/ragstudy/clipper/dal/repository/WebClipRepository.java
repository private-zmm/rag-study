package com.ragstudy.clipper.dal.repository;

import com.ragstudy.clipper.dal.dataobject.WebClipEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WebClipRepository extends JpaRepository<WebClipEntity, String> {

    List<WebClipEntity> findAllByUserIdOrderByUpdatedAtDesc(String userId, Pageable pageable);

    Optional<WebClipEntity> findFirstByUserIdAndCanonicalUrlOrderByUpdatedAtDesc(String userId, String canonicalUrl);
}
