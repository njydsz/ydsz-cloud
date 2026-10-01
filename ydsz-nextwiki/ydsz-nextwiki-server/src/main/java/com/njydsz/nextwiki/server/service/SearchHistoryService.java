package com.njydsz.nextwiki.server.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.search.analytics.SearchAnalyticsService;

/**
 * 搜索历史与热门搜索服务。
 *
 * <p>已{@code @Deprecated}，统一收敛到 {@link SearchAnalyticsService}。
 * 当前实现委托给 {@link SearchAnalyticsService#recordUserSearchHistory} /
 * {@link SearchAnalyticsService#getUserSearchHistory} / {@link SearchAnalyticsService#clearUserSearchHistory}。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @deprecated 使用 {@link SearchAnalyticsService} 替代（P1-4 收敛）
 */
@Slf4j
@Service
@Deprecated
@RequiredArgsConstructor
public class SearchHistoryService {

  /** 热门搜索返回条数 */
  private static final int HOT_SEARCHES_LIMIT = 10;

  private final SearchAnalyticsService searchAnalyticsService;

  /**
   * 记录用户搜索行为。
   *
   * <p>委托 {@link SearchAnalyticsService#recordUserSearchHistory}。
   *
   * @param userId 用户 ID
   * @param keyword 搜索关键词
   */
  public void recordSearch(String userId, String keyword) {
    searchAnalyticsService.recordUserSearchHistory(userId, keyword);
  }

  /**
   * 获取用户搜索历史列表。
   *
   * <p>委托 {@link SearchAnalyticsService#getUserSearchHistory}。
   *
   * @param userId 用户 ID
   * @return 搜索历史列表（最新在前），无记录返回空列表
   */
  public List<String> getUserHistory(String userId) {
    return searchAnalyticsService.getUserSearchHistory(userId);
  }

  /**
   * 清除用户搜索历史。
   *
   * <p>委托 {@link SearchAnalyticsService#clearUserSearchHistory}。
   *
   * @param userId 用户 ID
   */
  public void clearUserHistory(String userId) {
    searchAnalyticsService.clearUserSearchHistory(userId);
  }

  /**
   * 获取热门搜索列表。
   *
   * @return 热门搜索词列表（按热度降序），无记录返回空列表
   */
  public List<Map.Entry<String, Double>> getHotSearches() {
    return searchAnalyticsService.getHotKeywords(HOT_SEARCHES_LIMIT).stream()
        .map(hk -> Map.entry(hk.keyword(), (double) hk.count()))
        .collect(Collectors.toList());
  }
}
