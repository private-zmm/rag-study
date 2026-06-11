package com.ragstudy.clipper.controller.dto;

public record ClipperResultDto(
        String id,
        String url,
        String title,
        String target,
        String mode,
        String status
) {
}
