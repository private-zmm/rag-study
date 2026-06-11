package com.ragstudy.note.convert;

import com.ragstudy.note.controller.dto.NoteFolderDto;
import com.ragstudy.note.dal.dataobject.NoteFolderEntity;

import java.time.format.DateTimeFormatter;

public final class NoteFolderConvert {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private NoteFolderConvert() {
    }

    public static NoteFolderDto toDto(NoteFolderEntity folder) {
        return new NoteFolderDto(
                folder.getId(),
                folder.getPath(),
                folder.getUpdatedAt().format(FORMATTER)
        );
    }
}
