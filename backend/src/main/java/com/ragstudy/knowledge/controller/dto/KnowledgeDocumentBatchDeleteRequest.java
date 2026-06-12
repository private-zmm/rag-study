package com.ragstudy.knowledge.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record KnowledgeDocumentBatchDeleteRequest(
        @NotEmpty(message = "请选择要删除的文档")
        @Size(max = 200, message = "单次最多删除 200 个文档")
        List<@NotBlank(message = "文档 ID 不能为空") String> documentIds
) {
}
