package com.ragstudy.embedding.controller.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record EmbeddingModelConfigRequest(
        @NotBlank(message = "请输入配置名称")
        String name,

        @NotBlank(message = "请选择供应商类型")
        String providerType,

        @NotBlank(message = "请输入 API 地址")
        String baseUrl,

        @NotBlank(message = "请输入 API Key")
        String apiKey,

        @NotBlank(message = "请输入模型名")
        String model,

        @Min(value = 1, message = "向量维度必须大于 0")
        @Max(value = 4096, message = "向量维度不能超过 4096")
        int dimensions,

        boolean defaultModel
) {
}
