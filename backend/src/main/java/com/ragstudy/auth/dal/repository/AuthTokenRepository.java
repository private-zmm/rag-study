package com.ragstudy.auth.dal.repository;

import com.ragstudy.auth.dal.dataobject.AuthTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthTokenRepository extends JpaRepository<AuthTokenEntity, String> {
}
