package com.ragstudy.note.controller.dto;

public record NoteAssetUploadResponse(
        String url,
        String storagePath,
        String objectName
) {
}
