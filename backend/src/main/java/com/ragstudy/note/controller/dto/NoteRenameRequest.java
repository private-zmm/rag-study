package com.ragstudy.note.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record NoteRenameRequest(
        @NotBlank String title
) {
}
