package com.ragstudy.backup.dal.repository;

import com.ragstudy.backup.dal.dataobject.BackupConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BackupConfigRepository extends JpaRepository<BackupConfigEntity, String> {
}
