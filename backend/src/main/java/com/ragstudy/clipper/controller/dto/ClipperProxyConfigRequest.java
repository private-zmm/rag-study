package com.ragstudy.clipper.controller.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ClipperProxyConfigRequest(
        @NotBlank String protocol,
        @NotBlank String host,
        @NotNull @Min(1) @Max(65535) Integer port,
        String username,
        String password
) {
}