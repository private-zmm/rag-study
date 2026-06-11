package com.ragstudy.note.controller.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record NoteImportRequest(
        @NotEmpty @Size(max = 500) List<@Valid ImportedNote> notes
) {

    public record ImportedNote(
            @NotBlank String title,
            @NotNull String content
    ) {
    }
}
