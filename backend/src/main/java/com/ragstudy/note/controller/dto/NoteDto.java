package com.ragstudy.note.controller.dto;

import java.util.List;

public record NoteDto(
        String id,
        String title,
        String updatedAt,
        List<String> tags,
        String content
) {
}
