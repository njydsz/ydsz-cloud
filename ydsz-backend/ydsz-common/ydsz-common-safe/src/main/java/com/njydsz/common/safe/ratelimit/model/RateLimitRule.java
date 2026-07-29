package com.njydsz.common.safe.ratelimit.model;

import java.io.Serializable;
import java.time.Duration;

import com.njydsz.common.safe.ratelimit.enums.RateLimitAlgorithm;
import com.njydsz.common.safe.ratelimit.enums.RateLimitDimension;
import com.njydsz.common.safe.ratelimit.enums.RateLimitMode;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 限流规则
 *
 * <p>定义一条完整的限流规则，是限流决策的核心配置。
 * 支持 Sentinel 风格的多种限流模式（QPS、并发数、热点参数、集群限流等）。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * RateLimitRule rule = RateLimitRule.builder()
 *     .resource("user.login")
 *     .dimension(RateLimitDimension.API)
 *     .algorithm(RateLimitAlgorithm.TOKEN_BUCKET)
 *     .mode(RateLimitMode.LOCAL)
 *     .threshold(100)        // 每秒 100 个
 *     .window(Duration.ofSeconds(1))
 *     .burstCapacity(150)    // 桶容量 150
 *     .build();
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitRule implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 资源名称（限流 key 的基础） */
    private String resource;

    /** 限流维度 */
    @Builder.Default
    private RateLimitDimension dimension = RateLimitDimension.API;

    /** 限流算法 */
    @Builder.Default
    private RateLimitAlgorithm algorithm = RateLimitAlgorithm.TOKEN_BUCKET;

    /** 限流模式（本地 / 集群 / 自适应） */
    @Builder.Default
    private RateLimitMode mode = RateLimitMode.LOCAL;

    /** 阈值（每秒请求数 / 并发数 / 令牌数） */
    @Builder.Default
    private double threshold = 100.0;

    /** 限流统计窗口（默认 1 秒） */
    @Builder.Default
    private Duration window = Duration.ofSeconds(1);

    /** 令牌桶容量（允许的最大突发流量） */
    @Builder.Default
    private long burstCapacity = 200;

    /** 排队等待超时时间（仅 QUEUEING 模式） */
    @Builder.Default
    private Duration queueTimeout = Duration.ZERO;

    /** 预热期（冷启动预热，单位秒） */
    @Builder.Default
    private Duration warmupPeriod = Duration.ZERO;

    /** 是否启用 */
    @Builder.Default
    private boolean enabled = true;

    /** 优先级（数值越小优先级越高，1-100） */
    @Builder.Default
    private int priority = 50;

    /** 关联的限流错误码（默认 UnifiedExceptionCode.RATE_LIMIT） */
    private String errorCode;

    /** 降级方法（fallback bean name） */
    private String fallback;

    /** 备注 */
    private String remark;

    /**
     * 校验规则合法性
     */
    public void validate() {
        if (resource == null || resource.trim().isEmpty()) {
            throw new IllegalArgumentException("resource cannot be null or empty");
        }
        if (threshold <= 0) {
            throw new IllegalArgumentException("threshold must be positive");
        }
        if (window == null || window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("window must be positive");
        }
    }
}
