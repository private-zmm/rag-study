package com.ragstudy.chat.convert;

import com.ragstudy.chat.controller.dto.ChatConversationDto;
import com.ragstudy.chat.controller.dto.ChatMessageDto;
import com.ragstudy.chat.controller.dto.ChatModelConfigDto;
import com.ragstudy.chat.dal.dataobject.ChatConversationEntity;
import com.ragstudy.chat.dal.dataobject.ChatMessageEntity;
import com.ragstudy.chat.dal.dataobject.ChatModelConfigEntity;

public final class ChatConvert {

    private ChatConvert() {
    }

    public static ChatConversationDto toConversationDto(ChatConversationEntity conversation) {
        return new ChatConversationDto(
                conversation.getId(),
                conversation.getTitle(),
                conversation.getUpdatedAt().toString(),
                conversation.isArchived()
        );
    }

    public static ChatMessageDto toMessageDto(ChatMessageEntity message) {
        return new ChatMessageDto(
                message.getId(),
                message.getRole(),
                message.getContent()
        );
    }

    public static ChatModelConfigDto toModelConfigDto(ChatModelConfigEntity config) {
        return new ChatModelConfigDto(
                config.getId(),
                config.getName(),
                config.getProviderType(),
                config.getBaseUrl(),
                config.getModel(),
                config.getSystemPrompt(),
                config.isDefaultModel()
        );
    }
}
