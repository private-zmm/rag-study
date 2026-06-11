package com.ragstudy.clipper.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record ClipperRequest(
        @NotBlank String url,
        String knowledgeBaseId,
        String title,
        String content,
        String target,
        String mode
) {
}
