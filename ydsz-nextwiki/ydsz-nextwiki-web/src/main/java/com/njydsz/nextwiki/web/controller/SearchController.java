package com.njydsz.nextwiki.web.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.constant.AuthHeaderConstants;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.nextwiki.api.dto.NextwikiDTOs;
import com.njydsz.nextwiki.domain.vo.SearchResultVO;
import com.njydsz.nextwiki.server.service.SearchApplicationService;

/**
 * 全文搜索 REST API Controller。
 *
 * <p>提供网盘文件的综合搜索能力，支持多维度（文件名/路径/标签/内容）搜索：
 *
 * <ul>
 *   <li>{@code POST /search} - 综合搜索（支持 scope 限定：name/content/tag/all）
 *   <li>{@code POST /search/rebuild} - 重建全量索引（运维/修复用）
 * </ul>
 *
 * <h3>核心能力</h3>
 *
 * <ul>
 *   <li>多源适配：当 Elasticsearch 可用时走 ES 全文检索（高性能）；不可用时降级为数据库 LIKE 搜索
 *   <li>多维度：支持按文件名、文件内容、标签、文件路径等维度搜索
 *   <li>权限隔离：搜索结果按当前用户 ID 过滤，仅返回用户有权限访问的文件
 *   <li>高亮显示：ES 模式下可返回关键词高亮信息（前端可高亮展示匹配片段）
 * </ul>
 *
 * <h3>安全与稳定性</h3>
 *
 * <ul>
 *   <li>搜索接口加 {@link Idempotent} 防重（5s TTL），避免重复请求
 *   <li>所有接口加 {@link AuthApiPermission} 权限码校验（NEXTWIKI_SEARCH*）
 *   <li>重建索引是高耗时操作，仅管理员权限可触发
 *   <li>用户身份通过 {@code X-User-Id} 请求头传递（由网关层注入）
 * </ul>
 *
 * <h3>接口路径</h3>
 *
 * <pre>
 *   POST /api/v1/nextwiki/search       - 综合搜索
 *   POST /api/v1/nextwiki/search/rebuild - 重建全量索引
 * </pre>
 *
 * <h3>架构位置</h3>
 *
 * <pre>
 *   前端 (PC Web) → ydsz-gateway → ydsz-nextwiki-web (本 Controller)
 *                                            ↓
 *                                   ydsz-nextwiki-server.SearchApplicationService
 *                                       ├── WikiSearchProvider (ES 适配)
 *                                       └── SearchDomainService (DB LIKE 降级)
 *                                            ↓
 *                                   Elasticsearch / ydsz_search_index
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/nextwiki/search")
@RequiredArgsConstructor
@Tag(name = "全文搜索", description = "文件名/内容/标签综合搜索，支持 ES 全文检索与 DB LIKE 降级")
public class SearchController {

  /** 搜索应用服务（封装搜索逻辑 + 索引管理 + 降级策略） */
  private final SearchApplicationService searchApplicationService;

  /**
   * 综合搜索（含高级筛选能力）。
   *
   * <p>根据 keyword 在用户可见范围内搜索匹配的文件，支持多维度高级筛选：
   *
   * <ul>
   *   <li>文件类型筛选：按后缀名过滤（pdf / docx / xlsx 等）
   *   <li>时间范围筛选：按更新时间范围过滤
   *   <li>大小范围筛选：按文件大小范围过滤
   *   <li>标签筛选：按标签名称过滤（OR 关系）
   * </ul>
   *
   * <p>底层自动选择搜索引擎（可用时）或 DB LIKE（降级）。
   *
   * @param request 搜索请求（keyword + 筛选字段）
   * @param userId 当前用户 ID（用于权限过滤）
   * @return 统一响应结果，data 为 {@link SearchResultVO}（含结果列表 + 总数 + 高亮信息）
   */
  @Idempotent(key = "ydsz:nextwiki:SearchController:search:lock", ttlSeconds = 5)
  @PostMapping
  @Operation(summary = "综合搜索", description = "支持文件类型/时间范围/大小范围/标签多维度高级筛选")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_SEARCH)
  public BaseResponse<SearchResultVO> search(
      @Valid @RequestBody NextwikiDTOs.SearchRequest request,
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {

    SearchResultVO result = searchApplicationService.searchWithFilters(request, userId);
    return BaseResponse.success(result);
  }

  /**
   * 搜索自动补全建议。
   *
   * <p>委托 ydsz-common-search 的 SuggestionService 提供三层召回： 引擎前缀建议 → 热门搜索兜底 → Levenshtein 纠错。
   * 搜索模块未引入时返回空列表。
   *
   * @param prefix 用户已输入的前缀
   * @return 自动补全候选词列表
   */
  @GetMapping("/suggest")
  @Operation(summary = "搜索自动补全")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_SEARCH)
  public BaseResponse<List<String>> suggest(@RequestParam String prefix) {
    List<String> suggestions = searchApplicationService.autocomplete(prefix);
    return BaseResponse.success(suggestions);
  }

  /**
   * "您是不是要找"纠错建议。
   *
   * <p>委托 ydsz-common-search 的 SuggestionService 基于 Levenshtein 编辑距离纠错。 搜索模块未引入时返回空列表。
   *
   * @param keyword 用户输入的搜索词（通常为零结果查询词）
   * @return 纠错候选词列表
   */
  @GetMapping("/did-you-mean")
  @Operation(summary = "搜索纠错建议")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_SEARCH)
  public BaseResponse<List<String>> didYouMean(@RequestParam String keyword) {
    List<String> corrections = searchApplicationService.didYouMean(keyword);
    return BaseResponse.success(corrections);
  }

  /**
   * 重建全量搜索索引。
   *
   * <p>运维操作：将所有可索引文件重新写入 ES / 全文索引表，用于：
   *
   * <ul>
   *   <li>索引丢失/损坏后的恢复
   *   <li>首次部署后初始化索引
   *   <li>索引结构变更后的全量重建
   * </ul>
   *
   * @param userId 当前用户 ID（用于审计）
   * @return 统一响应结果
   */
  @Idempotent(key = "ydsz:nextwiki:SearchController:rebuildIndices:lock", ttlSeconds = 5)
  @PostMapping("/rebuild")
  @Operation(summary = "重建全量索引")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_SEARCH_REBUILD)
  public BaseResponse<Void> rebuildIndices(
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {
    searchApplicationService.rebuildAllIndices();
    return BaseResponse.success();
  }
}
