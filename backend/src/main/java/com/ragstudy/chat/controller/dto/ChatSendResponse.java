package com.ragstudy.chat.controller.dto;

import java.util.List;

public record ChatSendResponse(
        String conversationId,
        List<ChatMessageDto> messages
) {
}
