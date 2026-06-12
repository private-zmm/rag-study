package com.ragstudy.chat.controller.dto;

import java.util.List;

public record ChatMessageDto(
        String id,
        String role,
        String content,
        List<String> suggestedQuestions
) {
}
