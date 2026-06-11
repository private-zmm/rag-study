package com.ragstudy.knowledge.controller;

import com.ragstudy.auth.dal.dataobject.UserEntity;
import com.ragstudy.auth.service.AuthService;
import com.ragstudy.common.ApiResponse;
import com.ragstudy.common.PageResult;
import com.ragstudy.knowledge.controller.dto.KnowledgeBaseDto;
import com.ragstudy.knowledge.controller.dto.KnowledgeBaseRequest;
import com.ragstudy.knowledge.controller.dto.KnowledgeDocumentDto;
import com.ragstudy.knowledge.controller.dto.KnowledgeDocumentRequest;
import com.ragstudy.knowledge.controller.dto.KnowledgeIndexResultDto;
import com.ragstudy.knowledge.controller.dto.KnowledgeSearchRequest;
import com.ragstudy.knowledge.controller.dto.KnowledgeSearchResultDto;
import com.ragstudy.knowledge.service.KnowledgeBaseService;
import com.ragstudy.knowledge.service.KnowledgeDocumentService;
import com.ragstudy.knowledge.service.KnowledgeVectorService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/knowledge-bases")
public class KnowledgeController {

    private final KnowledgeBaseService knowledgeBaseService;
    private final KnowledgeDocumentService knowledgeDocumentService;
    private final KnowledgeVectorService knowledgeVectorService;
    private final AuthService authService;

    public KnowledgeController(
            KnowledgeBaseService knowledgeBaseService,
            KnowledgeDocumentService knowledgeDocumentService,
            KnowledgeVectorService knowledgeVectorService,
            AuthService authService
    ) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.knowledgeDocumentService = knowledgeDocumentService;
        this.knowledgeVectorService = knowledgeVectorService;
        this.authService = authService;
    }

    @GetMapping
    public ApiResponse<List<KnowledgeBaseDto>> listKnowledgeBases(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        UserEntity user = authService.requireUser(authorizationHeader);
        return ApiResponse.ok(knowledgeBaseService.listKnowledgeBases(user.getId()));
    }

    @PostMapping
    public ApiResponse<KnowledgeBaseDto> createKnowledgeBase(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody KnowledgeBaseRequest request
    ) {
        UserEntity user = authService.requireUser(authorizationHeader);
        return ApiResponse.ok(knowledgeBaseService.createKnowledgeBase(user.getId(), request));
    }

    @PutMapping("/{id}")
    public ApiResponse<KnowledgeBaseDto> updateKnowledgeBase(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String id,
            @Valid @RequestBody KnowledgeBaseRequest request
    ) {
        UserEntity user = authService.requireUser(authorizationHeader);
        return ApiResponse.ok(knowledgeBaseService.updateKnowledgeBase(user.getId(), id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteKnowledgeBase(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String id
    ) {
        UserEntity user = authService.requireUser(authorizationHeader);
        knowledgeBaseService.deleteKnowledgeBase(user.getId(), id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{knowledgeBaseId}/documents")
    public ApiResponse<PageResult<KnowledgeDocumentDto>> listDocuments(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String knowledgeBaseId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        UserEntity user = authService.requireUser(authorizationHeader);
        return ApiResponse.ok(knowledgeDocumentService.listDocuments(user.getId(), knowledgeBaseId, page, pageSize));
    }

    @GetMapping("/{knowledgeBaseId}/documents/{documentId}")
    public ApiResponse<KnowledgeDocumentDto> getDocument(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String knowledgeBaseId,
            @PathVariable String documentId
    ) {
        UserEntity user = authService.requireUser(authorizationHeader);
        return ApiResponse.ok(knowledgeDocumentService.getDocument(user.getId(), knowledgeBaseId, documentId));
    }

    @PostMapping("/{knowledgeBaseId}/documents")
    public ApiResponse<KnowledgeDocumentDto> createDocument(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String knowledgeBaseId,
            @Valid @RequestBody KnowledgeDocumentRequest request
    ) {
        UserEntity user = authService.requireUser(authorizationHeader);
        return ApiResponse.ok(knowledgeDocumentService.createDocument(user.getId(), knowledgeBaseId, request));
    }

    @PostMapping("/{knowledgeBaseId}/documents/upload")
    public ApiResponse<KnowledgeDocumentDto> uploadDocument(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String knowledgeBaseId,
            @RequestPart("file") MultipartFile file
    ) {
        UserEntity user = authService.requireUser(authorizationHeader);
        return ApiResponse.ok(knowledgeDocumentService.uploadDocument(user.getId(), knowledgeBaseId, file));
    }

    @PutMapping("/{knowledgeBaseId}/documents/{documentId}")
    public ApiResponse<KnowledgeDocumentDto> updateDocument(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String knowledgeBaseId,
            @PathVariable String documentId,
            @Valid @RequestBody KnowledgeDocumentRequest request
    ) {
        UserEntity user = authService.requireUser(authorizationHeader);
        return ApiResponse.ok(knowledgeDocumentService.updateDocument(user.getId(), knowledgeBaseId, documentId, request));
    }

    @DeleteMapping("/{knowledgeBaseId}/documents/{documentId}")
    public ApiResponse<Void> deleteDocument(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String knowledgeBaseId,
            @PathVariable String documentId
    ) {
        UserEntity user = authService.requireUser(authorizationHeader);
        knowledgeDocumentService.deleteDocument(user.getId(), knowledgeBaseId, documentId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{knowledgeBaseId}/index/rebuild")
    public ApiResponse<KnowledgeIndexResultDto> rebuildIndex(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String knowledgeBaseId
    ) {
        UserEntity user = authService.requireUser(authorizationHeader);
        return ApiResponse.ok(knowledgeVectorService.rebuildIndex(user.getId(), knowledgeBaseId));
    }

    @PostMapping("/{knowledgeBaseId}/documents/{documentId}/index/rebuild")
    public ApiResponse<KnowledgeIndexResultDto> rebuildDocumentIndex(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String knowledgeBaseId,
            @PathVariable String documentId
    ) {
        UserEntity user = authService.requireUser(authorizationHeader);
        return ApiResponse.ok(knowledgeVectorService.rebuildDocumentIndex(user.getId(), knowledgeBaseId, documentId));
    }

    @PostMapping("/{knowledgeBaseId}/search")
    public ApiResponse<List<KnowledgeSearchResultDto>> search(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String knowledgeBaseId,
            @Valid @RequestBody KnowledgeSearchRequest request
    ) {
        UserEntity user = authService.requireUser(authorizationHeader);
        int limit = request.limit() == null ? 5 : request.limit();
        return ApiResponse.ok(knowledgeVectorService.search(user.getId(), knowledgeBaseId, request.query(), limit));
    }
}
