package com.ragstudy.note.controller.dto;

import java.util.List;

public record NoteExportRequest(
        List<String> noteIds,
        String folderPath
) {
}
