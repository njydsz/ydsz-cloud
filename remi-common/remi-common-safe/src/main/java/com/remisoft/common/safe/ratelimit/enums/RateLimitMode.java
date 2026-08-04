package com.remisoft.common.safe.ratelimit.enums;

/**
 * 限流模式枚举
 *
 * <p>区分本地限流、集群限流与自适应限流，对应不同实现策略。
 *
 * @author remi-team
 * @since 1.0.0
 */
public enum RateLimitMode {

    /** 本地限流（单实例内精确，单节点隔离） */
    LOCAL("local", "本地限流"),

    /** 集群限流（基于 Redis 令牌桶，全局精确） */
    CLUSTER("cluster", "集群限流"),

    /** 自适应限流（基于系统负载动态调整） */
    ADAPTIVE("adaptive", "自适应限流"),

    /** 混合模式（先本地后集群） */
    HYBRID("hybrid", "混合模式");

    private final String code;
    private final String description;

    RateLimitMode(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
