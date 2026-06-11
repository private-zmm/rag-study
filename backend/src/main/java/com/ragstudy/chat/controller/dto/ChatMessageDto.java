package com.ragstudy.chat.controller.dto;

public record ChatMessageDto(
        String id,
        String role,
        String content
) {
}
