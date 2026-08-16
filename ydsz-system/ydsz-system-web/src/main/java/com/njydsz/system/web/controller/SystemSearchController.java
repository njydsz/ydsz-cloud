package com.njydsz.system.web.controller;

import java.util.Arrays;
import java.util.List;
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
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.constant.AuthHeaderConstants;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.jdbc.constant.DataPermissionHeaderConstants;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.common.search.api.SearchRequest;
import com.njydsz.common.search.api.SearchResponse;
import com.njydsz.common.search.service.UnifiedSearchService;

/**
 * 系统配置搜索 Controller
 *
 * <p>基于 {@link UnifiedSearchService} 提供系统配置的全文检索能力。
 * 与 {@link GlobalSearchController} 的区别：本 Controller 仅检索 {@code system} 域（配置项、变量、字典等），
 * 适合系统管理后台的精细化搜索；{@code GlobalSearchController} 跨所有模块聚合。
 *
 * <p><b>接口路径：</b>{@code /api/v1/system/search}
 *
 * <p><b>核心能力：</b>
 * <ul>
 *   <li>配置项（{@code ydsz_config}）的全文搜索</li>
 *   <li>系统变量（{@code ydsz_variable}）的全文搜索</li>
 *   <li>字典类型/字典项的全文搜索</li>
 *   <li>支持高亮、过滤、分页、排序</li>
 * </ul>
 *
 * <p><b>安全特性：</b>
 * <ul>
 *   <li>需权限码 {@link PermissionCodes#SYSTEM_SEARCH}</li>
 *   <li>启用 {@link Audit} 审计（QUERY 类型），便于合规留存</li>
 *   <li>仅系统管理角色可访问（通过 {@code @AuthApiPermission} 限定）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see GlobalSearchController 全局搜索聚合 Controller（跨模块）
 * @see UnifiedSearchService 统一搜索服务
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/system/search")
@RequiredArgsConstructor
@Tag(name = "系统搜索", description = "系统配置全文搜索")
public class SystemSearchController {

    private final UnifiedSearchService unifiedSearchService;

    /**
     * 搜索系统配置
     *
     * <p>走 {@link UnifiedSearchService} 统一搜索能力，限定 {@code types=["config"]} 仅检索系统配置域。
     * <p>默认开启<b>高亮</b>和<b>模糊匹配</b>，支持多关键字和拼写纠错。
     * <p>所有搜索操作记入审计日志（{@link Audit} QUERY 类型），便于合规追溯。
     *
     * @param keyword     搜索关键字（必填）
     * @param page        页码（默认 1）
     * @param pageSize    每页条数（默认 20）
     * @param userId      当前用户 ID（来自请求头 {@code X-User-Id}）
     * @param tenantId    当前租户 ID（来自请求头 {@code X-Tenant-Id}，多租户隔离）
     * @param rolesHeader 用户角色列表（逗号分隔，来自请求头 {@code X-User-Roles}）
     * @param deptId      用户部门 ID（来自请求头 {@code X-User-Dept}）
     * @param adminHeader 是否管理员（{@code true} / {@code false}，来自请求头 {@code X-User-Admin}）
     * @return 搜索响应（含分页结果、命中片段、聚合信息等）
     */
    @GetMapping
    @Operation(summary = "搜索系统配置")
    @Audit(action = AuditAction.QUERY, module = "SYSTEM", content = "搜索系统配置")
    @AuthApiPermission(apiCodes = PermissionCodes.SYSTEM_SEARCH)
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
                .types(List.of("config"))
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
     * 重建系统配置搜索索引
     *
     * <p>清空 {@link UnifiedSearchService} 的本地缓存（如 Caffeine 一级缓存），
     * 强制下次查询重新从 ES / DB 加载最新数据。
     * <p>典型场景：① 大批量配置导入后立即使搜索结果生效；② ES 索引切换 / 重建后清缓存；
     * ③ 紧急修复搜索结果不一致。
     * <p>本接口<b>仅清除缓存</b>，不重建 ES 索引（ES 重建由独立任务调度执行）。
     *
     * @param userId 操作用户 ID（来自请求头 {@code X-User-Id}，仅用于审计日志记录）
     * @return 空响应
     */
    @PostMapping("/rebuild")
    @Operation(summary = "重建系统配置索引")
    @Audit(action = AuditAction.UPDATE, module = "SYSTEM", content = "重建系统配置搜索索引")
    public BaseResponse<Void> rebuildIndex(
            @RequestHeader(value = AuthHeaderConstants.X_USER_ID, required = false) String userId) {

        unifiedSearchService.clearCache();
        log.info("[SystemSearch] 索引缓存已清除, userId={}", userId);
        return BaseResponse.success();
    }
}
