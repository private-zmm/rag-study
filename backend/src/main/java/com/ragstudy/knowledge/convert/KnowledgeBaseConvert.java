package com.ragstudy.knowledge.convert;

import com.ragstudy.knowledge.controller.dto.KnowledgeBaseDto;
import com.ragstudy.knowledge.dal.dataobject.KnowledgeBaseEntity;

public final class KnowledgeBaseConvert {

    private KnowledgeBaseConvert() {
    }

    public static KnowledgeBaseDto toDto(KnowledgeBaseEntity knowledgeBase) {
        return new KnowledgeBaseDto(
                knowledgeBase.getId(),
                knowledgeBase.getName(),
                knowledgeBase.getDescription(),
                knowledgeBase.getDocumentCount(),
                knowledgeBase.getChunkCount(),
                knowledgeBase.getVectorStatus()
        );
    }
}
