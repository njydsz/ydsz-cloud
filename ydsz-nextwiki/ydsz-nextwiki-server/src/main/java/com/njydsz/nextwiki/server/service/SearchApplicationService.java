package com.njydsz.nextwiki.server.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.search.api.SearchFilter;
import com.njydsz.common.search.api.SearchFilter.Operator;
import com.njydsz.common.search.api.SearchHit;
import com.njydsz.common.search.api.SearchRequest;
import com.njydsz.common.search.api.SearchResponse;
import com.njydsz.common.search.core.SearchEngineRegistry;
import com.njydsz.common.search.service.SuggestionService;
import com.njydsz.common.search.service.UnifiedSearchService;
import com.njydsz.nextwiki.api.dto.NextwikiDTOs;
import com.njydsz.nextwiki.domain.dto.SearchIndexDTO;
import com.njydsz.nextwiki.domain.query.SearchIndexQuery;
import com.njydsz.nextwiki.domain.repository.FileNodeRepository;
import com.njydsz.nextwiki.domain.repository.SearchIndexRepository;
import com.njydsz.nextwiki.domain.repository.TagRepository;
import com.njydsz.nextwiki.domain.service.SearchDomainService;
import com.njydsz.nextwiki.domain.vo.FileNodeVO;
import com.njydsz.nextwiki.domain.vo.SearchIndexVO;
import com.njydsz.nextwiki.domain.vo.SearchResultVO;
import com.njydsz.nextwiki.domain.vo.TagVO;

/**
 * NextWiki 搜索应用服务。
 *
 * <p>读路径优先走统一搜索引擎（{@code ydsz-common-search}），引擎不可用时降级到 DB LIKE 查询。
 *
 * <p><b>搜索链路：</b>
 *
 * <pre>
 *   用户请求 → SearchController → SearchApplicationService.search()
 *       ↓
 *   主路径：UnifiedSearchService → WikiSearchProvider（权限过滤 + 全文检索）
 *       ↓
 *   降级：SearchDomainService.search()（nw_search_index 表的 LIKE 查询）
 * </pre>
 *
 * <p><b>索引同步链路：</b>
 *
 * <ul>
 *   <li>增量同步：{@code FileOperatedEventListener} 调用 SearchIndexEventBridge → IndexSyncService
 *   <li>全量重建：{@code NextwikiScheduledJobs} 触发 → IndexRebuildService.rebuildAll()
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.common.search.service.UnifiedSearchService 统一搜索服务
 * @see com.njydsz.nextwiki.domain.service.SearchDomainService DB 降级搜索
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchApplicationService {

  private final SearchDomainService searchDomainService;
  private final SearchIndexRepository searchIndexRepository;
  private final FileNodeRepository fileNodeRepository;
  private final TagRepository tagRepository;
  private final ObjectProvider<UnifiedSearchService> unifiedSearchServiceProvider;
  private final ObjectProvider<SearchEngineRegistry> engineRegistryProvider;
  private final ObjectProvider<SuggestionService> suggestionServiceProvider;
  private final SearchHistoryService searchHistoryService;

  /**
   * 全文检索（按关键词在用户可见范围内分页搜索）。
   *
   * <p>搜索引擎可用时走统一检索链路（PG/ES + {@code WikiSearchProvider} 权限过滤）， 不可用时降级到 DB LIKE。引擎异常同样降级并打印 warn
   * 日志，保证搜索接口始终可用。
   *
   * <p><b>搜索记录：</b>每次成功搜索后记录用户搜索历史并更新热门搜索排行。
   *
   * @param keyword 搜索关键词（文件名/路径/全文/标签）
   * @param userId 操作人 ID（用于权限与结果过滤）
   * @param scope 搜索作用域（如 "all"/"my"，由领域服务解释）
   * @param page 页码（从 1 开始）
   * @param pageSize 每页大小
   * @return 分页搜索结果 {@link SearchResultVO}
   * @complexity 引擎路径 O(query)（一次搜索引擎查询 + 分页）；DB 路径同 {@link SearchDomainService#search}
   * @note 只读，无事务边界
   */
  public SearchResultVO search(
      String keyword, String userId, String scope, int page, int pageSize) {
    SearchEngineRegistry registry = engineRegistryProvider.getIfAvailable();
    UnifiedSearchService unifiedSearch = unifiedSearchServiceProvider.getIfAvailable();

    SearchResultVO result;
    if (registry != null && unifiedSearch != null && registry.isPrimaryAvailable()) {
      result = searchViaEngine(unifiedSearch, keyword, userId, page, pageSize, new ArrayList<>());
    } else {
      log.info("[SearchApplicationService] 搜索引擎不可用，降级 DB LIKE: keyword={}", keyword);
      result = searchViaDatabase(keyword, scope, page, pageSize);
    }

    // 记录搜索历史（异步不影响主流程）
    searchHistoryService.recordSearch(userId, keyword);
    return result;
  }

  /**
   * 高级检索（支持多维度筛选：文件类型 / 时间范围 / 大小范围 / 标签）。
   *
   * <p>在基础搜索能力上叠加筛选条件，构建 {@link SearchFilter} 列表传递给搜索引擎。 搜索引擎不可用时降级到 DB LIKE（筛选条件在降级场景下不生效）。
   *
   * @param request 搜索请求 DTO（含 keyword + 筛选字段）
   * @param userId 操作人 ID（用于权限过滤）
   * @return 分页搜索结果 {@link SearchResultVO}
   * @complexity 引擎路径 O(query + filters)；DB 路径同 {@link SearchDomainService#search}
   * @note 降级时筛选条件不生效，返回未筛选的全量关键词匹配结果
   */
  public SearchResultVO searchWithFilters(NextwikiDTOs.SearchRequest request, String userId) {
    int pageNum = request.getEffectivePageNum();
    int pageSize = request.getEffectivePageSize();

    // 构建高级筛选条件列表
    List<SearchFilter> filters =
        buildSearchFilters(
            request.getFileTypes(),
            request.getStartDate(),
            request.getEndDate(),
            request.getMinSize(),
            request.getMaxSize(),
            request.getTags());

    SearchEngineRegistry registry = engineRegistryProvider.getIfAvailable();
    UnifiedSearchService unifiedSearch = unifiedSearchServiceProvider.getIfAvailable();

    SearchResultVO result;
    if (registry != null && unifiedSearch != null && registry.isPrimaryAvailable()) {
      result = searchViaEngine(
          unifiedSearch, request.getKeyword(), userId, pageNum, pageSize, filters);
    } else {
      log.info(
          "[SearchApplicationService] 搜索引擎不可用，降级 DB LIKE（筛选不生效）: keyword={}",
          request.getKeyword());
      result = searchViaDatabase(request.getKeyword(), request.getScope(), pageNum, pageSize);
    }

    // 记录搜索历史
    if (request.getKeyword() != null && !request.getKeyword().isBlank()) {
      searchHistoryService.recordSearch(userId, request.getKeyword());
    }
    return result;
  }

  /**
   * 重建全量搜索索引（通常由定时任务或运维操作触发）。
   *
   * <p>遍历全部文件节点，构建搜索索引 DTO 并 upsert 到 DB 降级索引表
   * （统一搜索引擎主索引由 {@code NextwikiScheduledJobs#rebuildSearchIndex} 通过
   * {@code IndexRebuildService} 维护）。双索引链路在此保持同步。
   *
   * @return 无返回值
   * @complexity O(N)（N 为文件总数，遍历重新建索引，耗时较长）
   * @note 非事务（批量操作）；执行期间建议避开高峰期，避免影响在线搜索
   * @see com.njydsz.nextwiki.server.job.NextwikiScheduledJobs#rebuildSearchIndex()
   */
  public void rebuildAllIndices() {
    List<FileNodeVO> allNodes = fileNodeRepository.findAll();
    int count = 0;
    for (FileNodeVO node : allNodes) {
      try {
        List<TagVO> tags = tagRepository.findByFileNodeId(node.getId());
        SearchIndexDTO dto = searchDomainService.buildSearchIndex(node, tags, null, null);
        searchIndexRepository.upsert(dto);
        count++;
      } catch (Exception e) {
        log.warn(
            "[SearchApplicationService] 索引重建跳过节点: nodeId={}, error={}",
            node.getId(),
            e.getMessage());
      }
    }
    log.info("[SearchApplicationService] 全量索引重建完成: total={}, rebuilt={}", allNodes.size(), count);
  }

  /**
   * 搜索自动补全建议（委托 ydsz-common-search 的 SuggestionService）。
   *
   * <p>底层召回三层：引擎前缀建议 → 热门搜索兜底 → Levenshtein 纠错。 搜索模块未引入时返回空列表。
   *
   * @param prefix 用户已输入的前缀
   * @return 自动补全候选词列表；搜索模块不可用时返回空列表
   */
  public List<String> autocomplete(String prefix) {
    SuggestionService suggestionService = suggestionServiceProvider.getIfAvailable();
    if (suggestionService == null) {
      return List.of();
    }
    return suggestionService.autocomplete(prefix);
  }

  /**
   * "您是不是要找"纠错建议（委托 ydsz-common-search 的 SuggestionService）。
   *
   * <p>基于 Levenshtein 编辑距离按词长自适应纠错。 搜索模块未引入时返回空列表。
   *
   * @param keyword 用户输入的搜索词（通常为零结果查询词）
   * @return 纠错候选词列表；搜索模块不可用时返回空列表
   */
  public List<String> didYouMean(String keyword) {
    SuggestionService suggestionService = suggestionServiceProvider.getIfAvailable();
    if (suggestionService == null) {
      return List.of();
    }
    return suggestionService.didYouMean(keyword);
  }

  /**
   * 获取用户搜索历史列表。
   *
   * @param userId 用户 ID
   * @return 搜索历史列表（最新在前）
   */
  public List<String> getUserSearchHistory(String userId) {
    return searchHistoryService.getUserHistory(userId);
  }

  /**
   * 清除用户搜索历史。
   *
   * @param userId 用户 ID
   */
  public void clearUserSearchHistory(String userId) {
    searchHistoryService.clearUserHistory(userId);
  }

  /**
   * 获取热门搜索列表。
   *
   * @return 热门搜索词及热度分值列表（按热度降序）
   */
  public List<Map.Entry<String, Double>> getHotSearches() {
    return searchHistoryService.getHotSearches();
  }

  // ==================== 私有方法 ====================

  /**
   * 通过统一搜索引擎执行检索（含高级筛选），结果转换为 {@link SearchResultVO}。
   *
   * @param unifiedSearch 统一搜索服务
   * @param keyword 搜索关键词
   * @param userId 操作人 ID
   * @param page 页码
   * @param pageSize 每页大小
   * @param filters 高级筛选条件列表（可为 {@code null} 或空）
   * @return 分页搜索结果
   */
  private SearchResultVO searchViaEngine(
      UnifiedSearchService unifiedSearch,
      String keyword,
      String userId,
      int page,
      int pageSize,
      List<SearchFilter> filters) {
    SearchRequest request =
        SearchRequest.builder()
            .keyword(keyword)
            .types(List.of("wiki"))
            .page(page)
            .pageSize(pageSize)
            .userId(userId)
            .highlight(true)
            .filters(filters != null ? filters : new ArrayList<>())
            .build();

    try {
      SearchResponse response = unifiedSearch.search(request);
      log.info(
          "[SearchApplicationService] 引擎检索完成: keyword={}, total={}, tookMs={}, engine={}, filters={}",
          keyword,
          response.getTotal(),
          response.getTookMs(),
          response.getEngine(),
          request.getFilters().size());
      return convertResponse(response);
    } catch (Exception e) {
      log.warn(
          "[SearchApplicationService] 引擎检索异常，降级 DB: keyword={}, error={}", keyword, e.getMessage());
      return searchViaDatabase(keyword, null, page, pageSize);
    }
  }

  /**
   * DB LIKE 降级搜索（构建查询 → 分页 → 领域服务评分组装）。
   *
   * @param keyword 搜索关键词
   * @param scope 搜索范围（all/filename/content/tag，可空）
   * @param page 页码
   * @param pageSize 每页大小
   * @return 分页搜索结果
   */
  private SearchResultVO searchViaDatabase(String keyword, String scope, int page, int pageSize) {
    SearchIndexQuery query =
        SearchIndexQuery.builder()
            .keyword(keyword)
            .scope(scope)
            .page(page)
            .pageSize(pageSize)
            .build();
    PageResponse<List<SearchIndexVO>> pageResult = searchIndexRepository.searchPage(query);
    List<SearchIndexVO> indices = pageResult.getData();
    long total = pageResult.getTotal() != null ? pageResult.getTotal() : 0L;
    return searchDomainService.search(
        indices != null ? indices : List.of(), total, keyword, page, pageSize);
  }

  /**
   * 构建高级筛选条件列表。
   *
   * <p>将前端传入的筛选参数转换为搜索引擎的 {@link SearchFilter} 列表。 支持的筛选维度：
   *
   * <ul>
   *   <li>文件类型：suffix IN (fileTypes)
   *   <li>时间范围：updated_at BETWEEN [startDate, endDate]
   *   <li>大小范围：size BETWEEN [minSize, maxSize]
   *   <li>标签：tags IN (tagNames)
   * </ul>
   *
   * @param fileTypes 文件后缀名列表
   * @param startDate 更新时间起始
   * @param endDate 更新时间截止
   * @param minSize 文件大小下限
   * @param maxSize 文件大小上限
   * @param tags 标签名称列表
   * @return 筛选条件列表（可为空，表示无额外筛选）
   */
  List<SearchFilter> buildSearchFilters(
      List<String> fileTypes,
      String startDate,
      String endDate,
      Long minSize,
      Long maxSize,
      List<String> tags) {
    List<SearchFilter> filters = new ArrayList<>();

    // 文件类型筛选（suffix IN）
    if (fileTypes != null && !fileTypes.isEmpty()) {
      List<String> normalizedTypes =
          fileTypes.stream()
              .filter(t -> t != null && !t.isBlank())
              .map(String::toLowerCase)
              .map(t -> t.startsWith(".") ? t.substring(1) : t)
              .distinct()
              .collect(Collectors.toList());
      if (!normalizedTypes.isEmpty()) {
        filters.add(
            SearchFilter.builder()
                .field("suffix")
                .values(normalizedTypes)
                .operator(Operator.IN)
                .build());
      }
    }

    // 时间范围筛选（updated_at >= startDate AND updated_at <= endDate）
    if (startDate != null && !startDate.isBlank()) {
      filters.add(
          SearchFilter.builder()
              .field("updated_at")
              .values(List.of(startDate))
              .operator(Operator.GTE)
              .build());
    }
    if (endDate != null && !endDate.isBlank()) {
      filters.add(
          SearchFilter.builder()
              .field("updated_at")
              .values(List.of(endDate))
              .operator(Operator.LTE)
              .build());
    }

    // 大小范围筛选（size >= minSize AND size <= maxSize）
    if (minSize != null && minSize > 0) {
      filters.add(
          SearchFilter.builder()
              .field("size")
              .values(List.of(String.valueOf(minSize)))
              .operator(Operator.GTE)
              .build());
    }
    if (maxSize != null && maxSize > 0) {
      filters.add(
          SearchFilter.builder()
              .field("size")
              .values(List.of(String.valueOf(maxSize)))
              .operator(Operator.LTE)
              .build());
    }

    // 标签筛选（tags IN）
    if (tags != null && !tags.isEmpty()) {
      List<String> normalizedTags =
          tags.stream()
              .filter(t -> t != null && !t.isBlank())
              .distinct()
              .collect(Collectors.toList());
      if (!normalizedTags.isEmpty()) {
        filters.add(
            SearchFilter.builder()
                .field("tags")
                .values(normalizedTags)
                .operator(Operator.IN)
                .build());
      }
    }

    return filters;
  }

  /** 将 {@link SearchResponse} 转换为 {@link SearchResultVO}。 */
  private SearchResultVO convertResponse(SearchResponse response) {
    List<SearchResultVO.SearchHitVO> hits =
        response.getHits().stream().map(this::convertHit).collect(Collectors.toList());

    return SearchResultVO.builder()
        .hits(hits)
        .total(response.getTotal())
        .page(response.getPage())
        .pageSize(response.getPageSize())
        .tookMs(response.getTookMs())
        .build();
  }

  /** 单条命中转换：将引擎返回的 {@link SearchHit} 映射到 VO。 */
  private SearchResultVO.SearchHitVO convertHit(SearchHit hit) {
    return SearchResultVO.SearchHitVO.builder()
        .fileNodeId(hit.getId())
        .name(hit.getTitle())
        .path(hit.getSubtitle())
        .nodeType(FileNodeVO.TYPE_FILE)
        .highlight(hit.getHighlight() != null ? hit.getHighlight() : hit.getSnippet())
        .score(hit.getScore())
        .tags(hit.getTags())
        .updatedAt(hit.getUpdatedAt())
        .build();
  }
}
