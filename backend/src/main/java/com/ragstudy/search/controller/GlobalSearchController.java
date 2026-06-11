package com.ragstudy.search.controller;

import com.ragstudy.auth.dal.dataobject.UserEntity;
import com.ragstudy.auth.service.AuthService;
import com.ragstudy.common.ApiResponse;
import com.ragstudy.search.controller.dto.GlobalSearchResultDto;
import com.ragstudy.search.service.GlobalSearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/search")
public class GlobalSearchController {

    private final GlobalSearchService globalSearchService;
    private final AuthService authService;

    public GlobalSearchController(GlobalSearchService globalSearchService, AuthService authService) {
        this.globalSearchService = globalSearchService;
        this.authService = authService;
    }

    @GetMapping
    public ApiResponse<List<GlobalSearchResultDto>> search(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestParam String query,
            @RequestParam(defaultValue = "ALL") String scope,
            @RequestParam(defaultValue = "50") Integer limit
    ) {
        UserEntity user = authService.requireUser(authorizationHeader);
        return ApiResponse.ok(globalSearchService.search(user.getId(), query, scope, limit));
    }
}
