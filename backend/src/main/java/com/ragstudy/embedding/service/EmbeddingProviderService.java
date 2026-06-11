package com.ragstudy.embedding.service;

import com.ragstudy.embedding.dal.dataobject.EmbeddingModelConfigEntity;
import com.ragstudy.knowledge.framework.LocalHashEmbeddingService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class EmbeddingProviderService {

    private final EmbeddingModelConfigService configService;
    private final LocalHashEmbeddingService localHashEmbeddingService;

    public EmbeddingProviderService(
            EmbeddingModelConfigService configService,
            LocalHashEmbeddingService localHashEmbeddingService
    ) {
        this.configService = configService;
        this.localHashEmbeddingService = localHashEmbeddingService;
    }

    public EmbeddingVector embed(String userId, String content) {
        return configService.findDefaultConfig(userId)
                .map(config -> embedWithConfig(config, content))
                .orElseGet(() -> new EmbeddingVector(localHashEmbeddingService.embed(content), "local-hash"));
    }

    private EmbeddingVector embedWithConfig(EmbeddingModelConfigEntity config, String content) {
        OpenAiEmbeddingModel model = OpenAiEmbeddingModel.builder()
                .baseUrl(normalizeBaseUrl(config.getBaseUrl()))
                .apiKey(normalizeApiKey(config.getApiKey()))
                .modelName(config.getModel())
                .dimensions(config.getDimensions())
                .build();
        Response<Embedding> response = model.embed(content);

        if (response == null || response.content() == null || response.content().vector() == null) {
            throw new IllegalStateException("Embedding 服务没有返回向量");
        }

        return new EmbeddingVector(response.content().vector(), config.getModel());
    }

    private String normalizeBaseUrl(String baseUrl) {
        String normalizedBaseUrl = StringUtils.hasText(baseUrl)
                ? baseUrl.trim()
                : "https://dashscope.aliyuncs.com/compatible-mode/v1";

        if (normalizedBaseUrl.endsWith("/embeddings")) {
            normalizedBaseUrl = normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - "/embeddings".length());
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

    public record EmbeddingVector(float[] vector, String model) {
    }
}
