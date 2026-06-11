package com.ragstudy.embedding.convert;

import com.ragstudy.embedding.controller.dto.EmbeddingModelConfigDto;
import com.ragstudy.embedding.dal.dataobject.EmbeddingModelConfigEntity;

public final class EmbeddingModelConfigConvert {

    private EmbeddingModelConfigConvert() {
    }

    public static EmbeddingModelConfigDto toDto(EmbeddingModelConfigEntity config) {
        return new EmbeddingModelConfigDto(
                config.getId(),
                config.getName(),
                config.getProviderType(),
                config.getBaseUrl(),
                config.getModel(),
                config.getDimensions(),
                config.isDefaultModel()
        );
    }
}
