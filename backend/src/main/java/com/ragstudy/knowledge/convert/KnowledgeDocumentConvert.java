package com.ragstudy.knowledge.convert;

import com.ragstudy.knowledge.controller.dto.KnowledgeDocumentDto;
import com.ragstudy.knowledge.dal.dataobject.KnowledgeDocumentEntity;

import java.time.format.DateTimeFormatter;

public final class KnowledgeDocumentConvert {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private KnowledgeDocumentConvert() {
    }

    public static KnowledgeDocumentDto toDto(KnowledgeDocumentEntity document, long chunkCount) {
        return new KnowledgeDocumentDto(
                document.getId(),
                document.getKnowledgeBaseId(),
                document.getTitle(),
                document.getSourceType(),
                document.getRawContent(),
                document.getParseStatus(),
                document.getVectorStatus(),
                chunkCount,
                document.getUpdatedAt().format(TIME_FORMATTER)
        );
    }
}
