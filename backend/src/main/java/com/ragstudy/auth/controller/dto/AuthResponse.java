package com.ragstudy.auth.controller.dto;

public record AuthResponse(
        String token,
        UserDto user
) {
}
