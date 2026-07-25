package com.njydsz.common.ratelimit.model;

import java.io.Serializable;
import java.time.Instant;

import com.njydsz.common.ratelimit.enums.RateLimitResult;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 限流决策结果
 *
 * <p>封装单次限流决策的完整结果，包括是否通过、剩余配额、等待时间等。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitDecision implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 资源名称 */
    private String resource;

    /** 限流结果 */
    private RateLimitResult result;

    /** 限流 key */
    private String key;

    /** 限流规则 */
    private RateLimitRule rule;

    /** 剩余配额 */
    private double remaining;

    /** 限流阈值 */
    private double threshold;

    /** 等待时间（毫秒），0 表示无等待 */
    private long waitTimeMillis;

    /** 决策时间 */
    private Instant timestamp;

    /** 决策原因（用于日志/排查） */
    private String reason;

    /**
     * 是否通过限流
     */
    public boolean isPass() {
        return result == RateLimitResult.PASS;
    }

    /**
     * 是否被限流
     */
    public boolean isBlocked() {
        return result == RateLimitResult.BLOCKED;
    }
}
