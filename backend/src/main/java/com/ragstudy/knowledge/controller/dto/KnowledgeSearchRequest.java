package com.ragstudy.knowledge.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record KnowledgeSearchRequest(
        @NotBlank(message = "请输入搜索内容")
        String query,

        Integer limit
) {
}
