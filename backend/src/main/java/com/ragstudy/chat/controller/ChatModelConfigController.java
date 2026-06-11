package com.ragstudy.chat.controller;

import com.ragstudy.auth.service.AuthService;
import com.ragstudy.auth.dal.dataobject.UserEntity;
import com.ragstudy.chat.controller.dto.ChatModelConfigDto;
import com.ragstudy.chat.controller.dto.ChatModelConfigRequest;
import com.ragstudy.chat.service.ChatModelConfigService;
import com.ragstudy.common.ApiResponse;
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
@RequestMapping("/api/chat-models")
public class ChatModelConfigController {

    private final ChatModelConfigService configService;
    private final AuthService authService;

    public ChatModelConfigController(ChatModelConfigService configService, AuthService authService) {
        this.configService = configService;
        this.authService = authService;
    }

    @GetMapping
    public ApiResponse<List<ChatModelConfigDto>> listConfigs(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        UserEntity user = authService.requireUser(authorizationHeader);
        return ApiResponse.ok(configService.listConfigs(user.getId()));
    }

    @PostMapping
    public ApiResponse<ChatModelConfigDto> createConfig(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody ChatModelConfigRequest request
    ) {
        UserEntity user = authService.requireUser(authorizationHeader);
        return ApiResponse.ok(configService.createConfig(user.getId(), request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ChatModelConfigDto> updateConfig(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String id,
            @Valid @RequestBody ChatModelConfigRequest request
    ) {
        UserEntity user = authService.requireUser(authorizationHeader);
        return ApiResponse.ok(configService.updateConfig(user.getId(), id, request));
    }

    @PatchMapping("/{id}/default")
    public ApiResponse<ChatModelConfigDto> setDefaultConfig(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String id
    ) {
        UserEntity user = authService.requireUser(authorizationHeader);
        return ApiResponse.ok(configService.setDefaultConfig(user.getId(), id));
    }
}
