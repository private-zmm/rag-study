package com.ragstudy.chat.framework;

import com.ragstudy.chat.controller.dto.ChatMessageDto;
import com.ragstudy.chat.dal.dataobject.ChatModelConfigEntity;
import com.ragstudy.chat.service.ChatStreamHandler;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Service
public class OpenAiCompatibleChatClient {

    private final AiChatProperties properties;

    public OpenAiCompatibleChatClient(AiChatProperties properties) {
        this.properties = properties;
    }

    public String generateReply(List<ChatMessageDto> conversation, ChatModelConfigEntity config) {
        ModelSettings settings = resolveSettings(config);

        if (!StringUtils.hasText(settings.baseUrl())) {
            return "模型 API 地址还没有配置。请先在设置页填写 API 地址、API Key 和模型名，再继续对话。";
        }

        try {
            ChatModel model = OpenAiChatModel.builder()
                    .baseUrl(settings.baseUrl())
                    .apiKey(settings.apiKey())
                    .modelName(settings.model())
                    .temperature(properties.getTemperature())
                    .build();
            ChatResponse response = model.chat(buildMessages(conversation, config));

            if (response == null || response.aiMessage() == null || !StringUtils.hasText(response.aiMessage().text())) {
                return "模型服务已响应，但回复内容为空。";
            }

            return response.aiMessage().text();
        } catch (Exception exception) {
            return "模型调用失败：" + exception.getMessage();
        }
    }

    public String streamReply(List<ChatMessageDto> conversation, ChatModelConfigEntity config, ChatStreamHandler handler) {
        ModelSettings settings = resolveSettings(config);

        if (!StringUtils.hasText(settings.baseUrl())) {
            return "模型 API 地址还没有配置。请先在设置页填写 API 地址、API Key 和模型名，再继续对话。";
        }

        StringBuilder reply = new StringBuilder();
        CountDownLatch finished = new CountDownLatch(1);

        try {
            OpenAiStreamingChatModel model = OpenAiStreamingChatModel.builder()
                    .baseUrl(settings.baseUrl())
                    .apiKey(settings.apiKey())
                    .modelName(settings.model())
                    .temperature(properties.getTemperature())
                    .build();

            model.chat(buildMessages(conversation, config), new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partialResponse) {
                    if (!StringUtils.hasText(partialResponse)) {
                        return;
                    }

                    reply.append(partialResponse);
                    handler.onDelta(partialResponse);
                }

                @Override
                public void onCompleteResponse(ChatResponse completeResponse) {
                    if (!reply.isEmpty() || completeResponse == null || completeResponse.aiMessage() == null) {
                        finished.countDown();
                        return;
                    }

                    String text = completeResponse.aiMessage().text();
                    if (StringUtils.hasText(text)) {
                        reply.append(text);
                    }

                    finished.countDown();
                }

                @Override
                public void onError(Throwable error) {
                    String message = "模型调用失败：" + error.getMessage();
                    if (reply.isEmpty()) {
                        reply.append(message);
                        handler.onDelta(message);
                    }

                    finished.countDown();
                }
            });

            if (!finished.await(5, TimeUnit.MINUTES) && reply.isEmpty()) {
                String error = "模型调用超时，请稍后重试。";
                reply.append(error);
                handler.onDelta(error);
            }
        } catch (Exception exception) {
            String error = "模型调用失败：" + exception.getMessage();

            if (reply.isEmpty()) {
                reply.append(error);
                handler.onDelta(error);
            }
        }

        return reply.toString();
    }

    private List<ChatMessage> buildMessages(List<ChatMessageDto> conversation, ChatModelConfigEntity config) {
        List<ChatMessage> messages = new ArrayList<>();
        String systemPrompt = StringUtils.hasText(config.getSystemPrompt())
                ? config.getSystemPrompt()
                : properties.getSystemPrompt();

        if (StringUtils.hasText(systemPrompt)) {
            messages.add(SystemMessage.from(systemPrompt));
        }

        conversation.stream()
                .filter(message -> StringUtils.hasText(message.content()))
                .map(this::toLangChainMessage)
                .forEach(messages::add);

        return messages;
    }

    private ChatMessage toLangChainMessage(ChatMessageDto message) {
        if ("ai".equals(message.role())) {
            return AiMessage.from(message.content());
        }

        return UserMessage.from(message.content());
    }

    private ModelSettings resolveSettings(ChatModelConfigEntity config) {
        String baseUrl = StringUtils.hasText(config.getBaseUrl()) ? config.getBaseUrl() : properties.getBaseUrl();
        String apiKey = StringUtils.hasText(config.getApiKey()) ? config.getApiKey() : properties.getApiKey();
        String model = StringUtils.hasText(config.getModel()) ? config.getModel() : properties.getModel();

        return new ModelSettings(normalizeBaseUrl(baseUrl), normalizeApiKey(apiKey), model);
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            return "";
        }

        String normalizedBaseUrl = baseUrl.trim();

        if (normalizedBaseUrl.endsWith("/chat/completions")) {
            normalizedBaseUrl = normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - "/chat/completions".length());
        }

        return normalizedBaseUrl.endsWith("/")
                ? normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - 1)
                : normalizedBaseUrl;
    }

    private String normalizeApiKey(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            return "";
        }

        String trimmedApiKey = apiKey.trim();
        return trimmedApiKey.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())
                ? trimmedApiKey.substring("Bearer ".length()).trim()
                : trimmedApiKey;
    }

    private record ModelSettings(String baseUrl, String apiKey, String model) {
    }
}
