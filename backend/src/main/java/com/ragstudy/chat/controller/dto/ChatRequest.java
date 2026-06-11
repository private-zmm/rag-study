package com.ragstudy.chat.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(
        @NotBlank String content,
        String modelConfigId,
        String conversationId,
        String knowledgeBaseId
) {
}
