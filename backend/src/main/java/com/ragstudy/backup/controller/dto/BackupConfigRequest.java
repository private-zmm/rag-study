package com.ragstudy.backup.controller.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BackupConfigRequest(
        boolean enabled,
        @NotBlank String endpoint,
        @NotBlank String bucket,
        @NotBlank String accessKey,
        String secretKey,
        String region,
        @NotBlank String prefix,
        @NotBlank String cronExpression,
        @NotNull @Min(0) @Max(3650) Integer retentionDays,
        @NotNull @Min(0) @Max(1000) Integer retentionCount,
        boolean pathStyleAccess,
        String pgDumpPath,
        String psqlPath
) {
}
