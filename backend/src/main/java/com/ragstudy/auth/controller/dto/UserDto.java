package com.ragstudy.auth.controller.dto;

public record UserDto(
        String id,
        String username,
        String email,
        String nickname
) {
}
