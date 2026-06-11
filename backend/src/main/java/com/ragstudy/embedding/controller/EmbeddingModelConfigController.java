package com.ragstudy.embedding.controller;

import com.ragstudy.auth.dal.dataobject.UserEntity;
import com.ragstudy.auth.service.AuthService;
import com.ragstudy.common.ApiResponse;
import com.ragstudy.embedding.controller.dto.EmbeddingModelConfigDto;
import com.ragstudy.embedding.controller.dto.EmbeddingModelConfigRequest;
import com.ragstudy.embedding.service.EmbeddingModelConfigService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/embedding-models")
public class EmbeddingModelConfigController {

    private final EmbeddingModelConfigService configService;
    private final AuthService authService;

    public EmbeddingModelConfigController(EmbeddingModelConfigService configService, AuthService authService) {
        this.configService = configService;
        this.authService = authService;
    }

    @GetMapping
    public ApiResponse<List<EmbeddingModelConfigDto>> listConfigs(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        UserEntity user = authService.requireUser(authorizationHeader);
        return ApiResponse.ok(configService.listConfigs(user.getId()));
    }

    @PostMapping
    public ApiResponse<EmbeddingModelConfigDto> createConfig(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody EmbeddingModelConfigRequest request
    ) {
        UserEntity user = authService.requireUser(authorizationHeader);
        return ApiResponse.ok(configService.createConfig(user.getId(), request));
    }

    @PutMapping("/{id}")
    public ApiResponse<EmbeddingModelConfigDto> updateConfig(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String id,
            @Valid @RequestBody EmbeddingModelConfigRequest request
    ) {
        UserEntity user = authService.requireUser(authorizationHeader);
        return ApiResponse.ok(configService.updateConfig(user.getId(), id, request));
    }

    @PatchMapping("/{id}/default")
    public ApiResponse<EmbeddingModelConfigDto> setDefaultConfig(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String id
    ) {
        UserEntity user = authService.requireUser(authorizationHeader);
        return ApiResponse.ok(configService.setDefaultConfig(user.getId(), id));
    }
}
