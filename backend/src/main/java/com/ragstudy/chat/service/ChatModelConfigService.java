package com.ragstudy.chat.service;

import com.ragstudy.chat.controller.dto.ChatModelConfigDto;
import com.ragstudy.chat.controller.dto.ChatModelConfigRequest;
import com.ragstudy.chat.convert.ChatConvert;
import com.ragstudy.chat.dal.dataobject.ChatModelConfigEntity;
import com.ragstudy.chat.dal.repository.ChatModelConfigRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ChatModelConfigService {

    private final ChatModelConfigRepository repository;

    public ChatModelConfigService(ChatModelConfigRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ChatModelConfigDto> listConfigs(String userId) {
        return repository.findAllByUserIdOrderByDefaultModelDescUpdatedAtDesc(userId)
                .stream()
                .map(ChatConvert::toModelConfigDto)
                .toList();
    }

    @Transactional
    public ChatModelConfigDto createConfig(String userId, ChatModelConfigRequest request) {
        boolean shouldBeDefault = request.defaultModel() || repository.findFirstByUserIdAndDefaultModelTrue(userId).isEmpty();

        if (shouldBeDefault) {
            clearDefaultConfig(userId, null);
        }

        LocalDateTime now = LocalDateTime.now();
        ChatModelConfigEntity config = new ChatModelConfigEntity(
                UUID.randomUUID().toString(),
                userId,
                request.name().trim(),
                request.providerType().trim(),
                request.baseUrl().trim(),
                request.apiKey().trim(),
                request.model().trim(),
                normalizeSystemPrompt(request.systemPrompt(), shouldBeDefault),
                shouldBeDefault,
                now,
                now
        );

        return ChatConvert.toModelConfigDto(repository.save(config));
    }

    @Transactional
    public ChatModelConfigDto updateConfig(String userId, String id, ChatModelConfigRequest request) {
        ChatModelConfigEntity config = requireOwnedConfig(userId, id);
        boolean shouldBeDefault = request.defaultModel();

        if (shouldBeDefault) {
            clearDefaultConfig(userId, id);
        }

        config.update(
                request.name().trim(),
                request.providerType().trim(),
                request.baseUrl().trim(),
                request.apiKey().trim(),
                request.model().trim(),
                normalizeSystemPrompt(request.systemPrompt(), shouldBeDefault),
                shouldBeDefault
        );

        return ChatConvert.toModelConfigDto(repository.save(config));
    }

    @Transactional
    public ChatModelConfigDto setDefaultConfig(String userId, String id) {
        ChatModelConfigEntity config = requireOwnedConfig(userId, id);
        clearDefaultConfig(userId, id);
        config.setDefaultModel(true);
        return ChatConvert.toModelConfigDto(repository.save(config));
    }

    @Transactional(readOnly = true)
    public ChatModelConfigEntity requireConfigForChat(String userId, String id) {
        if (id == null || id.isBlank()) {
            return repository.findFirstByUserIdAndDefaultModelTrue(userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先在设置页新增默认聊天模型"));
        }

        return requireOwnedConfig(userId, id);
    }

    private ChatModelConfigEntity requireOwnedConfig(String userId, String id) {
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "聊天模型不存在"));
    }

    private void clearDefaultConfig(String userId, String exceptId) {
        repository.findAllByUserIdOrderByDefaultModelDescUpdatedAtDesc(userId)
                .stream()
                .filter(config -> config.isDefaultModel())
                .filter(config -> exceptId == null || !config.getId().equals(exceptId))
                .forEach(config -> config.setDefaultModel(false));
    }

    private String normalizeSystemPrompt(String systemPrompt, boolean defaultModel) {
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            return systemPrompt.trim();
        }

        return "";
    }
}
