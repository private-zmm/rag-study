package com.ragstudy.clipper.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record ClipperPreviewRequest(
        @NotBlank String url,
        String mode,
        Boolean useProxy
) {
}
