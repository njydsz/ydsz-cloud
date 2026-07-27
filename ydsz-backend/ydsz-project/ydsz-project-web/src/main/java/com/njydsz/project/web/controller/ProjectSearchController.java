package com.njydsz.project.web.controller;

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
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.SearchResponseVO;

/**
 * 项目搜索 Controller
 *
 * <p>基于统一搜索服务，提供项目立项数据的全文检索能力。
 *
 * @author ydsz-team
 * @since 1.3.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/project/search")
@RequiredArgsConstructor
@Tag(name = "项目搜索", description = "项目立项全文搜索")
public class ProjectSearchController {

    private final UnifiedSearchService unifiedSearchService;

    @GetMapping
    @Operation(summary = "搜索项目")
    public BaseResponse<SearchResponseVO> search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {

        SearchRequest request = SearchRequest.builder()
                .keyword(keyword)
                .types(List.of("project"))
                .page(page)
                .pageSize(pageSize)
                .userId(userId)
                .tenantId(tenantId)
                .highlight(true)
                .fuzzy(true)
                .build();

        return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(unifiedSearchService.search(request)));
    }

    @PostMapping("/rebuild")
    @Operation(summary = "重建项目索引")
    public BaseResponse<Void> rebuildIndex(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {

        unifiedSearchService.clearCache();
        log.info("[ProjectSearch] 索引缓存已清除, userId={}", userId);
        return BaseResponse.success();
    }
}
