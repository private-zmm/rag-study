package com.ragstudy.note.controller;

import com.ragstudy.auth.dal.dataobject.UserEntity;
import com.ragstudy.auth.service.AuthService;
import com.ragstudy.common.ApiResponse;
import com.ragstudy.note.controller.dto.NoteFolderDto;
import com.ragstudy.note.controller.dto.NoteFolderRequest;
import com.ragstudy.note.service.NoteFolderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/note-folders")
public class NoteFolderController {

    private final NoteFolderService noteFolderService;
    private final AuthService authService;

    public NoteFolderController(NoteFolderService noteFolderService, AuthService authService) {
        this.noteFolderService = noteFolderService;
        this.authService = authService;
    }

    @GetMapping
    public ApiResponse<List<NoteFolderDto>> listFolders(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        UserEntity user = authService.requireUser(authorizationHeader);
        return ApiResponse.ok(noteFolderService.listFolders(user.getId()));
    }

    @PostMapping
    public ApiResponse<NoteFolderDto> createFolder(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody NoteFolderRequest request
    ) {
        UserEntity user = authService.requireUser(authorizationHeader);
        return ApiResponse.ok(noteFolderService.createFolder(user.getId(), request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteFolder(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String id
    ) {
        UserEntity user = authService.requireUser(authorizationHeader);
        noteFolderService.deleteFolder(user.getId(), id);
        return ApiResponse.ok(null);
    }
}
