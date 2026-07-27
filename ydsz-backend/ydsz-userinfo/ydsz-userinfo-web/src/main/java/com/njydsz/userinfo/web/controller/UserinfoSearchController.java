package com.njydsz.userinfo.web.controller;

import java.util.Arrays;
import java.util.List;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.permission.PermissionCodes;
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
 * 用户搜索 Controller
 *
 * <p>基于统一搜索服务，提供用户数据的全文检索能力。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/userinfo/search")
@RequiredArgsConstructor
@Tag(name = "用户搜索", description = "用户全文搜索")
public class UserinfoSearchController {

    private final UnifiedSearchService unifiedSearchService;

    @GetMapping
    @Operation(summary = "搜索用户")
    @Audit(action = AuditAction.QUERY, module = "USERINFO", content = "搜索用户")
    @AuthApiPermission(apiCodes = PermissionCodes.USERINFO_SEARCH)
    public BaseResponse<SearchResponse> search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            @RequestHeader(value = "X-User-Roles", required = false) String rolesHeader,
            @RequestHeader(value = "X-User-Dept", required = false) String deptId,
            @RequestHeader(value = "X-User-Admin", required = false) String adminHeader) {

        SearchRequest request = SearchRequest.builder()
                .keyword(keyword)
                .types(List.of("user"))
                .page(page)
                .pageSize(pageSize)
                .userId(userId)
                .tenantId(tenantId)
                .roles(rolesHeader != null ? Arrays.asList(rolesHeader.split(",")) : List.of())
                .deptId(deptId)
                .admin("true".equalsIgnoreCase(adminHeader))
                .highlight(true)
                .fuzzy(true)
                .build();

        return BaseResponse.success(unifiedSearchService.search(request));
    }

    @PostMapping("/rebuild")
    @Operation(summary = "重建用户索引")
    @Audit(action = AuditAction.UPDATE, module = "USERINFO", content = "重建用户搜索索引")
    public BaseResponse<Void> rebuildIndex(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {

        unifiedSearchService.clearCache();
        log.info("[UserinfoSearch] 索引缓存已清除, userId={}", userId);
        return BaseResponse.success();
    }
}
