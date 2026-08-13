package com.njydsz.common.exception.code;
import lombok.Getter;

import com.njydsz.common.exception.enums.ExceptionCode;
import com.njydsz.common.exception.registry.YdszExceptionCode;

/**
 * 限流模块异常码。
 *
 * <p>限流、熔断、降级、流控相关异常码（A04 系列中的频率控制 + D01xxx）。
 * 覆盖请求限流、操作频率限制、流量控制拒绝等场景。
 *
 * @author ydsz-team
 * @since 2.0.0
 * @see CoreExceptionCode
 * @see SecurityExceptionCode
 */
@Getter
@YdszExceptionCode(module = "ratelimit", description = "限流模块限流熔断降级异常码")
public enum RateLimitExceptionCode implements ExceptionCode {

    // ==================== A04xx 请求频率相关 ====================

    /**
     * 请求过于频繁（原 ResponseCode.RATE_LIMIT 100429）
     * @param "A04057" "A04057" 参数说明
     * @param "rate.limit" "rate.limit" 参数说明
     * @param REQUEST_TOO_FREQUENT("A04058" REQUEST_TOO_FREQUENT("A04058" 参数说明
     * @param "request.too.frequent" "request.too.frequent" 参数说明
     * @param OPERATION_TOO_FREQUENT("A04059" OPERATION_TOO_FREQUENT("A04059" 参数说明
     * @param "operation.too.frequent" "operation.too.frequent" 参数说明
     * @param RATE_LIMIT_EXCEEDED("A04060" RATE_LIMIT_EXCEEDED("A04060" 参数说明
     * @param "rate.limit.exceeded" "rate.limit.exceeded" 参数说明
     * @param 429 429 参数说明
     * @return 处理结果
     */
    RATE_LIMIT("A04057", "rate.limit", 429),
    /**
     * 请求过于频繁（限流）
     * @param "A04058" "A04058" 参数说明
     * @param "request.too.frequent" "request.too.frequent" 参数说明
     * @param OPERATION_TOO_FREQUENT("A04059" OPERATION_TOO_FREQUENT("A04059" 参数说明
     * @param "operation.too.frequent" "operation.too.frequent" 参数说明
     * @param RATE_LIMIT_EXCEEDED("A04060" RATE_LIMIT_EXCEEDED("A04060" 参数说明
     * @param "rate.limit.exceeded" "rate.limit.exceeded" 参数说明
     * @param 429 429 参数说明
     * @return 处理结果
     */
    REQUEST_TOO_FREQUENT("A04058", "request.too.frequent", 429),
    /**
     * 操作过于频繁
     * @param "A04059" "A04059" 参数说明
     * @param "operation.too.frequent" "operation.too.frequent" 参数说明
     * @param RATE_LIMIT_EXCEEDED("A04060" RATE_LIMIT_EXCEEDED("A04060" 参数说明
     * @param "rate.limit.exceeded" "rate.limit.exceeded" 参数说明
     * @param 429 429 参数说明
     * @return 处理结果
     */
    OPERATION_TOO_FREQUENT("A04059", "operation.too.frequent", 429),
    /**
     * 限流异常
     * @param "A04060" "A04060" 参数说明
     * @param "rate.limit.exceeded" "rate.limit.exceeded" 参数说明
     * @param 429 429 参数说明
     * @return 处理结果
     */
    RATE_LIMIT_EXCEEDED("A04060", "rate.limit.exceeded", 429);

    // ==================== 字段定义 ====================

    /**
     * 异常错误码
     */
    private final String code;
    /**
     * 国际化消息键
     */
    private final String key;
    /**
     * HTTP 状态码
     */
    private final int httpStatus;

    RateLimitExceptionCode(String code, String key, int httpStatus) {
        this.code = code;
        this.key = key;
        this.httpStatus = httpStatus;
    }

    @Override
    public int getHttpStatus() {
        return httpStatus;
    }
}
