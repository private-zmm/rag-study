package com.ragstudy.clipper.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record ClipperVideoTaskRequest(
        @NotBlank String url,
        @NotBlank String knowledgeBaseId,
        String platform,
        String language,
        String modelSize,
        Boolean keepTimestamps
) {
}
