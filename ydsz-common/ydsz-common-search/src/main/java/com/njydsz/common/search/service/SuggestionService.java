package com.njydsz.common.search.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.search.analytics.SearchAnalyticsService;
import com.njydsz.common.search.api.SearchSuggestion;
import com.njydsz.common.search.config.SearchProperties;
import com.njydsz.common.search.core.SearchEngineRegistry;
import com.njydsz.common.search.core.SuggestStrategy;

/**
 * 搜索建议服务。
 *
 * <p>整合三种策略提升搜索建议覆盖率：
 *
 * <ol>
 *   <li><b>引擎前缀建议</b>：调用底层 SuggestStrategy 获取数据库候选
 *   <li><b>热门搜索兜底</b>：引擎结果不足时，从分析服务获取热门词推荐
 *   <li><b>Levenshtein 纠错</b>：零结果场景下按编辑距离生成「您是不是要找」
 * </ol>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class SuggestionService {

  private final SearchEngineRegistry engineRegistry;
  private final SearchAnalyticsService analyticsService;
  private final SearchProperties properties;

  public SuggestionService(
      SearchEngineRegistry engineRegistry,
      SearchAnalyticsService analyticsService,
      SearchProperties properties) {
    this.engineRegistry = engineRegistry;
    this.analyticsService = analyticsService;
    this.properties = properties;
  }

  /**
   * 根据输入前缀返回自动补全候选词。
   *
   * <p>召回策略分两轮：先向底层 {@link SuggestStrategy} 多取一倍候选 （{@code suggestLimit * 2}），第一轮只保留前缀匹配或包含匹配的词；
   * 若数量不足 {@code suggestLimit}，第二轮用剩余候选去重补齐， 以避免用户在输入过程中看到空下拉框。
   *
   * <p>引擎结果不足时，从热门搜索中补充与输入相关的词。
   *
   * <p><b>降级策略</b>：未注册 {@link SuggestStrategy}、或底层查询抛出任何异常时， 均返回空列表并记 warn
   * 日志，不向上抛异常——补全属于体验增强功能，不应阻断搜索。
   *
   * @param prefix 用户已输入的前缀，为 {@code null} 或空白时直接返回空列表；内部按 trim + 小写归一化
   * @return 候选词列表，最多 {@code ydsz.search.suggest-limit} 条；无结果时返回空列表而非 {@code null}
   */
  public List<String> autocomplete(String prefix) {
    if (prefix == null || prefix.isBlank()) {
      return Collections.emptyList();
    }
    try {
      Optional<SuggestStrategy> suggestStrategy = engineRegistry.getSuggestStrategy();
      if (suggestStrategy.isEmpty()) {
        return Collections.emptyList();
      }
      String normalizedPrefix = prefix.trim().toLowerCase();
      List<String> results = new ArrayList<>();

      // 策略 1：引擎前缀建议
      SearchSuggestion suggestion =
          suggestStrategy.get().suggest(normalizedPrefix, properties.getSuggestLimit() * 2);
      if (suggestion != null && suggestion.getSuggestions() != null) {
        for (String s : suggestion.getSuggestions()) {
          if (s != null && !s.isBlank()) {
            String lower = s.toLowerCase();
            if (lower.startsWith(normalizedPrefix) || lower.contains(normalizedPrefix)) {
              results.add(s);
            }
          }
        }
        // 不足时用剩余候选补齐
        if (results.size() < properties.getSuggestLimit()) {
          for (String s : suggestion.getSuggestions()) {
            if (s != null && !s.isBlank() && !results.contains(s)) {
              results.add(s);
            }
            if (results.size() >= properties.getSuggestLimit()) {
              break;
            }
          }
        }
      }

      // 策略 2：热门搜索兜底
      if (results.size() < properties.getSuggestLimit()) {
        int remainSlots = properties.getSuggestLimit() - results.size();
        List<String> hotKeywords = getHotKeywordFallback(normalizedPrefix, results, remainSlots);
        results.addAll(hotKeywords);
      }

      return results.stream()
          .distinct()
          .limit(properties.getSuggestLimit())
          .collect(Collectors.toList());
    } catch (Exception e) {
      log.warn("[SuggestionService] 自动补全失败: prefix={}", prefix, e);
      return Collections.emptyList();
    }
  }

  /**
   * 为疑似拼写错误的关键词生成「您是不是要找」纠错建议。
   *
   * <p>算法：取关键词前 2 个字符作为宽松前缀向底层多召回一批候选 （{@code suggestLimit * 3}），再按与原词的 Levenshtein 编辑距离升序排序并过滤。
   * 编辑距离阈值随词长放宽：长度 ≤2 允许 1，≤5 允许 2，更长允许 3， 兼顾短词的精确性与长词的容错性。原词自身会被排除。
   *
   * <p>引擎无结果时，从热门词中找相似的作为兜底。
   *
   * <p><b>降级策略</b>：未注册 {@link SuggestStrategy} 或底层异常时返回空列表并记 warn 日志， 不向上抛异常。
   *
   * @param keyword 用户输入的原始关键词，通常是零结果查询词；为 {@code null} 或空白时返回空列表
   * @return 按相似度升序排列的纠错候选，最多 {@code suggestLimit} 条；无合适候选时返回空列表而非 {@code null}
   */
  public List<String> didYouMean(String keyword) {
    if (keyword == null || keyword.isBlank()) {
      return Collections.emptyList();
    }
    try {
      Optional<SuggestStrategy> suggestStrategy = engineRegistry.getSuggestStrategy();
      if (suggestStrategy.isEmpty()) {
        return Collections.emptyList();
      }
      String normalizedKeyword = keyword.trim().toLowerCase();
      String loosePrefix = keyword.length() > 2 ? keyword.substring(0, 2) : keyword;
      SearchSuggestion suggestion =
          suggestStrategy.get().suggest(loosePrefix, properties.getSuggestLimit() * 3);
      if (suggestion == null || suggestion.getSuggestions() == null) {
        return Collections.emptyList();
      }

      List<String> candidates =
          suggestion.getSuggestions().stream()
              .filter(s -> s != null && !s.isBlank() && !s.equalsIgnoreCase(keyword))
              .sorted(
                  (a, b) ->
                      Integer.compare(
                          levenshtein(normalizedKeyword, a.toLowerCase()),
                          levenshtein(normalizedKeyword, b.toLowerCase())))
              .filter(
                  s -> levenshtein(normalizedKeyword, s.toLowerCase()) <= maxEditDistance(keyword))
              .limit(properties.getSuggestLimit())
              .collect(Collectors.toList());

      // 兜底：从热门词中找相似的
      if (candidates.isEmpty()) {
        candidates =
            analyticsService.getHotKeywords(20).stream()
                .map(SearchAnalyticsService.HotKeyword::keyword)
                .filter(k -> !k.equalsIgnoreCase(keyword))
                .sorted(
                    (a, b) ->
                        Integer.compare(
                            levenshtein(normalizedKeyword, a.toLowerCase()),
                            levenshtein(normalizedKeyword, b.toLowerCase())))
                .limit(properties.getSuggestLimit())
                .collect(Collectors.toList());
      }
      return candidates;
    } catch (Exception e) {
      log.warn("[SuggestionService] 纠错建议失败: keyword={}", keyword, e);
      return Collections.emptyList();
    }
  }

  // ==================== 私有方法 ====================

  /**
   * 热门搜索兜底逻辑：返回与输入相关且已有的热门搜索词。
   *
   * @param prefix 归一化输入前缀
   * @param existing 已有候选（用于去重）
   * @param limit 返回上限
   * @return 热门词列表
   */
  private List<String> getHotKeywordFallback(String prefix, List<String> existing, int limit) {
    try {
      return analyticsService.getHotKeywords(limit * 2).stream()
          .map(SearchAnalyticsService.HotKeyword::keyword)
          .filter(k -> !existing.contains(k))
          .filter(k -> k.toLowerCase().startsWith(prefix) || k.toLowerCase().contains(prefix))
          .limit(limit)
          .collect(Collectors.toList());
    } catch (Exception e) {
      return Collections.emptyList();
    }
  }

  private int levenshtein(String s1, String s2) {
    int len1 = s1.length();
    int len2 = s2.length();
    if (len1 == 0) {
      return len2;
    }
    if (len2 == 0) {
      return len1;
    }

    int[] prev = new int[len2 + 1];
    int[] curr = new int[len2 + 1];
    for (int j = 0; j <= len2; j++) {
      prev[j] = j;
    }

    for (int i = 1; i <= len1; i++) {
      curr[0] = i;
      for (int j = 1; j <= len2; j++) {
        int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
        curr[j] = Math.min(Math.min(prev[j] + 1, prev[j - 1] + cost), curr[j - 1] + 1);
      }
      int[] tmp = prev;
      prev = curr;
      curr = tmp;
    }
    return prev[len2];
  }

  private int maxEditDistance(String keyword) {
    int len = keyword.length();
    if (len <= 2) {
      return 1;
    }
    if (len <= 5) {
      return 2;
    }
    return 3;
  }
}
