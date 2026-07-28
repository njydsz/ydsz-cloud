package com.njydsz.project.web.controller;

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
 * 项目搜索 Controller
 *
 * <p>基于统一搜索服务，提供项目立项数据的全文检索能力。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/project/search")
@RequiredArgsConstructor
@Tag(name = "项目搜索", description = "项目立项全文搜索")
public class ProjectSearchController {

    private final UnifiedSearchService unifiedSearchService;

    /**
     * 搜索项目
     *
     * <p>基于统一搜索服务进行全文检索，支持关键词模糊匹配、高亮显示、权限过滤。
     *
     * @param keyword      搜索关键词
     * @param page         当前页码（默认 1）
     * @param pageSize     每页条数（默认 20）
     * @param userId       用户 ID（从 Header 解析）
     * @param tenantId     租户 ID（从 Header 解析）
     * @param rolesHeader  用户角色列表（逗号分隔，从 Header 解析）
     * @param deptId       部门 ID（从 Header 解析）
     * @param adminHeader  是否管理员（从 Header 解析）
     * @return 搜索结果
     */
    @GetMapping
    @Operation(summary = "搜索项目")
    @Audit(action = AuditAction.QUERY, module = "PROJECT", content = "搜索项目")
    @AuthApiPermission(apiCodes = PermissionCodes.PROJECT_SEARCH)
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
                .types(List.of("project"))
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

    /**
     * 重建项目搜索索引
     *
     * <p>清除搜索缓存，触发索引重建。
     *
     * @param userId 用户 ID（从 Header 解析）
     * @return 操作结果
     */
    @PostMapping("/rebuild")
    @Operation(summary = "重建项目索引")
    @Audit(action = AuditAction.UPDATE, module = "PROJECT", content = "重建项目搜索索引")
    @AuthApiPermission(apiCodes = PermissionCodes.PROJECT_SEARCH_REBUILD)
    public BaseResponse<Void> rebuildIndex(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {

        unifiedSearchService.clearCache();
        log.info("[ProjectSearch] 索引缓存已清除, userId={}", userId);
        return BaseResponse.success();
    }
}
