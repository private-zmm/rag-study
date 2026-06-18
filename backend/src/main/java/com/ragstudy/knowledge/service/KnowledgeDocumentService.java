package com.ragstudy.knowledge.service;

import com.ragstudy.common.PageResult;
import com.ragstudy.knowledge.controller.dto.KnowledgeDocumentDto;
import com.ragstudy.knowledge.controller.dto.KnowledgeDocumentRequest;
import com.ragstudy.knowledge.convert.KnowledgeDocumentConvert;
import com.ragstudy.knowledge.dal.dataobject.KnowledgeBaseEntity;
import com.ragstudy.knowledge.dal.dataobject.KnowledgeDocumentEntity;
import com.ragstudy.knowledge.dal.repository.KnowledgeBaseRepository;
import com.ragstudy.knowledge.dal.repository.KnowledgeDocumentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class KnowledgeDocumentService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeChunkService chunkService;
    private final KnowledgeDocumentParseService parseService;
    private final KnowledgeIndexService indexService;

    public KnowledgeDocumentService(
            KnowledgeBaseRepository knowledgeBaseRepository,
            KnowledgeDocumentRepository documentRepository,
            KnowledgeChunkService chunkService,
            KnowledgeDocumentParseService parseService,
            KnowledgeIndexService indexService
    ) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.documentRepository = documentRepository;
        this.chunkService = chunkService;
        this.parseService = parseService;
        this.indexService = indexService;
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
                        chunkService.countDocumentChunks(document.getId(), userId)
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
        KnowledgeDocumentEntity document = createParsedDocument(
                knowledgeBaseId,
                userId,
                request.title().trim(),
                "manual",
                null,
                null,
                "text/markdown",
                null,
                request.rawContent().trim()
        );

        KnowledgeDocumentEntity savedDocument = documentRepository.save(document);
        chunkService.rebuildChunks(savedDocument);
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
            savedDocument = documentRepository.save(createParsedDocument(
                    knowledgeBaseId,
                    userId,
                    title.trim(),
                    "note",
                    noteId,
                    null,
                    "text/markdown",
                    null,
                    rawContent.trim()
            ));
        } else {
            document.update(title.trim(), rawContent.trim());
            savedDocument = documentRepository.save(document);
        }

        chunkService.rebuildChunks(savedDocument);
        refreshKnowledgeBaseStats(knowledgeBase);
        return savedDocument;
    }

    @Transactional(readOnly = true)
    public boolean isNoteDocumentContentUnchanged(String userId, String knowledgeBaseId, String noteId, String contentHash) {
        return documentRepository
                .findByKnowledgeBaseIdAndUserIdAndSourceTypeAndSourceId(knowledgeBaseId, userId, "note", noteId)
                .map(document -> contentHash != null && contentHash.equals(document.getContentHash()))
                .orElse(false);
    }

    @Transactional
    public KnowledgeDocumentEntity syncNoteDocumentIfChanged(
            String userId,
            String knowledgeBaseId,
            String noteId,
            String title,
            String rawContent,
            String contentHash
    ) {
        KnowledgeDocumentEntity document = syncNoteDocument(userId, knowledgeBaseId, noteId, title, rawContent);
        document.setContentHash(contentHash);
        return documentRepository.save(document);
    }

    @Transactional
    public KnowledgeDocumentDto saveWebDocument(String userId, String knowledgeBaseId, String url, String title, String rawContent) {
        KnowledgeBaseEntity knowledgeBase = requireOwnedKnowledgeBase(userId, knowledgeBaseId);
        KnowledgeDocumentEntity document = createParsedDocument(
                knowledgeBaseId,
                userId,
                title.trim(),
                "web",
                url,
                null,
                "text/markdown",
                url,
                rawContent.trim()
        );

        KnowledgeDocumentEntity savedDocument = documentRepository.save(document);
        chunkService.rebuildChunks(savedDocument);
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
        KnowledgeDocumentParseService.ParsedUpload parsedUpload =
                parseService.uploadAndParse(knowledgeBaseId, documentId, file);
        LocalDateTime now = LocalDateTime.now();

        KnowledgeDocumentEntity document = new KnowledgeDocumentEntity(
                documentId,
                knowledgeBaseId,
                userId,
                parsedUpload.fileName(),
                "upload",
                null,
                parsedUpload.fileName(),
                parsedUpload.mimeType(),
                parsedUpload.storagePath(),
                parsedUpload.rawContent(),
                "parsed",
                "pending",
                now,
                now
        );

        KnowledgeDocumentEntity savedDocument = documentRepository.save(document);
        chunkService.rebuildChunks(savedDocument);
        refreshKnowledgeBaseStats(knowledgeBase);
        return toDto(savedDocument);
    }

    @Transactional
    public KnowledgeDocumentDto updateDocument(String userId, String knowledgeBaseId, String documentId, KnowledgeDocumentRequest request) {
        KnowledgeBaseEntity knowledgeBase = requireOwnedKnowledgeBase(userId, knowledgeBaseId);
        KnowledgeDocumentEntity document = requireOwnedDocument(userId, knowledgeBaseId, documentId);
        document.update(request.title().trim(), request.rawContent().trim());
        KnowledgeDocumentEntity savedDocument = documentRepository.save(document);
        chunkService.rebuildChunks(savedDocument);
        refreshKnowledgeBaseStats(knowledgeBase);
        return toDto(savedDocument);
    }

    @Transactional
    public void deleteDocument(String userId, String knowledgeBaseId, String documentId) {
        KnowledgeBaseEntity knowledgeBase = requireOwnedKnowledgeBase(userId, knowledgeBaseId);
        KnowledgeDocumentEntity document = requireOwnedDocument(userId, knowledgeBaseId, documentId);
        deleteDocumentResources(document);
        chunkService.deleteDocumentChunks(document.getId(), userId);
        documentRepository.delete(document);
        refreshKnowledgeBaseStats(knowledgeBase);
    }

    @Transactional
    public void deleteDocuments(String userId, String knowledgeBaseId, List<String> documentIds) {
        KnowledgeBaseEntity knowledgeBase = requireOwnedKnowledgeBase(userId, knowledgeBaseId);

        for (String documentId : documentIds.stream().distinct().toList()) {
            KnowledgeDocumentEntity document = requireOwnedDocument(userId, knowledgeBaseId, documentId);
            deleteDocumentResources(document);
            chunkService.deleteDocumentChunks(document.getId(), userId);
            documentRepository.delete(document);
        }

        refreshKnowledgeBaseStats(knowledgeBase);
    }

    private KnowledgeDocumentEntity createParsedDocument(
            String knowledgeBaseId,
            String userId,
            String title,
            String sourceType,
            String sourceId,
            String fileName,
            String mimeType,
            String storagePath,
            String rawContent
    ) {
        LocalDateTime now = LocalDateTime.now();
        return new KnowledgeDocumentEntity(
                UUID.randomUUID().toString(),
                knowledgeBaseId,
                userId,
                title,
                sourceType,
                sourceId,
                fileName,
                mimeType,
                storagePath,
                rawContent,
                "parsed",
                "pending",
                now,
                now
        );
    }

    private KnowledgeBaseEntity requireOwnedKnowledgeBase(String userId, String knowledgeBaseId) {
        return knowledgeBaseRepository.findByIdAndUserId(knowledgeBaseId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "知识库不存在"));
    }

    private KnowledgeDocumentEntity requireOwnedDocument(String userId, String knowledgeBaseId, String documentId) {
        return documentRepository.findByIdAndKnowledgeBaseIdAndUserId(documentId, knowledgeBaseId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "文档不存在"));
    }

    private void deleteDocumentResources(KnowledgeDocumentEntity document) {
        indexService.deleteDocumentVectors(document.getUserId(), document.getKnowledgeBaseId(), document.getId());

        if (StringUtils.hasText(document.getStoragePath())) {
            parseService.deleteStoredDocument(document.getStoragePath());
        }
    }

    private void refreshKnowledgeBaseStats(KnowledgeBaseEntity knowledgeBase) {
        long documentCount = documentRepository.countByKnowledgeBaseIdAndUserId(knowledgeBase.getId(), knowledgeBase.getUserId());
        long chunkCount = chunkService.countKnowledgeBaseChunks(knowledgeBase.getId(), knowledgeBase.getUserId());
        String vectorStatus = documentCount == 0 ? "empty" : "indexing";
        knowledgeBase.updateStats(Math.toIntExact(documentCount), Math.toIntExact(chunkCount), vectorStatus);
        knowledgeBaseRepository.save(knowledgeBase);
    }

    private KnowledgeDocumentDto toDto(KnowledgeDocumentEntity document) {
        long chunkCount = chunkService.countDocumentChunks(document.getId(), document.getUserId());
        return KnowledgeDocumentConvert.toDto(document, chunkCount);
    }
}
