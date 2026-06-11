package com.ragstudy.backup.controller.dto;

import java.time.Instant;

public record BackupConfigDto(
        boolean enabled,
        String endpoint,
        String bucket,
        String accessKey,
        String region,
        String prefix,
        String cronExpression,
        Integer retentionDays,
        Integer retentionCount,
        Instant lastBackupAt,
        Instant updatedAt,
        boolean pathStyleAccess,
        boolean secretConfigured,
        String pgDumpPath,
        String psqlPath
) {
}
