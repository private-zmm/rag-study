package com.ragstudy.clipper.convert;

import com.ragstudy.clipper.controller.dto.WebClipDto;
import com.ragstudy.clipper.dal.dataobject.WebClipEntity;

import java.time.format.DateTimeFormatter;

public final class WebClipConvert {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private WebClipConvert() {
    }

    public static WebClipDto toDto(WebClipEntity clip) {
        return toDto(clip, null);
    }

    public static WebClipDto toDto(WebClipEntity clip, String content) {
        return new WebClipDto(
                clip.getId(),
                clip.getKnowledgeBaseId(),
                clip.getDocumentId(),
                clip.getUrl(),
                clip.getTitle(),
                clip.getSiteName(),
                clip.getExcerpt(),
                content,
                clip.getStatus(),
                clip.getCreatedAt().format(TIME_FORMATTER),
                clip.getUpdatedAt().format(TIME_FORMATTER)
        );
    }
}
