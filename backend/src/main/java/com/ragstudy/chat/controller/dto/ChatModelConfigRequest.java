package com.ragstudy.chat.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatModelConfigRequest(
        @NotBlank String name,
        @NotBlank String providerType,
        @NotBlank String baseUrl,
        @NotBlank String apiKey,
        @NotBlank String model,
        String systemPrompt,
        boolean defaultModel
) {
}
