package com.ragstudy.embedding.service;

import com.ragstudy.embedding.controller.dto.EmbeddingModelConfigDto;
import com.ragstudy.embedding.controller.dto.EmbeddingModelConfigRequest;
import com.ragstudy.embedding.convert.EmbeddingModelConfigConvert;
import com.ragstudy.embedding.dal.dataobject.EmbeddingModelConfigEntity;
import com.ragstudy.embedding.dal.repository.EmbeddingModelConfigRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class EmbeddingModelConfigService {

    private final EmbeddingModelConfigRepository repository;

    public EmbeddingModelConfigService(EmbeddingModelConfigRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<EmbeddingModelConfigDto> listConfigs(String userId) {
        return repository.findAllByUserIdOrderByDefaultModelDescUpdatedAtDesc(userId)
                .stream()
                .map(EmbeddingModelConfigConvert::toDto)
                .toList();
    }

    @Transactional
    public EmbeddingModelConfigDto createConfig(String userId, EmbeddingModelConfigRequest request) {
        boolean shouldBeDefault = request.defaultModel() || repository.findFirstByUserIdAndDefaultModelTrue(userId).isEmpty();

        if (shouldBeDefault) {
            clearDefaultConfig(userId, null);
        }

        LocalDateTime now = LocalDateTime.now();
        EmbeddingModelConfigEntity config = new EmbeddingModelConfigEntity(
                UUID.randomUUID().toString(),
                userId,
                request.name().trim(),
                request.providerType().trim(),
                request.baseUrl().trim(),
                request.apiKey().trim(),
                request.model().trim(),
                request.dimensions(),
                shouldBeDefault,
                now,
                now
        );

        return EmbeddingModelConfigConvert.toDto(repository.save(config));
    }

    @Transactional
    public EmbeddingModelConfigDto updateConfig(String userId, String id, EmbeddingModelConfigRequest request) {
        EmbeddingModelConfigEntity config = requireOwnedConfig(userId, id);
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
                request.dimensions(),
                shouldBeDefault
        );

        return EmbeddingModelConfigConvert.toDto(repository.save(config));
    }

    @Transactional
    public EmbeddingModelConfigDto setDefaultConfig(String userId, String id) {
        EmbeddingModelConfigEntity config = requireOwnedConfig(userId, id);
        clearDefaultConfig(userId, id);
        config.setDefaultModel(true);
        return EmbeddingModelConfigConvert.toDto(repository.save(config));
    }

    @Transactional(readOnly = true)
    public Optional<EmbeddingModelConfigEntity> findDefaultConfig(String userId) {
        return repository.findFirstByUserIdAndDefaultModelTrue(userId);
    }

    private EmbeddingModelConfigEntity requireOwnedConfig(String userId, String id) {
        EmbeddingModelConfigEntity config = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Embedding 模型不存在"));

        if (!config.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Embedding 模型不存在");
        }

        return config;
    }

    private void clearDefaultConfig(String userId, String exceptId) {
        repository.findAllByUserIdOrderByDefaultModelDescUpdatedAtDesc(userId)
                .stream()
                .filter(EmbeddingModelConfigEntity::isDefaultModel)
                .filter(config -> exceptId == null || !config.getId().equals(exceptId))
                .forEach(config -> config.setDefaultModel(false));
    }
}
