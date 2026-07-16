package com.njydsz.common.search.analytics;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

/**
 * 搜索分析服务
 * <p>
 * 记录搜索日志、热门搜索词、零结果关键词，为搜索体验优化提供数据支撑。
 *
 * @author ydsz-team
 * @since 1.4.0
 */
@Slf4j
public class SearchAnalyticsService {

    /** 热门搜索词统计（keyword → count） */
    private final Map<String, AtomicLong> hotKeywords = new ConcurrentHashMap<>();

    /** 零结果关键词统计 */
    private final Map<String, AtomicLong> zeroResultKeywords = new ConcurrentHashMap<>();

    /** 每日搜索量统计（date → count） */
    private final Map<LocalDate, AtomicLong> dailySearches = new ConcurrentHashMap<>();

    /** 最大保留热门词数量 */
    private static final int MAX_HOT_KEYWORDS = 1000;

    /**
     * 记录一次搜索
     *
     * @param keyword    搜索关键词
     * @param resultCount 结果数
     */
    public void recordSearch(String keyword, long resultCount) {
        if (keyword == null || keyword.isBlank()) {
            return;
        }

        String normalized = keyword.trim().toLowerCase();

        // 热门搜索词
        hotKeywords.computeIfAbsent(normalized, k -> new AtomicLong(0)).incrementAndGet();

        // P1-1: evict low-count entries when map exceeds max size
        if (hotKeywords.size() > MAX_HOT_KEYWORDS) {
            evictLowCountEntries(hotKeywords);
        }

        // 零结果关键词
        if (resultCount == 0) {
            zeroResultKeywords.computeIfAbsent(normalized, k -> new AtomicLong(0)).incrementAndGet();
            // P1-1: evict low-count entries when map exceeds max size
            if (zeroResultKeywords.size() > MAX_HOT_KEYWORDS) {
                evictLowCountEntries(zeroResultKeywords);
            }
        }

        // 每日搜索量
        dailySearches.computeIfAbsent(LocalDate.now(), d -> new AtomicLong(0)).incrementAndGet();

        // 清理过期的每日统计（只保留最近 30 天）
        if (dailySearches.size() > 30) {
            LocalDate threshold = LocalDate.now().minusDays(30);
            dailySearches.entrySet().removeIf(e -> e.getKey().isBefore(threshold));
        }
    }

    /**
     * 获取热门搜索词
     *
     * @param limit 最大返回数
     * @return 热门搜索词列表（按搜索次数降序）
     */
    public List<HotKeyword> getHotKeywords(int limit) {
        return hotKeywords.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()))
                .limit(limit)
                .map(e -> new HotKeyword(e.getKey(), e.getValue().get()))
                .collect(Collectors.toList());
    }

    /**
     * 获取零结果关键词
     *
     * @param limit 最大返回数
     * @return 零结果关键词列表
     */
    public List<HotKeyword> getZeroResultKeywords(int limit) {
        return zeroResultKeywords.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()))
                .limit(limit)
                .map(e -> new HotKeyword(e.getKey(), e.getValue().get()))
                .collect(Collectors.toList());
    }

    /**
     * 获取每日搜索量统计
     *
     * @param days 最近天数
     * @return 日期 → 搜索量
     */
    public Map<LocalDate, Long> getDailySearches(int days) {
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

    /**
     * 获取搜索统计摘要
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
                zeroResultKeywords.size()
        );
    }

    // P1-1: evict entries with lowest counts to prevent unbounded growth
    private void evictLowCountEntries(Map<String, AtomicLong> map) {
        int target = MAX_HOT_KEYWORDS / 2;
        map.entrySet().stream()
                .sorted((a, b) -> Long.compare(a.getValue().get(), b.getValue().get()))
                .limit(map.size() - target)
                .forEach(e -> map.remove(e.getKey()));
    }

    /**
     * 清空统计数据
     */
    public void clear() {
        hotKeywords.clear();
        zeroResultKeywords.clear();
        dailySearches.clear();
    }

    /**
     * 热门搜索词
     */
    public record HotKeyword(String keyword, long count) {
    }

    /**
     * 搜索分析摘要
     */
    public record SearchAnalyticsSummary(
            long totalSearches,
            long zeroResultSearches,
            double zeroResultRate,
            int uniqueKeywords,
            int zeroResultKeywords
    ) {
    }
}
