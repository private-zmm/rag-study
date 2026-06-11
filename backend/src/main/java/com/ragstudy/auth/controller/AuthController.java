package com.ragstudy.auth.controller;

import com.ragstudy.auth.controller.dto.AuthResponse;
import com.ragstudy.auth.controller.dto.AuthRequests.LoginRequest;
import com.ragstudy.auth.controller.dto.AuthRequests.RegisterRequest;
import com.ragstudy.auth.controller.dto.UserDto;
import com.ragstudy.auth.convert.AuthConvert;
import com.ragstudy.auth.dal.dataobject.UserEntity;
import com.ragstudy.auth.service.AuthService;
import com.ragstudy.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ApiResponse<UserDto> me(@RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        UserEntity user = authService.requireUser(authorizationHeader);
        return ApiResponse.ok(AuthConvert.toDto(user));
    }
}
