package com.njydsz.common.safe.ratelimit.enums;

/**
 * 限流算法枚举
 *
 * <p>YDSZ 限流模块支持的算法类型，覆盖业界主流场景：
 * <ul>
 *   <li>{@link #COUNTER} - 固定窗口计数器（最简单，适合粗粒度 QPS 限流）</li>
 *   <li>{@link #SLIDING_WINDOW} - 滑动窗口（精度高，适合精准 QPS 限流）</li>
 *   <li>{@link #TOKEN_BUCKET} - 令牌桶（支持突发流量，适合 API 限流）</li>
 *   <li>{@link #LEAKY_BUCKET} - 漏桶（流量整形，适合流量平滑）</li>
 *   <li>{@link #CONCURRENCY} - 并发数限流（信号量，适合线程/资源隔离）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum RateLimitAlgorithm {

    /**
     * 固定窗口计数器
     * <p>在固定时间窗口内计数，超过阈值则拒绝。实现简单，但有窗口边界突刺问题。
     */
    COUNTER("counter", "固定窗口计数器"),

    /**
     * 滑动窗口
     * <p>基于时间轮或环形数组实现，精度高，无窗口边界问题。
     */
    SLIDING_WINDOW("sliding-window", "滑动窗口"),

    /**
     * 令牌桶
     * <p>以恒定速率往桶里放令牌，请求需取令牌。允许突发流量（桶满时）。
     */
    TOKEN_BUCKET("token-bucket", "令牌桶"),

    /**
     * 漏桶
     * <p>请求进入桶中，以恒定速率流出，平滑流量。
     */
    LEAKY_BUCKET("leaky-bucket", "漏桶"),

    /**
     * 并发数限流（信号量）
     * <p>基于 Semaphore，限制同时处理的请求数，适合资源隔离。
     */
    CONCURRENCY("concurrency", "并发数限流");

    private final String code;
    private final String description;

    RateLimitAlgorithm(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static RateLimitAlgorithm fromCode(String code) {
        if (code == null) {
            return COUNTER;
        }
        for (RateLimitAlgorithm alg : values()) {
            if (alg.code.equalsIgnoreCase(code)) {
                return alg;
            }
        }
        return COUNTER;
    }
}
