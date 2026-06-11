package com.ragstudy.knowledge.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KnowledgeBaseRequest(
        @NotBlank(message = "请输入知识库名称")
        @Size(max = 80, message = "知识库名称不能超过 80 个字符")
        String name,

        @Size(max = 500, message = "知识库描述不能超过 500 个字符")
        String description
) {
}
