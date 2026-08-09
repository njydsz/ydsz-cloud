package com.njydsz.system.web.controller;

import java.util.Arrays;
import java.util.List;

import com.njydsz.common.core.constant.HeaderConstants;
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
 *
 * <p>提供跨模块统一搜索入口，聚合 project / user / config / wiki / flow 等多个业务域的搜索结果，
 * 是大厂 B 端「一个搜索框搜全部」体验的服务端支撑。
 *
 * <p><b>接口路径：</b>{@code /api/v1/search}
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>跨模块聚合</b>：单次请求并发查询多个 {@link com.njydsz.common.search.spi.SearchProvider}，
 *       合并结果按相关度排序</li>
 *   <li><b>权限过滤</b>：根据当前用户的角色 / 部门 / 租户 / 管理员标识，下推过滤条件到每个 Provider，
 *       确保用户只能看到自己有权限的内容</li>
 *   <li><b>类型过滤</b>：通过 {@code types} 参数指定搜索的实体类型（如 {@code project,user,config}），
 *       不指定时搜索全部类型</li>
 *   <li><b>高亮 + 模糊匹配</b>：默认开启高亮（{@code highlight=true}）和模糊匹配（{@code fuzzy=true}）</li>
 * </ul>
 *
 * <p><b>权限要求：</b>{@link PermissionCodes#SYSTEM_SEARCH}（全局搜索权限码）
 *
 * <p><b>请求头约定：</b>
 * <ul>
 *   <li>{@code X-User-Id}：当前用户 ID（来自网关透传）</li>
 *   <li>{@code X-Tenant-Id}：当前租户 ID（多租户隔离）</li>
 *   <li>{@code X-User-Roles}：用户角色编码列表（逗号分隔）</li>
 *   <li>{@code X-User-Dept}：用户所属部门 ID</li>
 *   <li>{@code X-User-Admin}：是否为管理员（{@code true} / {@code false}）</li>
 * </ul>
 *
 * <p><b>典型使用：</b>
 * <pre>{@code
 * GET /api/v1/search?keyword=合同审批&page=1&pageSize=20&types=project,flow
 * → 返回 project + flow 两种类型的搜索结果，已按用户权限过滤
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see UnifiedSearchService 统一搜索服务
 * @see com.njydsz.common.search.spi.SearchProvider 搜索 Provider SPI
 * @see SearchRequest 搜索请求 DTO
 * @see SearchResponse 搜索响应 DTO
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
@Tag(name = "全局搜索", description = "跨模块统一全文搜索")
public class GlobalSearchController {

    private final UnifiedSearchService unifiedSearchService;

    /**
     * 执行全局搜索
     *
     * <p>业务流：参数装配 → {@link SearchRequest} 构建 → {@link UnifiedSearchService#search} 并发执行
     * 所有匹配的 {@link com.njydsz.common.search.spi.SearchProvider} → 聚合结果 → 权限过滤后返回。
     * <p>默认开启<b>高亮</b>和<b>模糊匹配</b>，可显著提升搜索体验。
     * <p>所有搜索操作记入审计日志（{@link Audit}），便于合规追溯。
     *
     * @param keyword    搜索关键字（必填）
     * @param page       页码（默认 1）
     * @param pageSize   每页条数（默认 20）
     * @param userId     当前用户 ID（来自请求头 {@code X-User-Id}）
     * @param tenantId   当前租户 ID（来自请求头 {@code X-Tenant-Id}，用于多租户隔离）
     * @param rolesHeader 用户角色列表（逗号分隔，来自请求头 {@code X-User-Roles}）
     * @param deptId     用户部门 ID（来自请求头 {@code X-User-Dept}）
     * @param adminHeader 是否管理员（{@code true} / {@code false}，来自请求头 {@code X-User-Admin}）
     * @param typesParam 限定搜索的实体类型列表（逗号分隔，可选；不指定时搜索全部类型）
     * @return 搜索响应（含分页结果、各类型的命中数、聚合信息等）
     */
    @GetMapping
    @Operation(summary = "全局搜索", description = "跨所有模块的统一搜索")
    @Audit(action = AuditAction.QUERY, module = "SYSTEM", content = "全局搜索")
    @AuthApiPermission(apiCodes = PermissionCodes.SYSTEM_SEARCH)
    public BaseResponse<SearchResponse> search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestHeader(value = HeaderConstants.X_USER_ID, required = false) String userId,
            @RequestHeader(value = HeaderConstants.X_TENANT_ID, required = false) String tenantId,
            @RequestHeader(value = HeaderConstants.X_USER_ROLES, required = false) String rolesHeader,
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
