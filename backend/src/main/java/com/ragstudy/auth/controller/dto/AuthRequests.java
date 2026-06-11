package com.ragstudy.auth.controller.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthRequests {

    private AuthRequests() {
    }

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 32) String username,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 6, max = 72) String password,
            String nickname
    ) {
    }

    public record LoginRequest(
            @NotBlank String account,
            @NotBlank String password
    ) {
    }
}
