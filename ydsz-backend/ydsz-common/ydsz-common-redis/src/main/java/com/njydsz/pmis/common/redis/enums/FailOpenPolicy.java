package com.njydsz.common.redis.enums;

/**
 * 限流器故障处理策略枚举
 *
 * <p>定义当限流器（如Redis）发生故障时的处理策略
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum FailOpenPolicy {

    /**
     * 故障时放行（Fail Open）
     *
     * <p>当限流器不可用时，允许所有请求通过，保证服务可用性
     * 适用于对限流要求不严格的场景
     */
    FAIL_OPEN,

    /**
     * 故障时拒绝（Fail Closed）
     *
     * <p>当限流器不可用时，拒绝所有请求，保证系统安全
     * 适用于安全敏感场景，如支付、登录等
     */
    FAIL_CLOSED,

    /**
     * 故障时抛出异常（Fail Throw）
     *
     * <p>当限流器不可用时，抛出异常让调用方处理
     * 适用于需要明确知道限流器故障的场景
     */
    FAIL_THROW
}
