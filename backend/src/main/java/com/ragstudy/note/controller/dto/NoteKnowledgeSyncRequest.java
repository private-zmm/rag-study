package com.ragstudy.note.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record NoteKnowledgeSyncRequest(
        @NotBlank String knowledgeBaseId,
        @NotEmpty @Size(max = 500) List<@NotBlank String> noteIds
) {
}
