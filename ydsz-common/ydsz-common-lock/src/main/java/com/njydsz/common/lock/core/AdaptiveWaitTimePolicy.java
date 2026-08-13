package com.njydsz.common.lock.core;

import java.util.concurrent.ConcurrentHashMap;


/**
 * 自适应等待时间策略（默认实现）
 *
 * <p>基于锁键历史统计数据动态调整等待时间。核心逻辑：
 * <ul>
 *   <li>超时率 &gt; 60%：降低等待时间至请求值的 50%（快速失败）</li>
 *   <li>超时率 &gt; 30%：降低等待时间至请求值的 75%</li>
 *   <li>超时率 &lt; 10% 且平均等待耗时短：维持请求值不变</li>
 *   <li>无统计数据时：直接返回请求值</li>
 * </ul>
 *
 * <p>策略参数（可通过 Builder 调整）：
 * <ul>
 *   <li>{@code highTimeoutRateThreshold} - 高超时率阈值（默认 0.6）</li>
 *   <li>{@code mediumTimeoutRateThreshold} - 中timeout率阈值（默认 0.3）</li>
 *   <li>{@code lowTimeoutRateThreshold} - 低超时率阈值（默认 0.1）</li>
 *   <li>{@code reduceFactorOnHighTimeout} - 高超时率时的缩减因子（默认 0.5）</li>
 *   <li>{@code reduceFactorOnMediumTimeout} - 中timeout率时的缩减因子（默认 0.75）</li>
 *   <li>{@code statsWindowCapacity} - 统计窗口容量（默认 100 个锁键）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class AdaptiveWaitTimePolicy implements LockWaitTimePolicy {

    /** 高超时率阈值，超过该值触发强降等待 */
    private static final double DEFAULT_HIGH_TIMEOUT_RATE_THRESHOLD = 0.6;

    /** 中timeout率阈值，超过该值触发弱降等待 */
    private static final double DEFAULT_MEDIUM_TIMEOUT_RATE_THRESHOLD = 0.3;

    /** 低超时率阈值，低于该值视为低竞争 */
    private static final double DEFAULT_LOW_TIMEOUT_RATE_THRESHOLD = 0.1;

    /** 高超时率时的等待时间缩减因子 */
    private static final double DEFAULT_REDUCE_FACTOR_HIGH = 0.5;

    /** 中timeout率时的等待时间缩减因子 */
    private static final double DEFAULT_REDUCE_FACTOR_MEDIUM = 0.75;

    /** 锁键统计缓存默认容量 */
    private static final int DEFAULT_STATS_CAPACITY = 100;

    /** 最小等待时间降级因子（避免降至过低） */
    private static final double MIN_REDUCE_FACTOR = 0.1;

    private final double highTimeoutRateThreshold;
    private final double mediumTimeoutRateThreshold;
    private final double lowTimeoutRateThreshold;
    private final double reduceFactorOnHighTimeout;
    private final double reduceFactorOnMediumTimeout;
    private final int statsWindowCapacity;

    /** 锁键 -> 统计数据 */
    private final ConcurrentHashMap<String, LockWaitStats> statsMap;

    /**
     * 使用默认参数构造 AdaptiveWaitTimePolicy
     */
    public AdaptiveWaitTimePolicy() {
        this.highTimeoutRateThreshold = DEFAULT_HIGH_TIMEOUT_RATE_THRESHOLD;
        this.mediumTimeoutRateThreshold = DEFAULT_MEDIUM_TIMEOUT_RATE_THRESHOLD;
        this.lowTimeoutRateThreshold = DEFAULT_LOW_TIMEOUT_RATE_THRESHOLD;
        this.reduceFactorOnHighTimeout = DEFAULT_REDUCE_FACTOR_HIGH;
        this.reduceFactorOnMediumTimeout = DEFAULT_REDUCE_FACTOR_MEDIUM;
        this.statsWindowCapacity = DEFAULT_STATS_CAPACITY;
        this.statsMap = new ConcurrentHashMap<>(statsWindowCapacity);
    }

    /**
     * 使用自定义参数构造 AdaptiveWaitTimePolicy
     *
     * @param highTimeoutRateThreshold    高超时率阈值
     * @param mediumTimeoutRateThreshold  中timeout率阈值
     * @param lowTimeoutRateThreshold     低超时率阈值
     * @param reduceFactorOnHighTimeout   高超时率时的缩减因子
     * @param reduceFactorOnMediumTimeout 中timeout率时的缩减因子
     * @param statsWindowCapacity         统计窗口容量
     */
    public AdaptiveWaitTimePolicy(double highTimeoutRateThreshold,
                                   double mediumTimeoutRateThreshold,
                                   double lowTimeoutRateThreshold,
                                   double reduceFactorOnHighTimeout,
                                   double reduceFactorOnMediumTimeout,
                                   int statsWindowCapacity) {
        this.highTimeoutRateThreshold = highTimeoutRateThreshold;
        this.mediumTimeoutRateThreshold = mediumTimeoutRateThreshold;
        this.lowTimeoutRateThreshold = lowTimeoutRateThreshold;
        this.reduceFactorOnHighTimeout = reduceFactorOnHighTimeout;
        this.reduceFactorOnMediumTimeout = reduceFactorOnMediumTimeout;
        this.statsWindowCapacity = statsWindowCapacity;
        this.statsMap = new ConcurrentHashMap<>(statsWindowCapacity);
    }

    @Override
    public long calculateWaitTime(String lockKey, long requestedWaitTime, LockWaitStats stats) {
        // 无统计数据或首次锁定时，直接返回请求值
        if (stats == null || stats.getTotalWaitCount() == 0) {
            return requestedWaitTime;
        }

        double timeoutRate = stats.getTimeoutRate();
        double factor;

        if (timeoutRate >= highTimeoutRateThreshold) {
            // 高超时率 - 强降等待时间
            factor = reduceFactorOnHighTimeout;
        } else if (timeoutRate >= mediumTimeoutRateThreshold) {
            // 中timeout率 - 适度降低等待时间
            factor = reduceFactorOnMediumTimeout;
        } else {
            // 低超时率 - 维持原等待时间
            factor = 1.0;
        }

        double adjusted = requestedWaitTime * factor;
        // 保证不会低于最小因子（避免降至过低）
        double floorValue = requestedWaitTime * MIN_REDUCE_FACTOR;
        double result = Math.max(adjusted, floorValue);

        return Math.round(result);
    }

    /**
     * 获取或创建锁键对应的统计数据（用于外部记录）
     *
     * <p>当容量达到上限时，不再创建新条目（避免无界增长），返回 null。
     *
     * @param lockKey 锁键
     * @return 统计数据实例，容量满时返回 null
     */
    public LockWaitStats getOrCreateStats(String lockKey) {
        LockWaitStats existing = statsMap.get(lockKey);
        if (existing != null) {
            return existing;
        }
        // 容量保护：避免无界增长
        if (statsMap.size() >= statsWindowCapacity) {
            return null;
        }
        LockWaitStats newStats = new LockWaitStats();
        LockWaitStats prev = statsMap.putIfAbsent(lockKey, newStats);
        return prev != null ? prev : newStats;
    }

    /**
     * 主动移除某锁键的统计数据（供锁键不再使用时显式清理）
     *
     * @param lockKey 锁键
     */
    public void removeStats(String lockKey) {
        statsMap.remove(lockKey);
    }

    /**
     * 清空所有统计数据
     */
    public void clearAllStats() {
        statsMap.clear();
    }
}
