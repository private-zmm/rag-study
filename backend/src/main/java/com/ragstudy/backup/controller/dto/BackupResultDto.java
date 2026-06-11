package com.ragstudy.backup.controller.dto;

import java.time.Instant;

public record BackupResultDto(
        String objectName,
        String fileName,
        Long size,
        Instant createdAt
) {
}
