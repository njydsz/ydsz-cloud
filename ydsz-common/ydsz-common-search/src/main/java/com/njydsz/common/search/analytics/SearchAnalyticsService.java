package com.njydsz.common.search.analytics;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import lombok.extern.slf4j.Slf4j;

/**
 * 搜索分析服务
 *
 * <p>记录搜索日志、热门搜索词、零结果关键词，为搜索体验优化提供数据支撑。
 *
 * <p>Redis 持久化：热门词用 Sorted Set，零结果词用 Sorted Set，每日量用 Hash。 Redis 不可用时自动降级到内存存储。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class SearchAnalyticsService {

  private static final String HOT_KEYWORDS_KEY = "search:analytics:hot";
  private static final String ZERO_RESULT_KEY = "search:analytics:zero";
  private static final String DAILY_SEARCHES_KEY = "search:analytics:daily";
  private static final int MAX_HOT_KEYWORDS = 1000;

  private final ObjectProvider<StringRedisTemplate> redisProvider;

  private final Map<String, AtomicLong> hotKeywords = new ConcurrentHashMap<>();
  private final Map<String, AtomicLong> zeroResultKeywords = new ConcurrentHashMap<>();
  private final Map<LocalDate, AtomicLong> dailySearches = new ConcurrentHashMap<>();

  public SearchAnalyticsService(ObjectProvider<StringRedisTemplate> redisProvider) {
    this.redisProvider = redisProvider;
  }

  /**
   * 记录一次搜索行为，用于沉淀热门词、零结果词与每日搜索量。
   *
   * <p>关键词会先做 {@code trim + toLowerCase} 归一化，避免大小写与空格造成统计分裂。 空白关键词直接忽略，不计入任何指标。
   *
   * <p><b>存储与降级</b>：优先写 Redis （热门词 {@code search:analytics:hot}、零结果词 {@code search:analytics:zero}
   * 均为 Sorted Set，每日量 {@code search:analytics:daily} 为 Hash， 这些 key 不设 TTL，需由运维侧定期归档）； Redis
   * 未装配或写入抛异常时静默降级到内存 Map，仅打 debug 日志， <b>绝不向调用方抛异常</b>，保证埋点失败不影响主搜索链路。
   *
   * <p>内存模式下热门词与零结果词各自最多保留 {@code 1000} 条， 超限时淘汰计数最低的一半；每日量只保留最近 30 天。
   *
   * <p>线程安全：底层为 {@link ConcurrentHashMap} + {@link AtomicLong}，可并发调用。
   *
   * @param keyword 用户输入的原始搜索词，为 {@code null} 或空白时静默跳过
   * @param resultCount 本次搜索命中的结果总数，取 0 时额外计入零结果词统计
   */
  public void recordSearch(String keyword, long resultCount) {
    if (keyword == null || keyword.isBlank()) {
      return;
    }
    String normalized = keyword.trim().toLowerCase();

    StringRedisTemplate redis = getRedis();
    if (redis != null) {
      recordToRedis(redis, normalized, resultCount);
    } else {
      recordToMemory(normalized, resultCount);
    }
  }

  private void recordToRedis(StringRedisTemplate redis, String normalized, long resultCount) {
    try {
      redis.opsForZSet().incrementScore(HOT_KEYWORDS_KEY, normalized, 1.0);
      if (resultCount == 0) {
        redis.opsForZSet().incrementScore(ZERO_RESULT_KEY, normalized, 1.0);
      }
      String today = LocalDate.now().toString();
      redis.opsForHash().increment(DAILY_SEARCHES_KEY, today, 1L);
    } catch (Exception e) {
      log.debug("[SearchAnalytics] Redis 写入失败，降级到内存", e);
      recordToMemory(normalized, resultCount);
    }
  }

  private void recordToMemory(String normalized, long resultCount) {
    hotKeywords.computeIfAbsent(normalized, k -> new AtomicLong(0)).incrementAndGet();
    if (hotKeywords.size() > MAX_HOT_KEYWORDS) {
      evictLowCountEntries(hotKeywords);
    }
    if (resultCount == 0) {
      zeroResultKeywords.computeIfAbsent(normalized, k -> new AtomicLong(0)).incrementAndGet();
      if (zeroResultKeywords.size() > MAX_HOT_KEYWORDS) {
        evictLowCountEntries(zeroResultKeywords);
      }
    }
    dailySearches.computeIfAbsent(LocalDate.now(), d -> new AtomicLong(0)).incrementAndGet();
    if (dailySearches.size() > 30) {
      LocalDate threshold = LocalDate.now().minusDays(30);
      dailySearches.entrySet().removeIf(e -> e.getKey().isBefore(threshold));
    }
  }

  /**
   * 获取热搜关键词排行。
   *
   * <p>优先从 Redis ZSet 读取；Redis 不可用时降级到本地内存统计， 保证分析面板在缓存故障时仍可用。
   *
   * @param limit 返回条数上限
   * @return 按搜索次数降序的热搜词列表；无数据时返回空列表
   */
  public List<HotKeyword> getHotKeywords(int limit) {
    StringRedisTemplate redis = getRedis();
    if (redis != null) {
      try {
        var tuples = redis.opsForZSet().reverseRangeWithScores(HOT_KEYWORDS_KEY, 0, limit - 1);
        if (tuples != null) {
          return tuples.stream()
              .map(
                  t ->
                      new HotKeyword(
                          t.getValue() != null ? t.getValue() : "",
                          t.getScore() != null ? t.getScore().longValue() : 0L))
              .collect(Collectors.toList());
        }
      } catch (Exception e) {
        log.debug("[SearchAnalytics] Redis 读取热门词失败，降级到内存", e);
      }
    }
    return hotKeywords.entrySet().stream()
        .sorted((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()))
        .limit(limit)
        .map(e -> new HotKeyword(e.getKey(), e.getValue().get()))
        .collect(Collectors.toList());
  }

  /**
   * 获取零结果关键词排行（用于优化搜索建议与内容补全）。
   *
   * <p>同样优先 Redis、降级内存，行为与 {@link #getHotKeywords(int)} 一致。
   *
   * @param limit 返回条数上限
   * @return 按零结果次数降序的关键词列表；无数据时返回空列表
   */
  public List<HotKeyword> getZeroResultKeywords(int limit) {
    StringRedisTemplate redis = getRedis();
    if (redis != null) {
      try {
        var tuples = redis.opsForZSet().reverseRangeWithScores(ZERO_RESULT_KEY, 0, limit - 1);
        if (tuples != null) {
          return tuples.stream()
              .map(
                  t ->
                      new HotKeyword(
                          t.getValue() != null ? t.getValue() : "",
                          t.getScore() != null ? t.getScore().longValue() : 0L))
              .collect(Collectors.toList());
        }
      } catch (Exception e) {
        log.debug("[SearchAnalytics] Redis 读取零结果词失败，降级到内存", e);
      }
    }
    return zeroResultKeywords.entrySet().stream()
        .sorted((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()))
        .limit(limit)
        .map(e -> new HotKeyword(e.getKey(), e.getValue().get()))
        .collect(Collectors.toList());
  }

  /**
   * 获取近 N 天的每日搜索量。
   *
   * <p>从 Redis Hash 读取每日计数，仅返回最近 {@code days} 天内的数据； Redis 不可用时返回空 Map。
   *
   * @param days 统计天数范围
   * @return 日期 → 搜索次数的映射；无数据时返回空 Map
   */
  public Map<LocalDate, Long> getDailySearches(int days) {
    StringRedisTemplate redis = getRedis();
    if (redis != null) {
      try {
        Map<Object, Object> entries = redis.opsForHash().entries(DAILY_SEARCHES_KEY);
        if (entries != null && !entries.isEmpty()) {
          LocalDate threshold = LocalDate.now().minusDays(days);
          return entries.entrySet().stream()
              .filter(
                  e -> {
                    LocalDate date = LocalDate.parse(e.getKey().toString());
                    return !date.isBefore(threshold);
                  })
              .sorted((a, b) -> a.getKey().toString().compareTo(b.getKey().toString()))
              .collect(
                  Collectors.toMap(
                      e -> LocalDate.parse(e.getKey().toString()),
                      e -> Long.parseLong(e.getValue().toString()),
                      (a, b) -> a,
                      LinkedHashMap::new));
        }
      } catch (Exception e) {
        log.debug("[SearchAnalytics] Redis 读取每日量失败，降级到内存", e);
      }
    }
    LocalDate threshold = LocalDate.now().minusDays(days);
    return dailySearches.entrySet().stream()
        .filter(e -> !e.getKey().isBefore(threshold))
        .sorted(Map.Entry.comparingByKey())
        .collect(
            Collectors.toMap(
                Map.Entry::getKey, e -> e.getValue().get(), (a, b) -> a, LinkedHashMap::new));
  }

  /**
   * 获取搜索分析汇总（内存统计，不依赖 Redis）。
   *
   * @return 汇总数据：总搜索量、零结果量、零结果率、去重关键词数与零结果关键词数
   */
  public SearchAnalyticsSummary getSummary() {
    long totalSearches = hotKeywords.values().stream().mapToLong(AtomicLong::get).sum();
    long totalZeroResults = zeroResultKeywords.values().stream().mapToLong(AtomicLong::get).sum();
    double zeroResultRate = totalSearches > 0 ? (double) totalZeroResults / totalSearches : 0.0;
    return new SearchAnalyticsSummary(
        totalSearches,
        totalZeroResults,
        zeroResultRate,
        hotKeywords.size(),
        zeroResultKeywords.size());
  }

  /**
   * 清空全部搜索分析数据（Redis 与内存双端）。
   *
   * <p>属于<b>不可逆</b>操作，会删除三个 Redis key 并清空内存 Map， 仅应由运维接口或测试用例调用，切勿放在业务链路中。
   *
   * <p>Redis 删除失败时只记 debug 日志并继续清理内存， 因此可能出现「内存已清空但 Redis 仍有残留」的中间态，调用方需可接受该不一致。
   */
  public void clear() {
    StringRedisTemplate redis = getRedis();
    if (redis != null) {
      try {
        redis.delete(HOT_KEYWORDS_KEY);
        redis.delete(ZERO_RESULT_KEY);
        redis.delete(DAILY_SEARCHES_KEY);
      } catch (Exception e) {
        log.debug("[SearchAnalytics] Redis 清除失败", e);
      }
    }
    hotKeywords.clear();
    zeroResultKeywords.clear();
    dailySearches.clear();
  }

  private StringRedisTemplate getRedis() {
    return redisProvider.getIfAvailable();
  }

  private void evictLowCountEntries(Map<String, AtomicLong> map) {
    int target = MAX_HOT_KEYWORDS / 2;
    map.entrySet().stream()
        .sorted((a, b) -> Long.compare(a.getValue().get(), b.getValue().get()))
        .limit(map.size() - target)
        .forEach(e -> map.remove(e.getKey()));
  }

  /**
   * 热搜关键词统计条目。
   *
   * @param keyword 关键词
   * @param count 搜索次数
   */
  public record HotKeyword(String keyword, long count) {}

  /**
   * 搜索分析汇总数据。
   *
   * @param totalSearches 总搜索次数
   * @param zeroResultSearches 零结果搜索次数
   * @param zeroResultRate 零结果率（0.0 ~ 1.0）
   * @param uniqueKeywords 去重后的关键词数量
   * @param zeroResultKeywords 零结果关键词数量
   */
  public record SearchAnalyticsSummary(
      long totalSearches,
      long zeroResultSearches,
      double zeroResultRate,
      int uniqueKeywords,
      int zeroResultKeywords) {}
}
