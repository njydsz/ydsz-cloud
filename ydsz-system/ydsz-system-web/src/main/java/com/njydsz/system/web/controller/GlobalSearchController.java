package com.njydsz.system.web.controller;

import java.util.Arrays;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.jdbc.constant.DataPermissionHeaderConstants;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.common.search.api.SearchRequest;
import com.njydsz.common.search.api.SearchResponse;
import com.njydsz.common.search.api.SearchSuggestion;
import com.njydsz.common.search.service.UnifiedSearchService;

/**
 * 全局搜索聚合 Controller
 *
 * <p>提供跨模块统一搜索入口，聚合 project / user / config / wiki / flow 等多个业务域的搜索结果， 是大厂 B 端「一个搜索框搜全部」体验的服务端支撑。
 *
 * <p><b>接口路径：</b>
 *
 * <pre>
 *   GET  /api/v1/search             - 全局搜索（跨模块聚合）
 *   GET  /api/v1/search/suggest     - 搜索自动补全建议
 *   GET  /api/v1/search/did-you-mean - "您是不是要找"纠错建议
 *   POST /api/v1/search/rebuild     - 重建搜索索引缓存
 * </pre>
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>跨模块聚合</b>：单次请求并发查询多个 {@link com.njydsz.common.search.spi.SearchProvider}， 合并结果按相关度排序
 *   <li><b>权限过滤</b>：根据当前用户的角色 / 部门 / 租户 / 管理员标识，下推过滤条件到每个 Provider， 确保用户只能看到自己有权限的内容
 *   <li><b>类型过滤</b>：通过 {@code types} 参数指定搜索的实体类型（如 {@code project,user,config}）， 不指定时搜索全部类型
 *   <li><b>高亮 + 模糊匹配</b>：默认开启高亮（{@code highlight=true}）和模糊匹配（{@code fuzzy=true}）
 *   <li><b>自动补全 + 纠错</b>：{@code GET /suggest} 提供搜索框下拉提示，{@code GET /did-you-mean} 提供零结果纠错
 * </ul>
 *
 * <p><b>权限要求：</b>{@link PermissionCodes#SYSTEM_SEARCH}（全局搜索权限码）
 *
 * <p><b>请求头约定：</b>
 *
 * <ul>
 *   <li>{@code X-User-Id}：当前用户 ID（来自网关透传）
 *   <li>{@code X-Tenant-Id}：当前租户 ID（多租户隔离）
 *   <li>{@code X-User-Roles}：用户角色编码列表（逗号分隔）
 *   <li>{@code X-User-Dept}：用户所属部门 ID
 *   <li>{@code X-User-Admin}：是否为管理员（{@code true} / {@code false}）
 * </ul>
 *
 * <p><b>典型使用：</b>
 *
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
   * <p>业务流：参数装配 → {@link SearchRequest} 构建 → {@link UnifiedSearchService#search} 并发执行 所有匹配的 {@link
   * com.njydsz.common.search.spi.SearchProvider} → 聚合结果 → 权限过滤后返回。
   *
   * <p>默认开启<b>高亮</b>和<b>模糊匹配</b>，可显著提升搜索体验。
   *
   * <p>所有搜索操作记入审计日志（{@link Audit}），便于合规追溯。
   *
   * @param keyword 搜索关键字（必填）
   * @param page 页码（默认 1）
   * @param pageSize 每页条数（默认 20）
   * @param typesParam 限定搜索的实体类型列表（逗号分隔，可选；不指定时搜索全部类型）
   * @param request HTTP 请求（从中提取用户上下文请求头：{@code X-User-Id} / {@code X-Tenant-Id} /
   *     {@code X-User-Roles} / {@code X-User-Dept} / {@code X-User-Admin}）
   * @return 搜索响应（含分页结果、各类型的命中数、聚合信息等）
   */
  @GetMapping
  @Operation(summary = "全局搜索", description = "跨所有模块的统一搜索")
  @AuthApiPermission(apiCodes = PermissionCodes.SYSTEM_SEARCH)
  public YdszResponse<SearchResponse> search(
      @RequestParam String keyword,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize,
      @RequestParam(value = "types", required = false) String typesParam,
      HttpServletRequest request) {

    // pageSize 服务端硬上限截断，防止深度分页 OOM
    int safePageSize = Math.min(pageSize, MAX_PAGE_SIZE);
    String userId = request.getHeader(AuthHeaderConstants.X_USER_ID);
    String tenantId = request.getHeader(DataPermissionHeaderConstants.X_TENANT_ID);
    String rolesHeader = request.getHeader(AuthHeaderConstants.X_USER_ROLES);
    String deptId = request.getHeader(USER_DEPT_HEADER);
    String adminHeader = request.getHeader(USER_ADMIN_HEADER);

    SearchRequest.SearchRequestBuilder builder =
        SearchRequest.builder()
            .keyword(keyword)
            .page(page)
            .pageSize(safePageSize)
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

    return YdszResponse.success(unifiedSearchService.search(builder.build()));
  }

  /**
   * 搜索自动补全建议
   *
   * <p>根据用户已输入的前缀返回自动补全候选词，用于搜索框下拉提示。 委托 {@link UnifiedSearchService#suggest} 通过引擎前缀建议 +
   * 热门搜索兜底三层召回。
   *
   * @param prefix 用户已输入的前缀（必填）
   * @return 自动补全建议（含候选词列表）
   */
  @GetMapping("/suggest")
  @Operation(summary = "搜索自动补全", description = "搜索框下拉自动补全建议")
  @AuthApiPermission(apiCodes = PermissionCodes.SYSTEM_SEARCH)
  public YdszResponse<SearchSuggestion> suggest(@RequestParam String prefix) {
    return YdszResponse.success(unifiedSearchService.suggest(prefix));
  }

  /**
   * "您是不是要找"纠错建议
   *
   * <p>在零结果场景下引导用户重新检索，基于 Levenshtein 编辑距离纠错。 委托 {@link UnifiedSearchService#didYouMean} 生成纠错候选词。
   *
   * @param keyword 用户输入的搜索词（通常为零结果查询词，必填）
   * @return 纠错建议（含候选词列表）
   */
  @GetMapping("/did-you-mean")
  @Operation(summary = "搜索纠错建议", description = "零结果时的 \"您是不是要找\" 纠错建议")
  @AuthApiPermission(apiCodes = PermissionCodes.SYSTEM_SEARCH)
  public YdszResponse<SearchSuggestion> didYouMean(@RequestParam String keyword) {
    return YdszResponse.success(unifiedSearchService.didYouMean(keyword));
  }

  /**
   * 重建搜索索引缓存
   *
   * <p>清空 {@link UnifiedSearchService} 的本地缓存，强制下次查询重新从 ES / DB 加载最新数据。
   *
   * <p>典型场景：① 大批量数据导入后立即使搜索结果生效；② ES 索引切换 / 重建后清缓存； ③ 紧急修复搜索结果不一致。
   *
   * @param userId 操作用户 ID（来自请求头 {@code X-User-Id}，仅用于审计日志记录）
   * @return 空响应
   */
  @PostMapping("/rebuild")
  @Operation(summary = "重建搜索索引缓存", description = "清空搜索本地缓存，强制下次查询重新加载")
  @Audit(action = AuditAction.UPDATE, module = "SYSTEM", content = "重建搜索索引缓存")
  public YdszResponse<Void> rebuildIndex(
      @RequestHeader(value = AuthHeaderConstants.X_USER_ID, required = false) String userId) {
    unifiedSearchService.clearCache();
    log.info("[GlobalSearch] 索引缓存已清除, userId={}", userId);
    return YdszResponse.success();
  }

  /** 分页安全上限：防止 pageSize=999999 导致深度分页 OOM */
  private static final int MAX_PAGE_SIZE = 500;

  /** 用户部门请求头 */
  private static final String USER_DEPT_HEADER = "X-User-Dept";

  /** 是否管理员请求头 */
  private static final String USER_ADMIN_HEADER = "X-User-Admin";
}
