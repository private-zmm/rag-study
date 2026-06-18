package com.ragstudy.clipper.controller;

import com.ragstudy.auth.dal.dataobject.UserEntity;
import com.ragstudy.auth.service.AuthService;
import com.ragstudy.clipper.controller.dto.ClipperPreviewDto;
import com.ragstudy.clipper.controller.dto.ClipperPreviewRequest;
import com.ragstudy.clipper.controller.dto.ClipperRequest;
import com.ragstudy.clipper.controller.dto.ClipperResultDto;
import com.ragstudy.clipper.controller.dto.WebClipDto;
import com.ragstudy.clipper.service.ClipperService;
import com.ragstudy.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/clipper")
public class ClipperController {

    private final ClipperService clipperService;
    private final AuthService authService;

    public ClipperController(ClipperService clipperService, AuthService authService) {
        this.clipperService = clipperService;
        this.authService = authService;
    }

    @PostMapping("/preview")
    public ApiResponse<ClipperPreviewDto> preview(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody ClipperPreviewRequest request
    ) {
        UserEntity user = authService.requireUser(authorizationHeader);
        return ApiResponse.ok(clipperService.preview(user.getId(), request));
    }

    @GetMapping("/history")
    public ApiResponse<List<WebClipDto>> listHistory(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestParam(defaultValue = "20") int limit
    ) {
        UserEntity user = authService.requireUser(authorizationHeader);
        return ApiResponse.ok(clipperService.listHistory(user.getId(), limit));
    }

    @GetMapping("/history/{clipId}")
    public ApiResponse<WebClipDto> getHistory(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String clipId
    ) {
        UserEntity user = authService.requireUser(authorizationHeader);
        return ApiResponse.ok(clipperService.getHistory(user.getId(), clipId));
    }

    @DeleteMapping("/history/{clipId}")
    public ApiResponse<Void> deleteHistory(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String clipId
    ) {
        UserEntity user = authService.requireUser(authorizationHeader);
        clipperService.deleteHistory(user.getId(), clipId);
        return ApiResponse.ok(null);
    }

    @PostMapping
    public ApiResponse<ClipperResultDto> clip(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody ClipperRequest request
    ) {
        UserEntity user = authService.requireUser(authorizationHeader);
        return ApiResponse.ok(clipperService.clip(user.getId(), request));
    }
}
