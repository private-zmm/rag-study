package com.ragstudy.clipper.controller.dto;

public record ClipperProxyConfigDto(
        String protocol,
        String host,
        Integer port,
        String username,
        boolean passwordConfigured
) {
}