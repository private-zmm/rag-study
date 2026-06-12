package com.ragstudy.note.service;

import com.ragstudy.note.controller.dto.NoteDto;
import com.ragstudy.note.controller.dto.NoteImportRequest;
import com.ragstudy.note.controller.dto.NoteRenameRequest;
import com.ragstudy.note.controller.dto.NoteSaveRequest;
import com.ragstudy.note.convert.NoteConvert;
import com.ragstudy.note.dal.dataobject.NoteEntity;
import com.ragstudy.note.dal.repository.NoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class NoteService {

    private final NoteRepository noteRepository;
    private final NoteAssetService noteAssetService;

    public NoteService(NoteRepository noteRepository, NoteAssetService noteAssetService) {
        this.noteRepository = noteRepository;
        this.noteAssetService = noteAssetService;
    }

    @Transactional(readOnly = true)
    public List<NoteDto> listNotes(String userId) {
        return noteRepository.findAllByUserIdOrderByUpdatedAtDesc(userId)
                .stream()
                .map(NoteConvert::toDto)
                .toList();
    }

    @Transactional
    public NoteDto createNote(String userId, NoteSaveRequest request) {
        LocalDateTime now = LocalDateTime.now();
        NoteEntity note = new NoteEntity(
                UUID.randomUUID().toString(),
                userId,
                sanitizeText(request.title()).trim(),
                sanitizeText(request.content()),
                now,
                now
        );
        return NoteConvert.toDto(noteRepository.save(note));
    }

    @Transactional
    public List<NoteDto> importNotes(String userId, NoteImportRequest request) {
        LocalDateTime now = LocalDateTime.now();
        List<NoteEntity> importedNotes = request.notes()
                .stream()
                .map(importedNote -> new NoteEntity(
                        UUID.randomUUID().toString(),
                        userId,
                        sanitizeText(importedNote.title()).trim(),
                        sanitizeText(importedNote.content()),
                        now,
                        now
                ))
                .toList();

        return noteRepository.saveAll(importedNotes)
                .stream()
                .map(NoteConvert::toDto)
                .toList();
    }

    @Transactional
    public NoteDto saveNote(String userId, String id, NoteSaveRequest request) {
        NoteEntity note = requireOwnedNote(userId, id);
        note.update(sanitizeText(request.title()).trim(), sanitizeText(request.content()));

        return NoteConvert.toDto(noteRepository.save(note));
    }

    @Transactional
    public NoteDto renameNote(String userId, String id, NoteRenameRequest request) {
        NoteEntity note = requireOwnedNote(userId, id);
        note.rename(sanitizeText(request.title()).trim());

        return NoteConvert.toDto(noteRepository.save(note));
    }

    @Transactional
    public void deleteNote(String userId, String id) {
        NoteEntity note = requireOwnedNote(userId, id);
        noteAssetService.deleteAssetsForNote(userId, id);
        noteRepository.delete(note);
    }

    private NoteEntity requireOwnedNote(String userId, String id) {
        return noteRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "笔记不存在"));
    }

    private String sanitizeText(String value) {
        return value.replace("\u0000", "");
    }
}
