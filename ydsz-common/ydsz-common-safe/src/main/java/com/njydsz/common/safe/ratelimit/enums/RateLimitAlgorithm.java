package com.njydsz.common.safe.ratelimit.enums;

/**
 * 限流算法枚举
 *
 * <p>YDSZ 限流模块支持的算法类型：
 * <ul>
 *   <li>{@link #TOKEN_BUCKET} - 令牌桶（支持突发流量，适合 API 限流）— <b>推荐</b></li>
 * </ul>
 *
 * <p>已废弃算法（保留实现以兼容现有配置，新业务请使用 {@link #TOKEN_BUCKET}）：
 * <ul>
 *   <li>{@link #COUNTER} - 固定窗口计数器（有窗口边界突刺问题）</li>
 *   <li>{@link #SLIDING_WINDOW} - 滑动窗口（令牌桶可完全覆盖其场景）</li>
 *   <li>{@link #LEAKY_BUCKET} - 漏桶（流量整形场景极少）</li>
 *   <li>{@link #CONCURRENCY} - 并发数限流（信号量，建议用线程池隔离替代）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum RateLimitAlgorithm {

    /**
     * 固定窗口计数器
     *
     * <p>在固定时间窗口内计数，超过阈值则拒绝。实现简单，但有窗口边界突刺问题。
     *
     * @deprecated 使用 {@link #TOKEN_BUCKET} 替代，令牌桶在相同场景下性能更优且无边界突刺
     */
    @Deprecated
    COUNTER("counter", "固定窗口计数器"),

    /**
     * 滑动窗口
     *
     * <p>基于时间轮或环形数组实现，精度高，无窗口边界问题。
     *
     * @deprecated 使用 {@link #TOKEN_BUCKET} 替代，令牌桶可覆盖其精准 QPS 限流场景
     */
    @Deprecated
    SLIDING_WINDOW("sliding-window", "滑动窗口"),

    /**
     * 令牌桶（推荐）
     *
     * <p>以恒定速率往桶里放令牌，请求需取令牌。允许突发流量（桶满时）。
     * 本地限流首选算法，兼顾突发流量支持和实现简洁性。
     */
    TOKEN_BUCKET("token-bucket", "令牌桶"),

    /**
     * 漏桶
     *
     * <p>请求进入桶中，以恒定速率流出，平滑流量。
     *
     * @deprecated 流量整形场景极少，使用 {@link #TOKEN_BUCKET} + 限流阈值可满足绝大多数需求
     */
    @Deprecated
    LEAKY_BUCKET("leaky-bucket", "漏桶"),

    /**
     * 并发数限流（信号量）
     *
     * <p>基于 Semaphore，限制同时处理的请求数，适合资源隔离。
     *
     * @deprecated 建议使用线程池 + 有界队列做资源隔离，而非在限流层实现
     */
    @Deprecated
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

    /**
     * 根据编码解析对应的限流算法。
     *
     * <p>编码匹配不区分大小写；编码为 {@code null} 或无法匹配时返回 {@link #TOKEN_BUCKET}
     * 作为默认值，不抛出异常，保证非法配置下限流仍可用。
     *
     * @param code 算法编码（如 {@code "token-bucket"}），允许为 {@code null}
     * @return 匹配到的限流算法；无法匹配时返回 {@link #TOKEN_BUCKET}
     */
    public static RateLimitAlgorithm fromCode(String code) {
        if (code == null) {
            return TOKEN_BUCKET;
        }
        for (RateLimitAlgorithm alg : values()) {
            if (alg.code.equalsIgnoreCase(code)) {
                return alg;
            }
        }
        return TOKEN_BUCKET;
    }
}
