package com.ragstudy.chat.controller.dto;

public record ChatConversationDto(
        String id,
        String title,
        String updatedAt,
        boolean archived
) {
}
