package com.ragstudy.note.service;

import com.ragstudy.knowledge.framework.MinioStorageService;
import com.ragstudy.note.controller.dto.NoteAssetUploadResponse;
import com.ragstudy.note.dal.dataobject.NoteAssetEntity;
import com.ragstudy.note.dal.repository.NoteRepository;
import com.ragstudy.note.dal.repository.NoteAssetRepository;
import io.minio.GetObjectResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class NoteAssetService {

    private static final Set<String> IMAGE_EXTENSIONS = Set.of(".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg", ".bmp");
    private static final String ASSET_OBJECT_PREFIX = "notes/assets/";

    private final MinioStorageService storageService;
    private final NoteAssetRepository noteAssetRepository;
    private final NoteRepository noteRepository;

    public NoteAssetService(
            MinioStorageService storageService,
            NoteAssetRepository noteAssetRepository,
            NoteRepository noteRepository
    ) {
        this.storageService = storageService;
        this.noteAssetRepository = noteAssetRepository;
        this.noteRepository = noteRepository;
    }

    @Transactional
    public NoteAssetUploadResponse uploadImage(String userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择要上传的图片");
        }

        String fileName = normalizeFileName(file.getOriginalFilename());

        if (!isImageFile(fileName, file.getContentType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持上传图片资源");
        }

        String objectName = ASSET_OBJECT_PREFIX + userId + "/" + UUID.randomUUID() + "/" + fileName;

        try {
            String contentType = StringUtils.hasText(file.getContentType()) ? file.getContentType() : "application/octet-stream";
            String storagePath = storageService.upload(objectName, file.getBytes(), contentType);
            noteAssetRepository.save(new NoteAssetEntity(
                    UUID.randomUUID().toString(),
                    userId,
                    null,
                    objectName,
                    fileName,
                    contentType,
                    file.getSize(),
                    LocalDateTime.now()
            ));
            String url = "/api/note-assets?objectName=" + URLEncoder.encode(objectName, StandardCharsets.UTF_8);
            return new NoteAssetUploadResponse(url, storagePath, objectName);
        } catch (IOException exception) {
            throw new IllegalStateException("读取图片内容失败", exception);
        }
    }

    @Transactional(readOnly = true)
    public GetObjectResponse openImage(String objectName) {
        if (!StringUtils.hasText(objectName) || !objectName.startsWith(ASSET_OBJECT_PREFIX) || objectName.contains("..")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "图片路径不合法");
        }

        return storageService.open(objectName);
    }

    @Transactional
    public void bindAssetsToNote(String userId, String noteId, List<String> objectNames) {
        if (objectNames == null || objectNames.isEmpty()) {
            return;
        }

        noteRepository.findByIdAndUserId(noteId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "笔记不存在"));

        List<NoteAssetEntity> assets = noteAssetRepository.findAllByUserIdAndObjectNameIn(userId, objectNames);

        assets.forEach(asset -> asset.bindToNote(noteId));
        noteAssetRepository.saveAll(assets);
    }

    @Transactional
    public void deleteAssetsForNote(String userId, String noteId) {
        List<NoteAssetEntity> assets = noteAssetRepository.findAllByUserIdAndNoteId(userId, noteId);

        assets.forEach(asset -> storageService.delete(asset.getObjectName()));
        noteAssetRepository.deleteAll(assets);
    }

    private boolean isImageFile(String fileName, String contentType) {
        if (StringUtils.hasText(contentType) && contentType.toLowerCase().startsWith("image/")) {
            return true;
        }

        String normalizedFileName = fileName.toLowerCase();
        return IMAGE_EXTENSIONS.stream().anyMatch(normalizedFileName::endsWith);
    }

    private String normalizeFileName(String originalFileName) {
        if (!StringUtils.hasText(originalFileName)) {
            return "image";
        }

        return originalFileName.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
