package com.ragstudy.chat.controller.dto;

public record ChatModelConfigDto(
        String id,
        String name,
        String providerType,
        String baseUrl,
        String model,
        String systemPrompt,
        boolean defaultModel
) {
}
