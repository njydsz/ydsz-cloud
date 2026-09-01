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
import com.njydsz.nextwiki.api.dto.NextwikiDto;
import com.njydsz.nextwiki.domain.dto.SearchIndexDTO;
import com.njydsz.nextwiki.domain.query.SearchIndexQuery;
import com.njydsz.nextwiki.domain.query.SearchQuery;
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
 * @since 26.09.01
 * @see com.njydsz.common.search.service.UnifiedSearchService 统一搜索服务
 * @see SearchDomainService DB 降级搜索
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
  /** 高级搜索语法解析器（S3-P2-02） */
  private final SearchQueryParser searchQueryParser;

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
  public SearchResultVO searchWithFilters(NextwikiDto.SearchRequest request, String userId) {
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

  /** 每页大小（分页重建索引批次） */
  private static final int REBUILD_BATCH_SIZE = 200;

  /**
   * 重建全量搜索索引（通常由定时任务或运维操作触发）。
   *
   * <p>分页批次遍历全部文件节点，构建搜索索引 DTO 并 upsert 到 DB 降级索引表
   * （统一搜索引擎主索引由 {@code NextwikiScheduledJobs#rebuildSearchIndex} 通过
   * {@code IndexRebuildService} 维护）。双索引链路在此保持同步。
   *
   * <p><b>S3-P2-3 优化：</b>采用分页批次处理，避免一次性全量加载导致 OOM。
   *
   * @complexity O(N)（N 为文件总数，分页批次遍历重建索引，耗时较长）
   * @note 非事务（批量操作）；执行期间建议避开高峰期，避免影响在线搜索
   * @see com.njydsz.nextwiki.server.job.NextwikiScheduledJobs#rebuildSearchIndex()
   */
  public void rebuildAllIndices() {
    int offset = 0;
    int totalRebuilt = 0;
    long totalProcessed = 0;

    while (true) {
      PageResponse<List<FileNodeVO>> pageResult = fileNodeRepository.findAllWithPage(offset, REBUILD_BATCH_SIZE);
      List<FileNodeVO> batch = pageResult.getData();
      if (batch == null || batch.isEmpty()) {
        break;
      }

      for (FileNodeVO node : batch) {
        try {
          List<TagVO> tags = tagRepository.findByFileNodeId(node.getId());
          SearchIndexDTO dto = searchDomainService.buildSearchIndex(node, tags, null, null);
          searchIndexRepository.upsert(dto);
          totalRebuilt++;
        } catch (Exception e) {
          log.warn(
              "[SearchApplicationService] 索引重建跳过节点: nodeId={}, error={}",
              node.getId(),
              e.getMessage());
        }
      }

      totalProcessed += batch.size();
      offset += REBUILD_BATCH_SIZE;

      // 如果本批次未满，说明已到末页
      if (batch.size() < REBUILD_BATCH_SIZE) {
        break;
      }
    }

    log.info("[SearchApplicationService] 全量索引重建完成: total={}, rebuilt={}", totalProcessed, totalRebuilt);
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

  // ==================== 高级语法搜索（S3-P2-02） ====================

  /**
   * 高级语法搜索（支持字段限定、布尔运算、短语精确匹配）。
   *
   * <p>解析用户输入的高级搜索语法：
   *
   * <ul>
   *   <li>字段限定：{@code name:报告}、{@code tag:重要}、{@code suffix:pdf}
   *   <li>短语精确匹配：{@code "季度财务"}
   *   <li>包含/排除：{@code +必须}、{@code -排除}
   *   <li>布尔运算符：{@code AND}、{@code OR}、{@code NOT}
   * </ul>
   *
   * <p>搜索引擎可用时走统一检索（高亮/权重），否则降级 DB 高级查询。
   *
   * @param rawInput 用户原始搜索输入
   * @param userId 当前用户 ID（权限过滤）
   * @param scope 搜索作用域（all / filename / content / tag）
   * @param page 页码
   * @param pageSize 每页大小
   * @return 分页搜索结果
   * @complexity 引擎路径 O(query + filters)；DB 路径 O(parsed_query)
   */
  public SearchResultVO searchWithAdvancedSyntax(
      String rawInput, String userId, String scope, int page, int pageSize) {
    // 1. 解析高级搜索语法
    SearchQuery searchQuery = searchQueryParser.parse(rawInput, userId, scope, page, pageSize);

    // 2. 判断走引擎还是 DB 降级
    SearchEngineRegistry registry = engineRegistryProvider.getIfAvailable();
    UnifiedSearchService unifiedSearch = unifiedSearchServiceProvider.getIfAvailable();

    SearchResultVO result;
    if (registry != null && unifiedSearch != null && registry.isPrimaryAvailable()) {
      result = searchViaEngineAdvanced(unifiedSearch, searchQuery, userId);
    } else {
      log.info("[SearchApplicationService] 高级语法搜索降级 DB: rawInput={}", rawInput);
      result = searchViaDatabaseAdvanced(searchQuery);
    }

    // 3. 记录搜索历史
    if (rawInput != null && !rawInput.isBlank()) {
      searchHistoryService.recordSearch(userId, rawInput);
    }
    return result;
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

  // ==================== 高级语法搜索私有方法（S3-P2-02） ====================

  /**
   * 高级语法搜索引擎路径：将解析后的查询转换为引擎 SearchFilter。
   *
   * <p>注意：统一搜索引擎的 {@link SearchFilter.Operator} 仅支持基础比较操作（EQ/IN/NOT_IN 等），
   * 不支持 CONTAINS/PHRASE。因此引擎路径仅处理：
   *
   * <ul>
   *   <li>suffix 字段 → IN 过滤
   *   <li>必须排除词 → NOT_IN 过滤
   *   <li>全文词/短语 → 拼接为 keyword 由引擎全文检索
   * </ul>
   *
   * <p>更复杂的语法（字段限定模糊匹配、短语精确匹配）仅在 DB 降级路径生效。
   *
   * @param unifiedSearch 统一搜索服务
   * @param searchQuery 解析后的搜索查询
   * @param userId 操作人 ID
   * @return 分页搜索结果
   */
  private SearchResultVO searchViaEngineAdvanced(
      UnifiedSearchService unifiedSearch, SearchQuery searchQuery, String userId) {
    // 将高级语法转换为引擎的 SearchFilter 列表
    List<SearchFilter> filters = new ArrayList<>();

    // suffix 字段限定 → IN 过滤
    if (searchQuery.getFieldQueries() != null) {
      List<String> suffixValues = searchQuery.getFieldQueries().stream()
          .filter(fq -> "suffix".equals(fq.getField()))
          .map(SearchQuery.FieldQuery::getValue)
          .collect(Collectors.toList());
      if (!suffixValues.isEmpty()) {
        filters.add(SearchFilter.builder()
            .field("suffix")
            .values(suffixValues)
            .operator(Operator.IN)
            .build());
      }
    }

    // 必须排除词 → NOT_IN 过滤（content 字段不支持 NOT_IN，此处作为 tag 排除示例）
    // 注：统一搜索引擎暂不支持全文 NOT，复杂排除逻辑在 DB 路径处理

    // 合并全文词 + 短语作为引擎 keyword
    StringBuilder keywordBuilder = new StringBuilder();
    if (searchQuery.getFullTextTerms() != null) {
      keywordBuilder.append(String.join(" ", searchQuery.getFullTextTerms()));
    }
    if (searchQuery.getPhrases() != null) {
      for (String phrase : searchQuery.getPhrases()) {
        if (keywordBuilder.length() > 0) {
          keywordBuilder.append(' ');
        }
        keywordBuilder.append('"').append(phrase).append('"');
      }
    }

    // 必须包含词追加到 keyword（搜索引擎默认 AND 语义）
    if (searchQuery.getMustIncludeTerms() != null) {
      for (String term : searchQuery.getMustIncludeTerms()) {
        if (keywordBuilder.length() > 0) {
          keywordBuilder.append(' ');
        }
        keywordBuilder.append('+').append(term);
      }
    }

    String keyword = keywordBuilder.toString();

    SearchRequest request = SearchRequest.builder()
        .keyword(keyword)
        .types(List.of("wiki"))
        .page(searchQuery.getPage())
        .pageSize(searchQuery.getPageSize())
        .userId(userId)
        .highlight(true)
        .filters(filters)
        .build();

    try {
      SearchResponse response = unifiedSearch.search(request);
      return convertResponse(response);
    } catch (Exception e) {
      log.warn("[SearchApplicationService] 高级语法引擎检索异常，降级 DB: error={}", e.getMessage());
      return searchViaDatabaseAdvanced(searchQuery);
    }
  }

  /**
   * 高级语法搜索 DB 降级路径：直接使用 searchAdvanced 查询。
   *
   * @param searchQuery 解析后的搜索查询
   * @return 分页搜索结果
   */
  private SearchResultVO searchViaDatabaseAdvanced(SearchQuery searchQuery) {
    PageResponse<List<SearchIndexVO>> pageResult = searchIndexRepository.searchAdvanced(searchQuery);
    List<SearchIndexVO> indices = pageResult.getData();
    long total = pageResult.getTotal() != null ? pageResult.getTotal() : 0L;

    // 合并所有关键词用于评分/高亮
    StringBuilder allKeywords = new StringBuilder();
    if (searchQuery.getFullTextTerms() != null) {
      allKeywords.append(String.join(" ", searchQuery.getFullTextTerms()));
    }

    return searchDomainService.search(
        indices != null ? indices : List.of(),
        total,
        allKeywords.toString(),
        searchQuery.getPage(),
        searchQuery.getPageSize());
  }
}
