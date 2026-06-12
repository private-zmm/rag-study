package com.ragstudy.chat.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragstudy.chat.controller.dto.ChatMessageDto;
import com.ragstudy.chat.dal.dataobject.ChatModelConfigEntity;
import com.ragstudy.chat.framework.AiChatProperties;
import com.ragstudy.chat.framework.OpenAiCompatibleChatClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class ChatSuggestionService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final OpenAiCompatibleChatClient chatClient;
    private final AiChatProperties aiChatProperties;

    public ChatSuggestionService(OpenAiCompatibleChatClient chatClient, AiChatProperties aiChatProperties) {
        this.chatClient = chatClient;
        this.aiChatProperties = aiChatProperties;
    }

    public List<String> generateFollowUpSuggestions(
            List<ChatMessageDto> modelSnapshot,
            String assistantReply,
            ChatModelConfigEntity modelConfig
    ) {
        if (!aiChatProperties.isFollowUpSuggestionsEnabled() || !StringUtils.hasText(assistantReply)) {
            return List.of();
        }

        String prompt = buildPrompt(modelSnapshot, assistantReply);
        String response = chatClient.generateReply(List.of(new ChatMessageDto("follow-up-suggestions", "user", prompt, List.of())), modelConfig);
        return normalizeSuggestions(parseSuggestions(response));
    }

    public String toJson(List<String> suggestions) {
        if (suggestions == null || suggestions.isEmpty()) {
            return null;
        }

        try {
            return OBJECT_MAPPER.writeValueAsString(suggestions);
        } catch (Exception exception) {
            return null;
        }
    }

    private String buildPrompt(List<ChatMessageDto> modelSnapshot, String assistantReply) {
        String latestQuestion = latestUserQuestion(modelSnapshot);
        return """
                请基于下面这轮对话，生成适合用户继续追问的中文问题。
                要求：
                1. 只输出 JSON 字符串数组，不要 Markdown，不要解释。
                2. 数组长度为 %d。
                3. 每个问题要具体、自然、能直接作为下一轮用户输入。
                4. 避免重复已回答的问题，优先补充比较、落地方案、风险、下一步操作。

                用户最新问题：
                %s

                助手回答：
                %s
                """.formatted(aiChatProperties.getFollowUpSuggestionCount(), latestQuestion, assistantReply);
    }

    private String latestUserQuestion(List<ChatMessageDto> modelSnapshot) {
        for (int index = modelSnapshot.size() - 1; index >= 0; index -= 1) {
            ChatMessageDto message = modelSnapshot.get(index);

            if ("user".equals(message.role()) && StringUtils.hasText(message.content())) {
                return message.content();
            }
        }

        return "";
    }

    private List<String> parseSuggestions(String response) {
        if (!StringUtils.hasText(response)) {
            return List.of();
        }

        String json = extractJsonArray(response);

        if (!StringUtils.hasText(json)) {
            return List.of();
        }

        try {
            return OBJECT_MAPPER.readValue(json, STRING_LIST_TYPE);
        } catch (Exception exception) {
            return List.of();
        }
    }

    private String extractJsonArray(String response) {
        int start = response.indexOf('[');
        int end = response.lastIndexOf(']');

        if (start < 0 || end <= start) {
            return "";
        }

        return response.substring(start, end + 1);
    }

    private List<String> normalizeSuggestions(List<String> suggestions) {
        List<String> normalizedSuggestions = new ArrayList<>();

        for (String suggestion : suggestions) {
            if (!StringUtils.hasText(suggestion)) {
                continue;
            }

            String normalizedSuggestion = suggestion.replaceAll("\\s+", " ").trim();

            if (normalizedSuggestion.length() > 120) {
                normalizedSuggestion = normalizedSuggestion.substring(0, 120);
            }

            if (!normalizedSuggestions.contains(normalizedSuggestion)) {
                normalizedSuggestions.add(normalizedSuggestion);
            }

            if (normalizedSuggestions.size() >= aiChatProperties.getFollowUpSuggestionCount()) {
                break;
            }
        }

        return normalizedSuggestions;
    }
}
