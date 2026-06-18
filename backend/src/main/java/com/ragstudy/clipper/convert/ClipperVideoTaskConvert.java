package com.ragstudy.clipper.convert;

import com.ragstudy.clipper.controller.dto.ClipperVideoTaskDto;
import com.ragstudy.clipper.dal.dataobject.ClipperVideoTaskEntity;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class ClipperVideoTaskConvert {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private ClipperVideoTaskConvert() {
    }

    public static ClipperVideoTaskDto toDto(ClipperVideoTaskEntity task) {
        return new ClipperVideoTaskDto(
                task.getId(),
                task.getKnowledgeBaseId(),
                task.getUrl(),
                task.getPlatform(),
                task.getStatus(),
                task.getTitle(),
                task.getDocumentId(),
                task.getErrorMessage(),
                format(task.getCreatedAt()),
                format(task.getUpdatedAt()),
                format(task.getStartedAt()),
                format(task.getFinishedAt())
        );
    }

    private static String format(LocalDateTime time) {
        return time == null ? null : time.format(TIME_FORMATTER);
    }
}
