package com.njydsz.common.search.service;

import com.njydsz.common.search.analytics.SearchAnalyticsService;
import com.njydsz.common.search.api.SearchRequest;
import com.njydsz.common.search.config.SearchProperties;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 零结果页体验优化处理器。
 *
 * <p>当搜索返回零结果时，按优先级提供丰富的引导策略：
 *
 * <ol>
 *   <li><b>搜索词纠错</b>：基于编辑距离的「您是不是要找」
 *   <li><b>热门搜索兜底</b>：从搜索分析服务获取热门词列表引导用户
 *   <li><b>范围扩大</b>：建议用户去掉部分过滤条件重新搜索
 * </ol>
 *
 * <p>对标行业实践：
 *
 * <ul>
 *   <li>淘宝搜索：零结果时推荐相似商品 + 搜索词高亮纠错
 *   <li>Google："Did you mean: xxx" + "Showing results for xxx"
 *   <li>美团搜索：零结果展示「为你推荐」兜底内容
 * </ul>
 *
 * @author ydzs-team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class ZeroResultHandler {

  private final SuggestionService suggestionService;
  private final SearchAnalyticsService analyticsService;
  private final SearchProperties properties;

  /**
   * 零结果引导策略结果。
   *
   * @param didYouMean 纠错建议列表（编辑距离相近词）
   * @param hotKeywords 热门搜索词兜底列表
   * @param suggestRemoveFilter 建议移除的过滤条件描述
   */
  public record ZeroResultGuide(
      List<String> didYouMean, List<String> hotKeywords, String suggestRemoveFilter) {}

  /**
   * 为零结果查询生成完整的引导策略。
   *
   * <p>按优先级构建：
   *
   * <ol>
   *   <li>先尝试搜索词纠错（编辑距离 ≤ 阈值）
   *   <li>再获取热门搜索词作为兜底引导
   *   <li>如果用户使用了过滤条件，建议扩大搜索范围
   * </ol>
   *
   * @param request 导致零结果的搜索请求
   * @return 零结果引导策略，永不返回 null
   */
  public ZeroResultGuide handle(SearchRequest request) {
    String keyword = request.getKeyword();
    if (keyword == null || keyword.isBlank()) {
      return new ZeroResultGuide(Collections.emptyList(), Collections.emptyList(), null);
    }

    // 策略 1：搜索词纠错
    List<String> didYouMean = suggestionService.didYouMean(keyword);

    // 策略 2：热门搜索兜底
    List<String> hotKeywords = Collections.emptyList();
    try {
      hotKeywords =
          analyticsService.getHotKeywords(5).stream()
              .map(SearchAnalyticsService.HotKeyword::keyword)
              .filter(k -> !k.equalsIgnoreCase(keyword))
              .limit(5)
              .toList();
    } catch (Exception e) {
      log.debug("[ZeroResultHandler] 获取热门词失败: {}", e.getMessage());
    }

    // 策略 3：建议移除过滤条件
    String suggestRemoveFilter = buildFilterRemovalSuggestion(request);

    log.info(
        "[ZeroResultHandler] 零结果引导生成: keyword={}, didYouMean={}, hotKeywords={}",
        keyword,
        didYouMean.size(),
        hotKeywords.size());

    return new ZeroResultGuide(didYouMean, hotKeywords, suggestRemoveFilter);
  }

  /**
   * 构建移除过滤条件的建议描述。
   *
   * <p>当用户使用了多个过滤条件时，建议移除计数最少的过滤条件， 从而扩大搜索范围、提升命中概率。
   *
   * @param request 搜索请求
   * @return 建议描述，无过滤条件时返回 null
   */
  private String buildFilterRemovalSuggestion(SearchRequest request) {
    List<com.njydsz.common.search.api.SearchFilter> filters = request.getFilters();
    if (filters == null || filters.isEmpty()) {
      return null;
    }

    // 如果只有权限过滤（由 Provider 自动注入），不建议移除
    long userFilterCount =
        filters.stream()
            .filter(f -> f.getField() != null)
            .filter(f -> !isSystemFilter(f.getField()))
            .count();

    if (userFilterCount == 0) {
      return null;
    }

    return String.format("当前使用了 %d 个筛选条件，试试减少筛选条件搜索更多结果？", userFilterCount);
  }

  /**
   * 判断是否为系统自动注入的过滤条件（非用户主动选择）。
   *
   * @param field 字段名
   * @return 系统字段返回 true
   */
  private boolean isSystemFilter(String field) {
    return "tenant_id".equals(field) || "created_by".equals(field);
  }
}
