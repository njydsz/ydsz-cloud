package com.njydsz.common.search.analytics;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 搜索分析服务
 * <p>
 * 记录搜索日志、热门搜索词、零结果关键词，为搜索体验优化提供数据支撑。
 * <p>
 * Redis 持久化：热门词用 Sorted Set，零结果词用 Sorted Set，每日量用 Hash。
 * Redis 不可用时自动降级到内存存储。
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

    public List<HotKeyword> getHotKeywords(int limit) {
        StringRedisTemplate redis = getRedis();
        if (redis != null) {
            try {
                var tuples = redis.opsForZSet().reverseRangeWithScores(HOT_KEYWORDS_KEY, 0, limit - 1);
                if (tuples != null) {
                    return tuples.stream()
                            .map(t -> new HotKeyword(
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

    public List<HotKeyword> getZeroResultKeywords(int limit) {
        StringRedisTemplate redis = getRedis();
        if (redis != null) {
            try {
                var tuples = redis.opsForZSet().reverseRangeWithScores(ZERO_RESULT_KEY, 0, limit - 1);
                if (tuples != null) {
                    return tuples.stream()
                            .map(t -> new HotKeyword(
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

    public Map<LocalDate, Long> getDailySearches(int days) {
        StringRedisTemplate redis = getRedis();
        if (redis != null) {
            try {
                Map<Object, Object> entries = redis.opsForHash().entries(DAILY_SEARCHES_KEY);
                if (entries != null && !entries.isEmpty()) {
                    LocalDate threshold = LocalDate.now().minusDays(days);
                    return entries.entrySet().stream()
                            .filter(e -> {
                                LocalDate date = LocalDate.parse(e.getKey().toString());
                                return !date.isBefore(threshold);
                            })
                            .sorted((a, b) -> a.getKey().toString().compareTo(b.getKey().toString()))
                            .collect(Collectors.toMap(
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
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().get(),
                        (a, b) -> a,
                        LinkedHashMap::new));
    }

    public SearchAnalyticsSummary getSummary() {
        long totalSearches = hotKeywords.values().stream().mapToLong(AtomicLong::get).sum();
        long totalZeroResults = zeroResultKeywords.values().stream().mapToLong(AtomicLong::get).sum();
        double zeroResultRate = totalSearches > 0 ? (double) totalZeroResults / totalSearches : 0.0;
        return new SearchAnalyticsSummary(totalSearches, totalZeroResults, zeroResultRate,
                hotKeywords.size(), zeroResultKeywords.size());
    }

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

    public record HotKeyword(String keyword, long count) {
    }

    public record SearchAnalyticsSummary(
            long totalSearches,
            long zeroResultSearches,
            double zeroResultRate,
            int uniqueKeywords,
            int zeroResultKeywords
    ) {
    }
}
