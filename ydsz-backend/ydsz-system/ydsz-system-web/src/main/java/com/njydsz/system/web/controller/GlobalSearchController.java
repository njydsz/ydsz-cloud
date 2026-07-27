package com.njydsz.system.web.controller;

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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 全局搜索聚合 Controller
 * <p>
 * 提供跨模块统一搜索入口，聚合 project/user/config/wiki 全部类型的搜索结果。
 *
 * @author ydsz-team
 * @since 1.3.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
@Tag(name = "全局搜索", description = "跨模块统一全文搜索")
public class GlobalSearchController {

    private final UnifiedSearchService unifiedSearchService;

    @GetMapping
    @Operation(summary = "全局搜索", description = "跨所有模块的统一搜索")
    @Audit(action = AuditAction.QUERY, module = "SYSTEM", content = "全局搜索")
    @AuthApiPermission(apiCodes = PermissionCodes.SYSTEM_SEARCH)
    public BaseResponse<SearchResponse> search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            @RequestHeader(value = "X-User-Roles", required = false) String rolesHeader,
            @RequestHeader(value = "X-User-Dept", required = false) String deptId,
            @RequestHeader(value = "X-User-Admin", required = false) String adminHeader,
            @RequestParam(value = "types", required = false) String typesParam) {

        SearchRequest.SearchRequestBuilder builder = SearchRequest.builder()
                .keyword(keyword)
                .page(page)
                .pageSize(pageSize)
                .userId(userId)
                .tenantId(tenantId)
                .roles(rolesHeader != null ? Arrays.asList(rolesHeader.split(",")) : List.of())
                .deptId(deptId)
                .admin("true".equalsIgnoreCase(adminHeader))
                .highlight(true)
                .fuzzy(true);

        if (typesParam != null && !typesParam.isBlank()) {
            builder.types(Arrays.asList(typesParam.split(",")));
        }

        return BaseResponse.success(unifiedSearchService.search(builder.build()));
    }
}
