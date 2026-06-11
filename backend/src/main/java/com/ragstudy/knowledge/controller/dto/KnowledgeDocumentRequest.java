package com.ragstudy.knowledge.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KnowledgeDocumentRequest(
        @NotBlank(message = "请输入文档标题")
        @Size(max = 120, message = "文档标题不能超过 120 个字符")
        String title,

        @NotBlank(message = "请输入文档内容")
        String rawContent
) {
}
