package com.ragstudy.note.controller;

import com.ragstudy.auth.dal.dataobject.UserEntity;
import com.ragstudy.auth.service.AuthService;
import com.ragstudy.common.ApiResponse;
import com.ragstudy.note.controller.dto.NoteAssetBindRequest;
import com.ragstudy.note.controller.dto.NoteAssetUploadResponse;
import com.ragstudy.note.service.NoteAssetService;
import io.minio.GetObjectResponse;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLConnection;
import java.time.Duration;

@RestController
@RequestMapping("/api/note-assets")
public class NoteAssetController {

    private final NoteAssetService noteAssetService;
    private final AuthService authService;

    public NoteAssetController(NoteAssetService noteAssetService, AuthService authService) {
        this.noteAssetService = noteAssetService;
        this.authService = authService;
    }

    @PostMapping("/upload")
    public ApiResponse<NoteAssetUploadResponse> uploadAsset(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestPart("file") MultipartFile file
    ) {
        UserEntity user = authService.requireUser(authorizationHeader);
        return ApiResponse.ok(noteAssetService.uploadImage(user.getId(), file));
    }

    @PostMapping("/bind")
    public ApiResponse<Void> bindAssets(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody NoteAssetBindRequest request
    ) {
        UserEntity user = authService.requireUser(authorizationHeader);
        noteAssetService.bindAssetsToNote(user.getId(), request.noteId(), request.objectNames());
        return ApiResponse.ok(null);
    }

    @GetMapping
    public ResponseEntity<InputStreamResource> getAsset(@RequestParam String objectName) {
        GetObjectResponse object = noteAssetService.openImage(objectName);
        String contentType = URLConnection.guessContentTypeFromName(objectName);

        if (contentType == null) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .body(new InputStreamResource(object));
    }
}
