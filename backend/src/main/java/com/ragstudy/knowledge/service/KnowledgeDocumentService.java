package com.ragstudy.knowledge.service;

import com.ragstudy.common.PageResult;
import com.ragstudy.knowledge.controller.dto.KnowledgeDocumentDto;
import com.ragstudy.knowledge.controller.dto.KnowledgeDocumentRequest;
import com.ragstudy.knowledge.convert.KnowledgeDocumentConvert;
import com.ragstudy.knowledge.dal.dataobject.KnowledgeBaseEntity;
import com.ragstudy.knowledge.dal.dataobject.KnowledgeDocumentChunkEntity;
import com.ragstudy.knowledge.dal.dataobject.KnowledgeDocumentEntity;
import com.ragstudy.knowledge.dal.repository.KnowledgeBaseRepository;
import com.ragstudy.knowledge.dal.repository.KnowledgeDocumentChunkRepository;
import com.ragstudy.knowledge.dal.repository.KnowledgeDocumentRepository;
import com.ragstudy.knowledge.framework.MinioStorageService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class KnowledgeDocumentService {

    private static final int CHUNK_SIZE = 900;
    private static final int CHUNK_OVERLAP = 120;

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeDocumentChunkRepository chunkRepository;
    private final MinioStorageService storageService;

    public KnowledgeDocumentService(
            KnowledgeBaseRepository knowledgeBaseRepository,
            KnowledgeDocumentRepository documentRepository,
            KnowledgeDocumentChunkRepository chunkRepository,
            MinioStorageService storageService
    ) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.storageService = storageService;
    }

    @Transactional(readOnly = true)
    public PageResult<KnowledgeDocumentDto> listDocuments(String userId, String knowledgeBaseId, int page, int pageSize) {
        requireOwnedKnowledgeBase(userId, knowledgeBaseId);
        int safePage = Math.max(1, page);
        int safePageSize = Math.min(100, Math.max(1, pageSize));
        PageRequest pageRequest = PageRequest.of(
                safePage - 1,
                safePageSize,
                Sort.by(Sort.Direction.DESC, "updatedAt")
        );
        Page<KnowledgeDocumentEntity> documentPage =
                documentRepository.findAllByKnowledgeBaseIdAndUserId(knowledgeBaseId, userId, pageRequest);

        if (documentPage.isEmpty() && documentPage.getTotalElements() > 0 && safePage > documentPage.getTotalPages()) {
            safePage = documentPage.getTotalPages();
            pageRequest = PageRequest.of(
                    safePage - 1,
                    safePageSize,
                    Sort.by(Sort.Direction.DESC, "updatedAt")
            );
            documentPage = documentRepository.findAllByKnowledgeBaseIdAndUserId(knowledgeBaseId, userId, pageRequest);
        }

        List<KnowledgeDocumentDto> items = documentPage.getContent()
                .stream()
                .map(document -> KnowledgeDocumentConvert.toDto(
                        document,
                        chunkRepository.countByDocumentIdAndUserId(document.getId(), userId)
                ))
                .toList();

        return new PageResult<>(items, documentPage.getTotalElements(), safePage, safePageSize);
    }

    @Transactional(readOnly = true)
    public KnowledgeDocumentDto getDocument(String userId, String knowledgeBaseId, String documentId) {
        return toDto(requireOwnedDocument(userId, knowledgeBaseId, documentId));
    }

    @Transactional
    public KnowledgeDocumentDto createDocument(String userId, String knowledgeBaseId, KnowledgeDocumentRequest request) {
        KnowledgeBaseEntity knowledgeBase = requireOwnedKnowledgeBase(userId, knowledgeBaseId);
        LocalDateTime now = LocalDateTime.now();
        KnowledgeDocumentEntity document = new KnowledgeDocumentEntity(
                UUID.randomUUID().toString(),
                knowledgeBaseId,
                userId,
                request.title().trim(),
                "manual",
                null,
                null,
                "text/markdown",
                null,
                request.rawContent().trim(),
                "parsed",
                "pending",
                now,
                now
        );

        KnowledgeDocumentEntity savedDocument = documentRepository.save(document);
        rebuildChunks(savedDocument);
        refreshKnowledgeBaseStats(knowledgeBase);
        return toDto(savedDocument);
    }

    @Transactional
    public KnowledgeDocumentEntity syncNoteDocument(String userId, String knowledgeBaseId, String noteId, String title, String rawContent) {
        KnowledgeBaseEntity knowledgeBase = requireOwnedKnowledgeBase(userId, knowledgeBaseId);
        KnowledgeDocumentEntity document = documentRepository
                .findByKnowledgeBaseIdAndUserIdAndSourceTypeAndSourceId(knowledgeBaseId, userId, "note", noteId)
                .orElse(null);
        KnowledgeDocumentEntity savedDocument;

        if (document == null) {
            LocalDateTime now = LocalDateTime.now();
            savedDocument = documentRepository.save(new KnowledgeDocumentEntity(
                    UUID.randomUUID().toString(),
                    knowledgeBaseId,
                    userId,
                    title.trim(),
                    "note",
                    noteId,
                    null,
                    "text/markdown",
                    null,
                    rawContent.trim(),
                    "parsed",
                    "pending",
                    now,
                    now
            ));
        } else {
            document.update(title.trim(), rawContent.trim());
            savedDocument = documentRepository.save(document);
        }

        rebuildChunks(savedDocument);
        refreshKnowledgeBaseStats(knowledgeBase);
        return savedDocument;
    }

    @Transactional
    public KnowledgeDocumentDto saveWebDocument(String userId, String knowledgeBaseId, String url, String title, String rawContent) {
        KnowledgeBaseEntity knowledgeBase = requireOwnedKnowledgeBase(userId, knowledgeBaseId);
        LocalDateTime now = LocalDateTime.now();
        KnowledgeDocumentEntity document = new KnowledgeDocumentEntity(
                UUID.randomUUID().toString(),
                knowledgeBaseId,
                userId,
                title.trim(),
                "web",
                url,
                null,
                "text/markdown",
                url,
                rawContent.trim(),
                "parsed",
                "pending",
                now,
                now
        );

        KnowledgeDocumentEntity savedDocument = documentRepository.save(document);
        rebuildChunks(savedDocument);
        refreshKnowledgeBaseStats(knowledgeBase);
        return toDto(savedDocument);
    }

    @Transactional
    public KnowledgeDocumentDto uploadDocument(String userId, String knowledgeBaseId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择要上传的文件");
        }

        KnowledgeBaseEntity knowledgeBase = requireOwnedKnowledgeBase(userId, knowledgeBaseId);
        String documentId = UUID.randomUUID().toString();
        String fileName = normalizeFileName(file.getOriginalFilename());
        String mimeType = StringUtils.hasText(file.getContentType()) ? file.getContentType() : "application/octet-stream";
        String objectName = "knowledge-bases/" + knowledgeBaseId + "/documents/" + documentId + "/" + fileName;
        String storagePath = storageService.upload(objectName, file);
        String rawContent = readRawContent(file, mimeType, fileName);
        LocalDateTime now = LocalDateTime.now();

        KnowledgeDocumentEntity document = new KnowledgeDocumentEntity(
                documentId,
                knowledgeBaseId,
                userId,
                fileName,
                "upload",
                null,
                fileName,
                mimeType,
                storagePath,
                rawContent,
                "parsed",
                "pending",
                now,
                now
        );

        KnowledgeDocumentEntity savedDocument = documentRepository.save(document);
        rebuildChunks(savedDocument);
        refreshKnowledgeBaseStats(knowledgeBase);
        return toDto(savedDocument);
    }

    @Transactional
    public KnowledgeDocumentDto updateDocument(String userId, String knowledgeBaseId, String documentId, KnowledgeDocumentRequest request) {
        KnowledgeBaseEntity knowledgeBase = requireOwnedKnowledgeBase(userId, knowledgeBaseId);
        KnowledgeDocumentEntity document = requireOwnedDocument(userId, knowledgeBaseId, documentId);
        document.update(request.title().trim(), request.rawContent().trim());
        KnowledgeDocumentEntity savedDocument = documentRepository.save(document);
        rebuildChunks(savedDocument);
        refreshKnowledgeBaseStats(knowledgeBase);
        return toDto(savedDocument);
    }

    @Transactional
    public void deleteDocument(String userId, String knowledgeBaseId, String documentId) {
        KnowledgeBaseEntity knowledgeBase = requireOwnedKnowledgeBase(userId, knowledgeBaseId);
        KnowledgeDocumentEntity document = requireOwnedDocument(userId, knowledgeBaseId, documentId);
        chunkRepository.deleteAllByDocumentIdAndUserId(document.getId(), userId);
        documentRepository.delete(document);
        refreshKnowledgeBaseStats(knowledgeBase);
    }

    private KnowledgeBaseEntity requireOwnedKnowledgeBase(String userId, String knowledgeBaseId) {
        KnowledgeBaseEntity knowledgeBase = knowledgeBaseRepository.findById(knowledgeBaseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "知识库不存在"));

        if (!knowledgeBase.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "知识库不存在");
        }

        return knowledgeBase;
    }

    private KnowledgeDocumentEntity requireOwnedDocument(String userId, String knowledgeBaseId, String documentId) {
        KnowledgeDocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "文档不存在"));

        if (!document.getUserId().equals(userId) || !document.getKnowledgeBaseId().equals(knowledgeBaseId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文档不存在");
        }

        return document;
    }

    private void refreshKnowledgeBaseStats(KnowledgeBaseEntity knowledgeBase) {
        long documentCount = documentRepository.countByKnowledgeBaseIdAndUserId(knowledgeBase.getId(), knowledgeBase.getUserId());
        long chunkCount = chunkRepository.countByKnowledgeBaseIdAndUserId(knowledgeBase.getId(), knowledgeBase.getUserId());
        String vectorStatus = documentCount == 0 ? "empty" : "indexing";
        knowledgeBase.updateStats(Math.toIntExact(documentCount), Math.toIntExact(chunkCount), vectorStatus);
        knowledgeBaseRepository.save(knowledgeBase);
    }

    private KnowledgeDocumentDto toDto(KnowledgeDocumentEntity document) {
        long chunkCount = chunkRepository.countByDocumentIdAndUserId(document.getId(), document.getUserId());
        return KnowledgeDocumentConvert.toDto(document, chunkCount);
    }

    private void rebuildChunks(KnowledgeDocumentEntity document) {
        chunkRepository.deleteAllByDocumentIdAndUserId(document.getId(), document.getUserId());
        List<String> chunks = splitContent(document.getRawContent());

        for (int index = 0; index < chunks.size(); index += 1) {
            String chunk = chunks.get(index);
            chunkRepository.save(new KnowledgeDocumentChunkEntity(
                    UUID.randomUUID().toString(),
                    document.getId(),
                    document.getKnowledgeBaseId(),
                    document.getUserId(),
                    index,
                    chunk,
                    estimateTokenCount(chunk),
                    null,
                    LocalDateTime.now()
            ));
        }
    }

    private List<String> splitContent(String rawContent) {
        String normalizedContent = rawContent == null ? "" : rawContent.replaceAll("\\s+", " ").trim();

        if (!StringUtils.hasText(normalizedContent)) {
            return List.of();
        }

        if (normalizedContent.length() <= CHUNK_SIZE) {
            return List.of(normalizedContent);
        }

        List<String> chunks = new java.util.ArrayList<>();
        int start = 0;

        while (start < normalizedContent.length()) {
            int end = Math.min(start + CHUNK_SIZE, normalizedContent.length());
            int boundary = findChunkBoundary(normalizedContent, start, end);
            String chunk = normalizedContent.substring(start, boundary).trim();

            if (StringUtils.hasText(chunk)) {
                chunks.add(chunk);
            }

            if (boundary >= normalizedContent.length()) {
                break;
            }

            start = Math.max(boundary - CHUNK_OVERLAP, start + 1);
        }

        return chunks;
    }

    private int findChunkBoundary(String content, int start, int end) {
        if (end >= content.length()) {
            return content.length();
        }

        for (int index = end; index > start + CHUNK_SIZE / 2; index -= 1) {
            char currentChar = content.charAt(index - 1);

            if (currentChar == '。' || currentChar == '！' || currentChar == '？' || currentChar == '\n') {
                return index;
            }
        }

        return end;
    }

    private int estimateTokenCount(String content) {
        return Math.max(1, (int) Math.ceil(content.length() / 1.8));
    }

    private String normalizeFileName(String originalFileName) {
        if (!StringUtils.hasText(originalFileName)) {
            return "untitled";
        }

        return originalFileName.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private String readRawContent(MultipartFile file, String mimeType, String fileName) {
        if (isReadableTextFile(mimeType, fileName)) {
            try {
                return new String(file.getBytes(), StandardCharsets.UTF_8);
            } catch (IOException exception) {
                throw new IllegalStateException("读取上传文件内容失败", exception);
            }
        }

        return "文件已上传到 MinIO，文件名：" + fileName + "。当前版本暂未解析该文件类型。";
    }

    private boolean isReadableTextFile(String mimeType, String fileName) {
        String normalizedMimeType = mimeType.toLowerCase();
        String normalizedFileName = fileName.toLowerCase();

        return normalizedMimeType.startsWith("text/")
                || normalizedMimeType.contains("json")
                || normalizedFileName.endsWith(".md")
                || normalizedFileName.endsWith(".markdown")
                || normalizedFileName.endsWith(".txt")
                || normalizedFileName.endsWith(".json")
                || normalizedFileName.endsWith(".csv");
    }
}
