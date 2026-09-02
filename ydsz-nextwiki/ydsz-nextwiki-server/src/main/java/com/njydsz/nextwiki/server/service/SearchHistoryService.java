package com.njydsz.nextwiki.server.service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

/**
 * 搜索历史与热门搜索服务。
 *
 * <p>基于 Redis 实现：
 *
 * <ul>
 *   <li>搜索历史：每个用户保留最近 20 条搜索记录（Redis List，TTL 30 天）
 *   <li>热门搜索：全局搜索热度排行（Redis Sorted Set，按搜索次数排序）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchHistoryService {

  /** 用户搜索历史缓存前缀 */
  private static final String KEY_SEARCH_HISTORY = "nw:search:history:";

  /** 热门搜索 Sorted Set 键 */
  private static final String KEY_HOT_SEARCHES = "nw:search:hot";

  /** 搜索历史最大条数 */
  private static final int MAX_HISTORY_SIZE = 20;

  /** 热门搜索返回条数 */
  private static final int HOT_SEARCHES_LIMIT = 10;

  /** 搜索历史 TTL：30 天 */
  private static final Duration HISTORY_TTL = Duration.ofDays(30);

  /** 热门搜索 TTL：7 天（滑动窗口） */
  private static final Duration HOT_TTL = Duration.ofDays(7);

  private final StringRedisTemplate redisTemplate;

  /**
   * 记录用户搜索行为。
   *
   * <p>同时写入用户搜索历史列表和热门搜索 Sorted Set。
   *
   * @param userId 用户 ID
   * @param keyword 搜索关键词
   */
  public void recordSearch(String userId, String keyword) {
    if (userId == null || userId.isBlank() || keyword == null || keyword.isBlank()) {
      return;
    }

    String normalizedKeyword = keyword.trim();
    if (normalizedKeyword.length() > 100) {
      normalizedKeyword = normalizedKeyword.substring(0, 100);
    }

    try {
      // 写入用户搜索历史（去重 + 去头部重复 + 裁剪）
      String historyKey = KEY_SEARCH_HISTORY + userId;
      // 先移除相同关键词（避免重复）
      redisTemplate.opsForList().remove(historyKey, 0, normalizedKeyword);
      // 左侧插入（最新的在前面）
      redisTemplate.opsForList().leftPush(historyKey, normalizedKeyword);
      // 裁剪到最大条数
      redisTemplate.opsForList().trim(historyKey, 0, MAX_HISTORY_SIZE - 1);
      // 设置 TTL
      redisTemplate.expire(historyKey, HISTORY_TTL);

      // 增加热门搜索计数
      redisTemplate.opsForZSet().incrementScore(KEY_HOT_SEARCHES, normalizedKeyword, 1);
      redisTemplate.expire(KEY_HOT_SEARCHES, HOT_TTL);

      log.debug("[SearchHistoryService] 记录搜索: userId={}, keyword={}", userId, normalizedKeyword);
    } catch (Exception e) {
      log.warn("[SearchHistoryService] 记录搜索失败: userId={}, keyword={}, err={}",
          userId, normalizedKeyword, e.getMessage());
    }
  }

  /**
   * 获取用户搜索历史列表。
   *
   * @param userId 用户 ID
   * @return 搜索历史列表（最新在前），无记录返回空列表
   */
  public List<String> getUserHistory(String userId) {
    if (userId == null || userId.isBlank()) {
      return Collections.emptyList();
    }

    try {
      List<String> history = redisTemplate.opsForList().range(KEY_SEARCH_HISTORY + userId, 0, -1);
      return history != null ? history : Collections.emptyList();
    } catch (Exception e) {
      log.warn("[SearchHistoryService] 获取搜索历史失败: userId={}, err={}", userId, e.getMessage(), e);
      return Collections.emptyList();
    }
  }

  /**
   * 清除用户搜索历史。
   *
   * @param userId 用户 ID
   */
  public void clearUserHistory(String userId) {
    if (userId == null || userId.isBlank()) {
      return;
    }

    try {
      redisTemplate.delete(KEY_SEARCH_HISTORY + userId);
      log.debug("[SearchHistoryService] 清除搜索历史: userId={}", userId);
    } catch (Exception e) {
      log.warn("[SearchHistoryService] 清除搜索历史失败: userId={}, err={}", userId, e.getMessage(), e);
    }
  }

  /**
   * 获取热门搜索列表。
   *
   * @return 热门搜索词列表（按热度降序），无记录返回空列表
   */
  public List<Map.Entry<String, Double>> getHotSearches() {
    try {
      ZSetOperations<String, String> zSet = redisTemplate.opsForZSet();
      var range = zSet.reverseRangeWithScores(KEY_HOT_SEARCHES, 0, HOT_SEARCHES_LIMIT - 1);
      if (range == null || range.isEmpty()) {
        return Collections.emptyList();
      }
      return range.stream()
          .map(entry -> Map.<String, Double>entry(entry.getValue(), entry.getScore()))
          .collect(Collectors.toList());
    } catch (Exception e) {
      log.warn("[SearchHistoryService] 获取热门搜索失败: err={}", e.getMessage(), e);
      return Collections.emptyList();
    }
  }
}
