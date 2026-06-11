package com.ragstudy.chat.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatConversationRenameRequest(
        @NotBlank String title
) {
}
