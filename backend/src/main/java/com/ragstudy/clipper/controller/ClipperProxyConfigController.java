package com.ragstudy.clipper.controller;

import com.ragstudy.auth.service.AuthService;
import com.ragstudy.clipper.controller.dto.ClipperProxyConfigDto;
import com.ragstudy.clipper.controller.dto.ClipperProxyConfigRequest;
import com.ragstudy.clipper.service.ClipperProxyConfigService;
import com.ragstudy.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/clipper/proxy-config")
public class ClipperProxyConfigController {

    private final ClipperProxyConfigService proxyConfigService;
    private final AuthService authService;

    public ClipperProxyConfigController(ClipperProxyConfigService proxyConfigService, AuthService authService) {
        this.proxyConfigService = proxyConfigService;
        this.authService = authService;
    }

    @GetMapping
    public ApiResponse<ClipperProxyConfigDto> getConfig(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        authService.requireUser(authorizationHeader);
        return ApiResponse.ok(proxyConfigService.getConfig());
    }

    @PatchMapping
    public ApiResponse<ClipperProxyConfigDto> saveConfig(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody ClipperProxyConfigRequest request
    ) {
        authService.requireUser(authorizationHeader);
        return ApiResponse.ok(proxyConfigService.saveConfig(request));
    }

    @PostMapping("/test")
    public ApiResponse<Void> testConfig(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody ClipperProxyConfigRequest request
    ) {
        authService.requireUser(authorizationHeader);
        proxyConfigService.testConfig(request);
        return ApiResponse.ok(null);
    }
}