package com.remisoft.common.safe.ratelimit.enums;

/**
 * 限流结果枚举
 *
 * @author remi-team
 * @since 1.0.0
 */
public enum RateLimitResult {

    /** 通过 */
    PASS("pass", "通过"),

    /** 限流拒绝 */
    BLOCKED("blocked", "限流拒绝"),

    /** 排队等待 */
    QUEUEING("queueing", "排队等待");

    private final String code;
    private final String description;

    RateLimitResult(String code, String description) {
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
