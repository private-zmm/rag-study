package com.ragstudy.backup.controller;

import com.ragstudy.auth.service.AuthService;
import com.ragstudy.backup.controller.dto.BackupConfigDto;
import com.ragstudy.backup.controller.dto.BackupConfigRequest;
import com.ragstudy.backup.controller.dto.BackupItemDto;
import com.ragstudy.backup.controller.dto.BackupResultDto;
import com.ragstudy.backup.service.BackupService;
import com.ragstudy.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/backups")
public class BackupController {

    private final BackupService backupService;
    private final AuthService authService;

    public BackupController(BackupService backupService, AuthService authService) {
        this.backupService = backupService;
        this.authService = authService;
    }

    @GetMapping("/config")
    public ApiResponse<BackupConfigDto> getConfig(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        authService.requireUser(authorizationHeader);
        return ApiResponse.ok(backupService.getConfig());
    }

    @PatchMapping("/config")
    public ApiResponse<BackupConfigDto> saveConfig(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody BackupConfigRequest request
    ) {
        authService.requireUser(authorizationHeader);
        return ApiResponse.ok(backupService.saveConfig(request));
    }

    @PostMapping("/config/test")
    public ApiResponse<Void> testConfig(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody BackupConfigRequest request
    ) {
        authService.requireUser(authorizationHeader);
        backupService.testConfig(request);
        return ApiResponse.ok(null);
    }

    @PostMapping("/database-tools/test")
    public ApiResponse<Void> testDatabaseTools(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody BackupConfigRequest request
    ) {
        authService.requireUser(authorizationHeader);
        backupService.testDatabaseTools(request);
        return ApiResponse.ok(null);
    }

    @GetMapping
    public ApiResponse<List<BackupItemDto>> listBackups(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        authService.requireUser(authorizationHeader);
        return ApiResponse.ok(backupService.listBackups());
    }

    @PostMapping
    public ApiResponse<BackupResultDto> createBackup(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        authService.requireUser(authorizationHeader);
        return ApiResponse.ok(backupService.createBackup("manual"));
    }

    @PostMapping("/restore")
    public ApiResponse<Void> restoreBackup(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestParam String objectName
    ) {
        authService.requireUser(authorizationHeader);
        backupService.restoreBackup(objectName);
        return ApiResponse.ok(null);
    }

    @DeleteMapping
    public ApiResponse<Void> deleteBackup(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestParam String objectName
    ) {
        authService.requireUser(authorizationHeader);
        backupService.deleteBackup(objectName);
        return ApiResponse.ok(null);
    }
}
