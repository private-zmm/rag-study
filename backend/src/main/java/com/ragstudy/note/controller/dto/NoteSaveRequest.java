package com.ragstudy.note.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record NoteSaveRequest(
        @NotBlank String title,
        @NotBlank String content
) {
}
