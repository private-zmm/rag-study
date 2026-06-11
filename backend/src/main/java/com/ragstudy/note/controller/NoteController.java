package com.ragstudy.note.controller;

import com.ragstudy.auth.service.AuthService;
import com.ragstudy.auth.dal.dataobject.UserEntity;
import com.ragstudy.common.ApiResponse;
import com.ragstudy.note.controller.dto.NoteDto;
import com.ragstudy.note.controller.dto.NoteImportRequest;
import com.ragstudy.note.controller.dto.NoteKnowledgeSyncRequest;
import com.ragstudy.note.controller.dto.NoteKnowledgeSyncResponse;
import com.ragstudy.note.controller.dto.NoteRenameRequest;
import com.ragstudy.note.controller.dto.NoteSaveRequest;
import com.ragstudy.note.service.NoteKnowledgeSyncService;
import com.ragstudy.note.service.NoteService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notes")
public class NoteController {

    private final NoteService noteService;
    private final NoteKnowledgeSyncService noteKnowledgeSyncService;
    private final AuthService authService;

    public NoteController(NoteService noteService, NoteKnowledgeSyncService noteKnowledgeSyncService, AuthService authService) {
        this.noteService = noteService;
        this.noteKnowledgeSyncService = noteKnowledgeSyncService;
        this.authService = authService;
    }

    @GetMapping
    public ApiResponse<List<NoteDto>> listNotes(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        UserEntity user = authService.requireUser(authorizationHeader);
        return ApiResponse.ok(noteService.listNotes(user.getId()));
    }

    @PostMapping
    public ApiResponse<NoteDto> createNote(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody NoteSaveRequest request
    ) {
        UserEntity user = authService.requireUser(authorizationHeader);
        return ApiResponse.ok(noteService.createNote(user.getId(), request));
    }

    @PostMapping("/import")
    public ApiResponse<List<NoteDto>> importNotes(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody NoteImportRequest request
    ) {
        UserEntity user = authService.requireUser(authorizationHeader);
        return ApiResponse.ok(noteService.importNotes(user.getId(), request));
    }

    @PostMapping("/sync-to-knowledge")
    public ApiResponse<NoteKnowledgeSyncResponse> syncToKnowledge(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody NoteKnowledgeSyncRequest request
    ) {
        UserEntity user = authService.requireUser(authorizationHeader);
        return ApiResponse.ok(noteKnowledgeSyncService.syncNotes(user.getId(), request));
    }

    @PutMapping("/{id}")
    public ApiResponse<NoteDto> saveNote(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String id,
            @Valid @RequestBody NoteSaveRequest request
    ) {
        UserEntity user = authService.requireUser(authorizationHeader);
        return ApiResponse.ok(noteService.saveNote(user.getId(), id, request));
    }

    @PatchMapping("/{id}/title")
    public ApiResponse<NoteDto> renameNote(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String id,
            @Valid @RequestBody NoteRenameRequest request
    ) {
        UserEntity user = authService.requireUser(authorizationHeader);
        return ApiResponse.ok(noteService.renameNote(user.getId(), id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteNote(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String id
    ) {
        UserEntity user = authService.requireUser(authorizationHeader);
        noteService.deleteNote(user.getId(), id);
        return ApiResponse.ok(null);
    }
}
