package com.njydsz.common.exception.code;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.njydsz.common.exception.enums.ExceptionCode;
import com.njydsz.common.exception.registry.YdszExceptionCode;

import lombok.Getter;

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

    /** 请求过于频繁（原 ResponseCode.RATE_LIMIT 100429） */
    RATE_LIMIT("A04057", "rate.limit", 429),
    /** 请求过于频繁（限流） */
    REQUEST_TOO_FREQUENT("A04058", "request.too.frequent", 429),
    /** 操作过于频繁 */
    OPERATION_TOO_FREQUENT("A04059", "operation.too.frequent", 429),
    /** 限流异常 */
    RATE_LIMIT_EXCEEDED("A04060", "rate.limit.exceeded", 429);

    // ==================== 字段定义 ====================

    /** 异常错误码 */
    private final String code;
    /** 国际化消息键 */
    private final String key;
    /** HTTP 状态码 */
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

    // ============================================================
    // 静态注册 & 便捷查找
    // ============================================================

    /**
     * 内部快速查找缓存。
     */
    private static final Map<String, RateLimitExceptionCode> LOOKUP_MAP;

    static {
        Map<String, RateLimitExceptionCode> map = new HashMap<>();
        for (RateLimitExceptionCode code : values()) {
            map.put(code.getCode(), code);
        }
        LOOKUP_MAP = Collections.unmodifiableMap(map);
    }

    /**
     * 便捷查找方法：按 code 字符串查找本模块的限流异常码枚举。
     *
     * @param code 异常码字符串
     * @return 对应的 RateLimitExceptionCode 枚举实例；未找到返回 null
     */
    public static RateLimitExceptionCode resolve(String code) {
        return code != null ? LOOKUP_MAP.get(code) : null;
    }
}
