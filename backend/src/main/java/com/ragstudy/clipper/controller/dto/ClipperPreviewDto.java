package com.ragstudy.clipper.controller.dto;

public record ClipperPreviewDto(
        String url,
        String title,
        String content,
        String excerpt,
        String siteName,
        String contentType,
        int wordCount,
        WebClipDto existingClip
) {
}
