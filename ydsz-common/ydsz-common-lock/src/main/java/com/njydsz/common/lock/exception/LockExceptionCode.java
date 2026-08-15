package com.njydsz.common.lock.exception;

import com.njydsz.common.exception.enums.ExceptionCode;
import com.njydsz.common.exception.registry.YdszExceptionCode;

import lombok.Getter;

/**
 * 分布式锁模块异常码枚举。
 *
 * <p>实现 {@link ExceptionCode} 接口，自动注册到 {@link com.njydsz.common.exception.code.ErrorCodeTable}。
 * 细分错误码用于日志告警分类与根因分析：
 * <ul>
 *   <li>I01001 - 获取锁超时</li>
 *   <li>I01002 - 获取锁被中断</li>
 *   <li>I01003 - 释放锁失败</li>
 *   <li>I01004 - 锁续期失败</li>
 *   <li>I01005 - 超过最大重入深度</li>
 *   <li>I01006 - Redis 不可用</li>
 *   <li>I01007 - 未知错误</li>
 * </ul>
 *
 * <p><b>稳定性：</b>错误码是业务契约，修改/废弃必须保留向前兼容，
 * 避免错误码硬编码在客户端代码中后无法平滑升级。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Getter
@YdszExceptionCode(module = "lock", description = "分布式锁")
public enum LockExceptionCode implements ExceptionCode {

    /** 默认 / 未知错误 */
    LOCK_ERROR("I01000", "lock.error", 409),

    /** 获取锁超时 */
    ACQUIRE_TIMEOUT("I01001", "lock.acquire.timeout", 409),

    /** 获取锁被中断 */
    ACQUIRE_INTERRUPTED("I01002", "lock.acquire.interrupted", 409),

    /** 释放锁失败 */
    RELEASE_FAILED("I01003", "lock.release.failed", 409),

    /** 锁续期失败 */
    RENEW_FAILED("I01004", "lock.renew.failed", 409),

    /** 超过最大重入深度 */
    MAX_DEPTH_EXCEEDED("I01005", "lock.max.depth.exceeded", 409),

    /** Redis 不可用 */
    REDIS_UNAVAILABLE("I01006", "lock.redis.unavailable", 503),

    /** 未知错误 */
    UNKNOWN("I01007", "lock.unknown", 409);

    /** 错误码（业务契约，不应轻易变更） */
    private final String code;
    /** 国际化 key */
    private final String key;
    /** HTTP 状态码 */
    private final int httpStatus;

    LockExceptionCode(String code, String key, int httpStatus) {
        this.code = code;
        this.key = key;
        this.httpStatus = httpStatus;
    }

    @Override
    public int getHttpStatus() {
        return httpStatus;
    }
}
