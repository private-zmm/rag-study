package com.ragstudy.knowledge.service;

import com.ragstudy.knowledge.framework.DocumentParser;
import com.ragstudy.knowledge.framework.MinioStorageService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class KnowledgeDocumentParseService {

    private final MinioStorageService storageService;
    private final DocumentParser documentParser;

    public KnowledgeDocumentParseService(MinioStorageService storageService, DocumentParser documentParser) {
        this.storageService = storageService;
        this.documentParser = documentParser;
    }

    public ParsedUpload uploadAndParse(String knowledgeBaseId, String documentId, MultipartFile file) {
        String fileName = normalizeFileName(file.getOriginalFilename());
        String mimeType = StringUtils.hasText(file.getContentType()) ? file.getContentType() : "application/octet-stream";
        String objectName = "knowledge-bases/" + knowledgeBaseId + "/documents/" + documentId + "/" + fileName;
        String rawContent = documentParser.parse(file, mimeType, fileName);
        String storagePath = storageService.upload(objectName, file);

        return new ParsedUpload(fileName, mimeType, storagePath, rawContent);
    }

    public void deleteStoredDocument(String storagePath) {
        if (!StringUtils.hasText(storagePath)) {
            return;
        }

        storageService.delete(toObjectName(storagePath));
    }

    private String normalizeFileName(String originalFileName) {
        if (!StringUtils.hasText(originalFileName)) {
            return "untitled";
        }

        return originalFileName.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private String toObjectName(String storagePath) {
        String prefix = storageService.bucketName() + "/";

        if (storagePath.startsWith(prefix)) {
            return storagePath.substring(prefix.length());
        }

        return storagePath;
    }

    public record ParsedUpload(String fileName, String mimeType, String storagePath, String rawContent) {
    }
}
