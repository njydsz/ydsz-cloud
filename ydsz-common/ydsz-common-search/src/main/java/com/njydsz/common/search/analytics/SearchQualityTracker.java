package com.njydsz.common.search.analytics;

import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 搜索质量评估框架
 * <p>
 * 收集搜索质量指标：
 * <ul>
 *   <li>MRR（Mean Reciprocal Rank）— 用户点击结果的倒数排名的平均值</li>
 *   <li>CTR（Click-Through Rate）— 点击率 = 点击次数 / 搜索次数</li>
 *   <li>Zero Result Rate — 零结果率</li>
 *   <li>Avg Result Count — 平均结果数</li>
 *   <li>Search Latency — 搜索延迟分布</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class SearchQualityTracker {

    private static final String KEY_MRR_SUM = "search:quality:mrr:sum";
    private static final String KEY_MRR_COUNT = "search:quality:mrr:count";
    private static final String KEY_CTR_CLICKS = "search:quality:ctr:clicks";
    private static final String KEY_CTR_SEARCHES = "search:quality:ctr:searches";
    private static final String KEY_ZERO_RESULT = "search:quality:zero:count";
    private static final String KEY_TOTAL_SEARCH = "search:quality:total:count";
    private static final String KEY_LATENCY_SUM = "search:quality:latency:sum";
    private static final String KEY_LATENCY_COUNT = "search:quality:latency:count";

    private final ObjectProvider<StringRedisTemplate> redisProvider;

    private final AtomicLong localMrrSum = new AtomicLong(0);
    private final AtomicLong localMrrCount = new AtomicLong(0);
    private final AtomicLong localClicks = new AtomicLong(0);
    private final AtomicLong localSearches = new AtomicLong(0);
    private final AtomicLong localZeroResult = new AtomicLong(0);
    private final AtomicLong localTotalSearch = new AtomicLong(0);
    private final AtomicLong localLatencySum = new AtomicLong(0);
    private final AtomicLong localLatencyCount = new AtomicLong(0);

    public SearchQualityTracker(ObjectProvider<StringRedisTemplate> redisProvider) {
        this.redisProvider = redisProvider;
    }

    /**
     * 记录一次搜索事件
     *
     * @param resultCount 结果数
     * @param tookMs      耗时（毫秒）
     */
    public void recordSearchEvent(long resultCount, long tookMs) {
        localTotalSearch.incrementAndGet();
        localLatencySum.addAndGet(tookMs);
        localLatencyCount.incrementAndGet();
        if (resultCount == 0) {
            localZeroResult.incrementAndGet();
        }

        StringRedisTemplate redis = getRedis();
        if (redis != null) {
            try {
                redis.opsForValue().increment(KEY_TOTAL_SEARCH, 1);
                redis.opsForValue().increment(KEY_LATENCY_SUM, tookMs);
                redis.opsForValue().increment(KEY_LATENCY_COUNT, 1);
                if (resultCount == 0) {
                    redis.opsForValue().increment(KEY_ZERO_RESULT, 1);
                }
            } catch (Exception e) {
                log.debug("[SearchQuality] Redis 写入失败", e);
            }
        }
    }

    /**
     * 记录用户点击搜索结果
     *
     * @param position 点击结果的排名（从 1 开始）
     */
    public void recordClick(int position) {
        double rr = position > 0 ? 1.0 / position : 0.0;
        long mrrScaled = (long) (rr * 10000);
        localMrrSum.addAndGet(mrrScaled);
        localMrrCount.incrementAndGet();
        localClicks.incrementAndGet();

        StringRedisTemplate redis = getRedis();
        if (redis != null) {
            try {
                redis.opsForValue().increment(KEY_MRR_SUM, mrrScaled);
                redis.opsForValue().increment(KEY_MRR_COUNT, 1);
                redis.opsForValue().increment(KEY_CTR_CLICKS, 1);
            } catch (Exception e) {
                log.debug("[SearchQuality] Redis 写入失败", e);
            }
        }
    }

    /**
     * 获取搜索质量报告
     *
     * @return 质量报告
     */
    public QualityReport getReport() {
        StringRedisTemplate redis = getRedis();
        long totalSearches = localTotalSearch.get();
        long zeroResults = localZeroResult.get();
        long clicks = localClicks.get();
        long mrrSum = localMrrSum.get();
        long mrrCount = localMrrCount.get();
        long latencySum = localLatencySum.get();
        long latencyCount = localLatencyCount.get();

        if (redis != null) {
            try {
                totalSearches = getLongValue(redis, KEY_TOTAL_SEARCH, totalSearches);
                zeroResults = getLongValue(redis, KEY_ZERO_RESULT, zeroResults);
                clicks = getLongValue(redis, KEY_CTR_CLICKS, clicks);
                mrrSum = getLongValue(redis, KEY_MRR_SUM, mrrSum);
                mrrCount = getLongValue(redis, KEY_MRR_COUNT, mrrCount);
                latencySum = getLongValue(redis, KEY_LATENCY_SUM, latencySum);
                latencyCount = getLongValue(redis, KEY_LATENCY_COUNT, latencyCount);
            } catch (Exception e) {
                log.debug("[SearchQuality] Redis 读取失败，降级到内存", e);
            }
        }

        double mrr = mrrCount > 0 ? (double) mrrSum / 10000.0 / mrrCount : 0.0;
        double ctr = totalSearches > 0 ? (double) clicks / totalSearches : 0.0;
        double zeroResultRate = totalSearches > 0 ? (double) zeroResults / totalSearches : 0.0;
        double avgLatency = latencyCount > 0 ? (double) latencySum / latencyCount : 0.0;

        return new QualityReport(mrr, ctr, zeroResultRate, avgLatency, totalSearches, clicks);
    }

    private long getLongValue(StringRedisTemplate redis, String key, long fallback) {
        try {
            String val = redis.opsForValue().get(key);
            return val != null ? Long.parseLong(val) : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private StringRedisTemplate getRedis() {
        return redisProvider.getIfAvailable();
    }

    /**
     * 搜索质量报告
     *
     * @param mrr            平均倒数排名（0~1，越高越好）
     * @param ctr            点击率（0~1，越高越好）
     * @param zeroResultRate 零结果率（0~1，越低越好）
     * @param avgLatencyMs   平均搜索延迟（毫秒）
     * @param totalSearches  总搜索次数
     * @param totalClicks    总点击次数
     */
    public record QualityReport(
            double mrr,
            double ctr,
            double zeroResultRate,
            double avgLatencyMs,
            long totalSearches,
            long totalClicks
    ) {
    }
}
