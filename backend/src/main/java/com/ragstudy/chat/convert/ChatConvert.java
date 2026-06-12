package com.ragstudy.chat.convert;

import com.ragstudy.chat.controller.dto.ChatConversationDto;
import com.ragstudy.chat.controller.dto.ChatMessageDto;
import com.ragstudy.chat.controller.dto.ChatModelConfigDto;
import com.ragstudy.chat.dal.dataobject.ChatConversationEntity;
import com.ragstudy.chat.dal.dataobject.ChatMessageEntity;
import com.ragstudy.chat.dal.dataobject.ChatModelConfigEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

public final class ChatConvert {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

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
                message.getContent(),
                parseSuggestedQuestions(message.getSuggestedQuestions())
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

    private static List<String> parseSuggestedQuestions(String suggestedQuestions) {
        if (suggestedQuestions == null || suggestedQuestions.isBlank()) {
            return List.of();
        }

        try {
            return OBJECT_MAPPER.readValue(suggestedQuestions, STRING_LIST_TYPE);
        } catch (Exception exception) {
            return List.of();
        }
    }
}
