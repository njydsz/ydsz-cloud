package com.njydsz.system.web.controller;

import java.util.List;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.search.api.SearchRequest;
import com.njydsz.common.search.api.SearchResponse;
import com.njydsz.common.search.service.UnifiedSearchService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统配置搜索 Controller
 *
 * <p>基于统一搜索服务，提供系统配置的全文检索能力。
 *
 * @author ydsz-team
 * @since 1.3.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/system/search")
@RequiredArgsConstructor
@Tag(name = "系统搜索", description = "系统配置全文搜索")
public class SystemSearchController {

    private final UnifiedSearchService unifiedSearchService;

    @GetMapping
    @Operation(summary = "搜索系统配置")
    public BaseResponse<SearchResponse> search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {

        SearchRequest request = SearchRequest.builder()
                .keyword(keyword)
                .types(List.of("config"))
                .page(page)
                .pageSize(pageSize)
                .userId(userId)
                .tenantId(tenantId)
                .highlight(true)
                .fuzzy(true)
                .build();

        return BaseResponse.success(unifiedSearchService.search(request));
    }

    @PostMapping("/rebuild")
    @Operation(summary = "重建系统配置索引")
    public BaseResponse<Void> rebuildIndex(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {

        unifiedSearchService.clearCache();
        log.info("[SystemSearch] 索引缓存已清除, userId={}", userId);
        return BaseResponse.success();
    }
}
