package com.ragstudy.note.controller.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record NoteAssetBindRequest(
        @NotEmpty String noteId,
        @Size(max = 200) List<String> objectNames
) {
}
