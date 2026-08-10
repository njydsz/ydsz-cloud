package com.njydsz.userinfo.web.controller;

import java.util.Arrays;
import java.util.List;

import com.njydsz.common.auth.constant.AuthHeaderConstants;
import com.njydsz.common.jdbc.constant.DataPermissionHeaderConstants;
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
 * <p>基于统一搜索服务（{@link UnifiedSearchService}），
 * 提供用户维度的全文检索能力，支持权限感知与多租户隔离。
 *
 * <p><b>接口路径：</b>{@code /api/v1/userinfo/search}
 *
 * <p><b>核心能力：</b>
 * <ul>
 *   <li>{@code GET /} — 用户全文检索（高亮 + 模糊匹配 + 权限过滤）</li>
 *   <li>{@code POST /rebuild} — 重建/失效用户搜索索引（运维用）</li>
 * </ul>
 *
 * <p><b>权限模型：</b>
 * <ul>
 *   <li>仅拥有 {@link PermissionCodes#USERINFO_SEARCH} 权限码的角色可调用</li>
 *   <li>非管理员用户的搜索结果会被 {@code UnifiedSearchService} 按部门 / 数据范围过滤</li>
 *   <li>跨租户搜索由 {@code X-Tenant-Id} 头自动隔离</li>
 * </ul>
 *
 * <p><b>与全局搜索的区别：</b>{@code GlobalSearchController} 跨所有实体类型搜索，
 * 本 Controller 仅搜索用户类型（{@code types=["user"]}），性能更优、排序更贴合用户搜索场景。
 *
 * <p><b>安全特性：</b>
 * <ul>
 *   <li>启用 {@link AuthApiPermission} 接口级鉴权（细粒度权限码）</li>
 *   <li>启用 {@link Audit} 审计日志（搜索行为合规追溯）</li>
 *   <li>索引重建接口仅运维可调用，建议通过单独角色限制</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.system.web.controller.GlobalSearchController 全局搜索（跨实体）
 * @see UnifiedSearchService 统一搜索服务
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/userinfo/search")
@RequiredArgsConstructor
@Tag(name = "用户搜索", description = "用户全文搜索")
public class UserinfoSearchController {

    private final UnifiedSearchService unifiedSearchService;

    /**
     * 搜索用户
     *
     * <p>业务流程：参数装配 → {@link SearchRequest} 构建 → {@link UnifiedSearchService#search} 并发执行
     * 所有匹配的 {@code SearchProvider} → 聚合结果 → 权限过滤后返回。
     * <p>默认开启<b>高亮</b>和<b>模糊匹配</b>，可显著提升搜索体验。
     * <p>所有搜索操作记入审计日志（{@link Audit}），便于合规追溯。
     * <p>仅搜索 {@code types=["user"]} 类型；如需跨类型搜索请使用 {@code GlobalSearchController}。
     *
     * @param keyword      搜索关键字（必填，按 username / realName / phone / email 全文匹配）
     * @param page         页码（默认 1）
     * @param pageSize     每页条数（默认 20）
     * @param userId       当前用户 ID（来自请求头 {@code X-User-Id}）
     * @param tenantId     当前租户 ID（来自请求头 {@code X-Tenant-Id}，用于多租户隔离）
     * @param rolesHeader  用户角色列表（逗号分隔，来自请求头 {@code X-User-Roles}）
     * @param deptId       用户部门 ID（来自请求头 {@code X-User-Dept}）
     * @param adminHeader  是否管理员（{@code true} / {@code false}，来自请求头 {@code X-User-Admin}）
     * @return 搜索响应（含分页结果、高亮片段、聚合信息等）
     */
    @GetMapping
    @Operation(summary = "搜索用户")
    @Audit(action = AuditAction.QUERY, module = "USERINFO", content = "搜索用户")
    @AuthApiPermission(apiCodes = PermissionCodes.USERINFO_SEARCH)
    public BaseResponse<SearchResponse> search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestHeader(value = AuthHeaderConstants.X_USER_ID, required = false) String userId,
            @RequestHeader(value = DataPermissionHeaderConstants.X_TENANT_ID, required = false) String tenantId,
            @RequestHeader(value = AuthHeaderConstants.X_USER_ROLES, required = false) String rolesHeader,
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

    /**
     * 重建用户索引（运维端点）
     *
     * <p>清空搜索缓存并触发索引重建（具体重建由 {@link UnifiedSearchService} 异步调度）。
     * <p><b>使用场景：</b>
     * <ul>
     *   <li>用户基础数据批量导入后</li>
     *   <li>ES 索引出现数据漂移需要重建时</li>
     *   <li>搜索服务重启后</li>
     * </ul>
     * <p>该接口建议通过独立运维角色调用，避免被普通用户误触。
     *
     * @param userId 触发重建的用户 ID（来自请求头 {@code X-User-Id}，记入审计日志）
     * @return 成功响应（无业务数据）
     */
    @PostMapping("/rebuild")
    @Operation(summary = "重建用户索引")
    @Audit(action = AuditAction.UPDATE, module = "USERINFO", content = "重建用户搜索索引")
    public BaseResponse<Void> rebuildIndex(
            @RequestHeader(value = AuthHeaderConstants.X_USER_ID, required = false) String userId) {

        unifiedSearchService.clearCache();
        log.info("[UserinfoSearch] 索引缓存已清除, userId={}", userId);
        return BaseResponse.success();
    }
}
