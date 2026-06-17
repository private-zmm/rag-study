package com.ragstudy.note.service;

import com.ragstudy.note.controller.dto.NoteDto;
import com.ragstudy.note.controller.dto.NoteExportRequest;
import com.ragstudy.note.controller.dto.NoteImportRequest;
import com.ragstudy.note.controller.dto.NoteRenameRequest;
import com.ragstudy.note.controller.dto.NoteSaveRequest;
import com.ragstudy.note.convert.NoteConvert;
import com.ragstudy.note.dal.dataobject.NoteEntity;
import com.ragstudy.note.dal.repository.NoteRepository;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class NoteService {

    private static final DateTimeFormatter EXPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

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

    @Transactional(readOnly = true)
    public NoteExportFile exportNotes(String userId, NoteExportRequest request) {
        List<NoteEntity> exportNotes = resolveExportNotes(userId, request);

        if (exportNotes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "没有可导出的笔记");
        }

        String fileName = buildExportFileName(request, exportNotes);

        try {
            return new NoteExportFile(fileName, zipNotes(exportNotes));
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "笔记导出失败", exception);
        }
    }

    private List<NoteEntity> resolveExportNotes(String userId, NoteExportRequest request) {
        if (request != null && request.noteIds() != null && !request.noteIds().isEmpty()) {
            List<NoteEntity> ownedNotes = noteRepository.findAllByIdInAndUserId(request.noteIds(), userId);
            Set<String> ownedNoteIds = new HashSet<>();

            ownedNotes.forEach(note -> ownedNoteIds.add(note.getId()));

            if (ownedNoteIds.size() != new HashSet<>(request.noteIds()).size()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "部分笔记不存在");
            }

            return sortNotesForExport(ownedNotes);
        }

        String folderPath = normalizeFolderPath(request == null ? "" : request.folderPath());
        List<NoteEntity> userNotes = noteRepository.findAllByUserIdOrderByUpdatedAtDesc(userId);

        if (!folderPath.isBlank()) {
            String folderPrefix = folderPath + "/";

            userNotes = userNotes.stream()
                    .filter(note -> normalizeNotePath(note.getTitle()).startsWith(folderPrefix))
                    .toList();
        }

        return sortNotesForExport(userNotes);
    }

    private List<NoteEntity> sortNotesForExport(List<NoteEntity> notes) {
        return notes.stream()
                .sorted(Comparator.comparing(note -> normalizeNotePath(note.getTitle()), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private byte[] zipNotes(List<NoteEntity> notes) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Set<String> usedEntryNames = new HashSet<>();

        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
            for (NoteEntity note : notes) {
                String entryName = uniqueEntryName(buildNoteEntryName(note), usedEntryNames);
                ZipEntry entry = new ZipEntry(entryName);
                entry.setTime(System.currentTimeMillis());

                zipOutputStream.putNextEntry(entry);
                zipOutputStream.write(sanitizeText(note.getContent()).getBytes(StandardCharsets.UTF_8));
                zipOutputStream.closeEntry();
            }
        }

        return outputStream.toByteArray();
    }

    private String buildExportFileName(NoteExportRequest request, List<NoteEntity> notes) {
        String baseName = "notes";

        if (request != null && request.noteIds() != null && request.noteIds().size() == 1 && notes.size() == 1) {
            baseName = lastPathPart(notes.get(0).getTitle());
        } else if (request != null && !normalizeFolderPath(request.folderPath()).isBlank()) {
            baseName = lastPathPart(request.folderPath());
        }

        return sanitizeFileName(baseName) + "-" + LocalDateTime.now().format(EXPORT_TIME_FORMATTER) + ".zip";
    }

    private String buildNoteEntryName(NoteEntity note) {
        List<String> pathParts = splitPath(normalizeNotePath(note.getTitle()));

        if (pathParts.isEmpty()) {
            pathParts = List.of("未命名笔记");
        }

        int lastIndex = pathParts.size() - 1;
        List<String> entryParts = new ArrayList<>(pathParts);
        entryParts.set(lastIndex, ensureMarkdownExtension(entryParts.get(lastIndex)));

        return String.join("/", entryParts);
    }

    private String uniqueEntryName(String entryName, Set<String> usedEntryNames) {
        if (usedEntryNames.add(entryName)) {
            return entryName;
        }

        int dotIndex = entryName.lastIndexOf('.');
        String baseName = dotIndex > 0 ? entryName.substring(0, dotIndex) : entryName;
        String extension = dotIndex > 0 ? entryName.substring(dotIndex) : "";
        int index = 2;

        while (true) {
            String nextEntryName = baseName + " (" + index + ")" + extension;

            if (usedEntryNames.add(nextEntryName)) {
                return nextEntryName;
            }

            index += 1;
        }
    }

    private String normalizeFolderPath(String path) {
        return normalizeNotePath(path).replaceAll("/$", "");
    }

    private String normalizeNotePath(String path) {
        return splitPath(path).isEmpty() ? "" : String.join("/", splitPath(path));
    }

    private List<String> splitPath(String path) {
        if (path == null) {
            return List.of();
        }

        return java.util.Arrays.stream(path.replace("\\", "/").split("/"))
                .map(String::trim)
                .filter(part -> !part.isBlank())
                .filter(part -> !".".equals(part) && !"..".equals(part))
                .map(this::sanitizeFileName)
                .filter(part -> !part.isBlank())
                .toList();
    }

    private String lastPathPart(String path) {
        List<String> pathParts = splitPath(path);

        if (pathParts.isEmpty()) {
            return "notes";
        }

        return pathParts.get(pathParts.size() - 1);
    }

    private String ensureMarkdownExtension(String fileName) {
        String lowerFileName = fileName.toLowerCase();

        if (lowerFileName.endsWith(".md") || lowerFileName.endsWith(".markdown") || lowerFileName.endsWith(".mdx")) {
            return fileName;
        }

        return fileName + ".md";
    }

    private String sanitizeFileName(String value) {
        String sanitizedValue = sanitizeText(value == null ? "" : value)
                .replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_")
                .trim();

        return sanitizedValue.isBlank() ? "notes" : sanitizedValue;
    }

    private NoteEntity requireOwnedNote(String userId, String id) {
        return noteRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "笔记不存在"));
    }

    private String sanitizeText(String value) {
        return value.replace("\u0000", "");
    }

    public record NoteExportFile(String fileName, byte[] content) {

        public HttpHeaders headers() {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentDisposition(ContentDisposition.attachment()
                    .filename(fileName, StandardCharsets.UTF_8)
                    .build());
            headers.setContentLength(content.length);
            return headers;
        }
    }
}
