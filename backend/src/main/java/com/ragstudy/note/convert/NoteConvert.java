package com.ragstudy.note.convert;

import com.ragstudy.note.controller.dto.NoteDto;
import com.ragstudy.note.dal.dataobject.NoteEntity;

import java.time.format.DateTimeFormatter;
import java.util.List;

public final class NoteConvert {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private NoteConvert() {
    }

    public static NoteDto toDto(NoteEntity note) {
        return new NoteDto(
                note.getId(),
                note.getTitle(),
                note.getUpdatedAt().format(TIME_FORMATTER),
                List.of(),
                note.getContent()
        );
    }
}
