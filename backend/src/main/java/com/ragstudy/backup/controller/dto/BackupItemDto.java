package com.ragstudy.backup.controller.dto;

import java.time.Instant;

public record BackupItemDto(
        String objectName,
        String fileName,
        Long size,
        Instant createdAt
) {
}
