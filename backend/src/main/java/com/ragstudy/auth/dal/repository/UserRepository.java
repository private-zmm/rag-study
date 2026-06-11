package com.ragstudy.auth.dal.repository;

import com.ragstudy.auth.dal.dataobject.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, String> {

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    Optional<UserEntity> findByUsernameOrEmail(String username, String email);
}
