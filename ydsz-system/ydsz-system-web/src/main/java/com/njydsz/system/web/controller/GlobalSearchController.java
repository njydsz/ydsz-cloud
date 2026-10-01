package com.njydsz.system.web.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.constant.PermissionCodes;
import com.njydsz.common.auth.context.AuthContextUtils;
import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.search.analytics.ClickFeedbackService;
import com.njydsz.common.search.analytics.SearchAnalyticsService;
import com.njydsz.common.search.analytics.SearchAnalyticsService.HotKeyword;
import com.njydsz.common.search.analytics.SearchAnalyticsService.SearchAnalyticsSummary;
import com.njydsz.common.search.api.SearchAggregation;
import com.njydsz.common.search.api.SearchRequest;
import com.njydsz.common.search.api.SearchResponse;
import com.njydsz.common.search.api.SearchSuggestion;
import com.njydsz.common.search.service.IndexRebuildService;
import com.njydsz.common.search.service.UnifiedSearchService;

/**
 * 全局统一搜索 Controller。
 *
 * <p>提供跨实体类型（项目/合同/任务/文档等）的统一检索入口，整合搜索引擎查询、自动补全建议、
 * 点击反馈回传、搜索分析统计与索引重建运维能力。所有检索请求经 {@link UnifiedSearchService}
 * 统一编排，自动串联限流、熔断、缓存、分词、权限过滤与业务重排。
 *
 * <p><b>接口路径：</b>{@code /api/search}
 *
 * <p><b>端点清单：</b>
 *
 * <ul>
 *   <li>{@code GET /api/search} — 跨类型统一搜索（types 可选，不传搜全部）
 *   <li>{@code GET /api/search/aggregations} — 单字段聚合查询（友好参数格式）
 *   <li>{@code GET /api/search/suggest} — 自动补全/搜索建议
 *   <li>{@code POST /api/search/click} — 搜索结果点击反馈回传
 *   <li>{@code GET /api/search/analytics/hot} — 热门关键词排行（需 dashboard 权限）
 *   <li>{@code GET /api/search/analytics/zero} — 零结果关键词排行（需 dashboard 权限）
 *   <li>{@code GET /api/search/analytics/summary} — 搜索概览统计
 *   <li>{@code POST /api/search/rebuild} — 清空缓存并触发索引重建（运维操作）
 * </ul>
 *
 * <p><b>安全特性：</b>
 *
 * <ul>
 *   <li>所有端点启用 {@link AuthApiPermission} 权限校验</li>
 *   <li>分析/运维端点需额外具备 {@code system:search:dashboard} 权限</li>
 *   <li>所有请求记录 {@link Audit} 操作审计</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.10.01
 * @see UnifiedSearchService 跨实体统一搜索服务
 * @see SearchAnalyticsService 搜索行为分析服务（热门词/零结果词/汇总）
 * @see ClickFeedbackService 点击反馈闭环服务（CTR 回传）
 * @see IndexRebuildService 索引重建服务（全量/按类型重建）
 */
@Slf4j
@ApiVersion("26.10.01")
@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
@Tag(name = "全局搜索", description = "跨实体类型统一检索、搜索建议、点击反馈与搜索运维分析")
@AuthApiPermission(apiCodes = PermissionCodes.SYSTEM_SEARCH)
public class GlobalSearchController {

  private final UnifiedSearchService unifiedSearchService;
  private final SearchAnalyticsService searchAnalyticsService;
  private final ClickFeedbackService clickFeedbackService;
  private final IndexRebuildService indexRebuildService;

  // ============================== 检索端点 ==============================

  /**
   * 跨实体类型统一搜索。
   *
   * <p>将 keyword/page/pageSize/types 等参数绑定到 {@link SearchRequest}，
   * 并从当前请求上下文自动补充 userId/tenantId 后交由 {@link UnifiedSearchService} 执行检索。
   * types 为空时搜索全部已注册实体类型。
   *
   * @param request 检索请求参数（keyword、types、page、pageSize、aggregations 等）
   * @return 统一搜索响应（命中列表、总数、耗时、聚合信息）
   */
  @Audit(
      module = "全局搜索",
      type = AuditType.OPERATION,
      action = AuditAction.QUERY,
      content = "'跨类型搜索: ' + #request.keyword")
  @Operation(summary = "跨类型统一搜索", description = "types 可选，不传搜全部已注册实体类型；支持 keyword/page/pageSize/aggregations 等参数")
  @GetMapping
  public YdszResponse<SearchResponse> search(SearchRequest request) {
    fillUserContext(request);
    SearchResponse response = unifiedSearchService.search(request);
    log.info("搜索请求: keyword={}, types={}, page={}, pageSize={}, total={}",
        request.getKeyword(), request.getTypes(), request.getPage(), request.getPageSize(),
        response.getTotal());
    return YdszResponse.success(response);
  }

  // ============================== 聚合查询端点 ==============================

  /**
   * 单字段聚合查询（友好参数格式）。
   *
   * <p>相比 {@code GET /search} 直接接受 {@code aggregations} 参数，本端点用扁平化的 query 参数
   * 简化前端调用，自动构造 {@link SearchAggregation} 并返回聚合结果。
   *
   * @param type  实体类型（如 project/task/contract 等，为空搜索全部）
   * @param field 聚合字段名（必填）
   * @param size  返回 Top N（可选，默认 10）
   * @return 聚合结果列表
   */
  @Audit(
      module = "全局搜索",
      type = AuditType.OPERATION,
      action = AuditAction.QUERY,
      content = "'聚合查询: type=' + #type + ', field=' + #field")
  @Operation(summary = "单字段聚合查询", description = "扁平化参数格式的聚合查询；field 为必填字段名，size 控制返回 Top N")
  @GetMapping("/aggregations")
  public YdszResponse<List<SearchAggregation>> aggregations(
      @RequestParam(required = false) String type,
      @RequestParam String field,
      @RequestParam(required = false) Integer size) {
    SearchRequest request = SearchRequest.builder().build();
    if (type != null && !type.isBlank()) {
      request.setTypes(List.of(type));
    }
    request.setAggregations(List.of(
        SearchAggregation.builder()
            .field(field)
            .label(field)
            .build()));
    fillUserContext(request);
    SearchResponse response = unifiedSearchService.search(request);
    log.info("聚合查询: type={}, field={}, size={}, aggCount={}",
        type, field, size,
        response.getAggregations() != null ? response.getAggregations().size() : 0);
    return YdszResponse.success(response.getAggregations());
  }

  // ============================== 搜索建议端点 ==============================

  /**
   * 自动补全 / 搜索建议。
   *
   * <p>根据用户已输入的前缀返回联想词列表，由底层引擎注册的 SuggestStrategy 提供；
   * 引擎不支持建议能力时返回空列表而非报错。
   *
   * @param prefix 用户已输入的前缀（非空）
   * @return 搜索建议结果（自动补全条目列表）
   */
  @Audit(
      module = "全局搜索",
      type = AuditType.OPERATION,
      action = AuditAction.QUERY,
      content = "'搜索建议: ' + #prefix")
  @Operation(summary = "自动补全/搜索建议", description = "按前缀返回联想词列表；引擎不支持时返回空列表")
  @GetMapping("/suggest")
  public YdszResponse<SearchSuggestion> suggest(@RequestParam String prefix) {
    SearchSuggestion suggestion = unifiedSearchService.suggest(prefix);
    log.debug("搜索建议: prefix={}, items={}", prefix,
        suggestion.getItems() != null ? suggestion.getItems().size() : 0);
    return YdszResponse.success(suggestion);
  }

  // ============================== 点击反馈端点 ==============================

  /**
   * 搜索结果点击反馈回传。
   *
   * <p>前端用户点击搜索结果后回传点击事件，由 {@link ClickFeedbackService} 写入 Redis（CTR ZSet），
   * 供 {@code BusinessRanker} 重排阶段消费以提升高 CTR 文档排序。
   *
   * <p>Redis 不可用时降级为 no-op，不影响主链路。
   *
   * @param clickRequest 点击反馈请求体（keyword/docId/position/sessionId）
   * @return 是否接受回传（恒定 true）
   */
  @Audit(
      module = "全局搜索",
      type = AuditType.OPERATION,
      action = AuditAction.OTHER,
      content = "'点击反馈: keyword=' + #clickRequest.keyword + ', docId=' + #clickRequest.docId")
  @Operation(summary = "点击反馈回传", description = "接收搜索结果点击事件，回写到 CTR ZSet 供排序消费")
  @PostMapping("/click")
  public YdszResponse<Boolean> recordClick(@RequestBody ClickFeedbackRequest clickRequest) {
    clickFeedbackService.recordClick(
        clickRequest.keyword(),
        clickRequest.docId(),
        clickRequest.position() != null ? clickRequest.position() : 0,
        AuthContextUtils.getCurrentOrNull() != null
            ? AuthContextUtils.getCurrentOrNull().getUserId()
            : null,
        clickRequest.sessionId());
    log.info("点击反馈: keyword={}, docId={}, position={}",
        clickRequest.keyword(), clickRequest.docId(), clickRequest.position());
    return YdszResponse.success(Boolean.TRUE);
  }

  // ============================== 分析端点（dashboard 权限） ==============================

  /**
   * 获取热门关键词排行。
   *
   * <p>优先从 Redis ZSet 读取；Redis 不可用时降级到本地内存统计。
   *
   * @param limit 返回条数上限（默认 20）
   * @return 按搜索次数降序排列的热搜词列表
   */
  @AuthApiPermission(apiCodes = PermissionCodes.SYSTEM_SEARCH_DASHBOARD)
  @Audit(
      module = "全局搜索",
      type = AuditType.OPERATION,
      action = AuditAction.QUERY,
      content = "'查询热门关键词: limit=' + #limit")
  @Operation(summary = "热门关键词", description = "热门搜索词排行（按搜索次数降序）；需 dashboard 权限")
  @GetMapping("/analytics/hot")
  public YdszResponse<List<HotKeyword>> hotKeywords(
      @RequestParam(defaultValue = "20") int limit) {
    List<HotKeyword> keywords = searchAnalyticsService.getHotKeywords(limit);
    log.info("热门关键词查询: limit={}, resultSize={}", limit, keywords.size());
    return YdszResponse.success(keywords);
  }

  /**
   * 获取零结果关键词排行。
   *
   * <p>用于优化搜索建议与内容补全。优先从 Redis ZSet 读取；Redis 不可用时降级到内存统计。
   *
   * @param limit 返回条数上限（默认 20）
   * @return 按零结果次数降序排列的关键词列表
   */
  @AuthApiPermission(apiCodes = PermissionCodes.SYSTEM_SEARCH_DASHBOARD)
  @Audit(
      module = "全局搜索",
      type = AuditType.OPERATION,
      action = AuditAction.QUERY,
      content = "'查询零结果关键词: limit=' + #limit")
  @Operation(summary = "零结果关键词", description = "零结果搜索词排行（按零结果次数降序）；需 dashboard 权限")
  @GetMapping("/analytics/zero")
  public YdszResponse<List<HotKeyword>> zeroResultKeywords(
      @RequestParam(defaultValue = "20") int limit) {
    List<HotKeyword> keywords = searchAnalyticsService.getZeroResultKeywords(limit);
    log.info("零结果关键词查询: limit={}, resultSize={}", limit, keywords.size());
    return YdszResponse.success(keywords);
  }

  // ============================== 概览统计端点 ==============================

  /**
   * 获取搜索概览统计。
   *
   * <p>返回总搜索量、零结果量、零结果率、去重关键词数与零结果关键词数。数据来自内存统计
   * （不依赖 Redis），反映本节点运行状态。
   *
   * @return 搜索概览汇总数据
   */
  @Audit(
      module = "全局搜索",
      type = AuditType.OPERATION,
      action = AuditAction.QUERY,
      content = "'查询搜索概览统计'")
  @Operation(summary = "搜索概览统计", description = "总搜索量/零结果量/零结果率/去重关键词数/零结果关键词数")
  @GetMapping("/analytics/summary")
  public YdszResponse<SearchAnalyticsSummary> summary() {
    SearchAnalyticsSummary summary = searchAnalyticsService.getSummary();
    log.info("搜索概览统计: totalSearches={}, zeroResultRate={}",
        summary.totalSearches(), summary.zeroResultRate());
    return YdszResponse.success(summary);
  }

  // ============================== 运维端点 ==============================

  /**
   * 清空搜索缓存并触发索引重建。
   *
   * <p>运维功能：先清空搜索结果缓存（避免脏数据），再异步触发全量或按类型索引重建。
   * 重建提交到单线程池，最多 1 个任务在跑 + 1 个排队。
   *
   * @param types 待重建的实体类型列表；为空时重建全部已注册类型
   * @return 是否成功触发（恒定 true）
   */
  @AuthApiPermission(apiCodes = PermissionCodes.SYSTEM_SEARCH_DASHBOARD)
  @Audit(
      module = "全局搜索",
      type = AuditType.SYSTEM,
      action = AuditAction.SYNC,
      content = "'索引重建: types=' + #types")
  @Operation(summary = "索引重建",
      description = "清空缓存 + 异步触发索引重建（运维操作，需 dashboard 权限）")
  @PostMapping("/rebuild")
  public YdszResponse<Boolean> rebuild(
      @RequestParam(required = false) List<String> types) {
    unifiedSearchService.clearCache();
    if (types != null && !types.isEmpty()) {
      for (String type : types) {
        indexRebuildService.rebuildAllAsync(type, null);
      }
      log.info("索引重建已触发: types={}", types);
    } else {
      indexRebuildService.rebuildAllAsync(null, null);
      log.info("全量索引重建已触发");
    }
    return YdszResponse.success(Boolean.TRUE);
  }

  // ============================== 私有方法 ==============================

  /**
   * 从当前请求上下文补充用户身份信息到搜索请求。
   *
   * <p>读取 userId / tenantId / isAdmin 以减少前端显式透传，保证数据权限过滤生效。
   *
   * @param request 搜索请求（就地修改）
   */
  private void fillUserContext(SearchRequest request) {
    try {
      request.setUserId(AuthContextUtils.getUserId());
    } catch (Exception e) {
      log.debug("[GlobalSearch] 未登录用户搜索请求，跳过 userId 回填");
    }
    request.setTenantId(AuthContextUtils.getTenantIdOrDefault());
  }

  // ============================== 内部 DTO ==============================

  /**
   * 聚合查询请求。
   *
   * @param field 聚合字段名
   * @param size  返回 Top N
   */
  public record AggregationRequest(String field, Integer size) {}

  /**
   * 点击反馈请求体。
   *
   * @param keyword  搜索关键词
   * @param docId    被点击文档 ID
   * @param position 点击位置（从 1 开始）
   * @param sessionId 搜索会话 ID（用于去重）
   */
  public record ClickFeedbackRequest(String keyword, String docId, Integer position, String sessionId) {}
}
