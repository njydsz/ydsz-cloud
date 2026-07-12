package com.njydsz.pmis.common.feign.exception;

import lombok.Getter;

/**
 * Feign 调用通用异常。
 *
 * <p>当 Feign 客户端调用失败且不属于特定异常类型时抛出此异常。
 * 用于封装 401、403、429、500、503 等 HTTP 错误状态。
 *
 * <p>支持的 HTTP 状态码：
 * <ul>
 *   <li>{@code 401 Unauthorized} - 未授权或 Token 过期</li>
 *   <li>{@code 403 Forbidden} - 权限不足，禁止访问</li>
 *   <li>{@code 429 Too Many Requests} - 请求过于频繁，触发限流</li>
 *   <li>{@code 500 Internal Server Error} - 下游服务内部错误</li>
 *   <li>{@code 503 Service Unavailable} - 下游服务不可用</li>
 * </ul>
 *
 * <p>错误消息示例：
 * <pre>
 * Feign 调用失败, method: UserService#getUser, request: GET http://user-service/api/user,
 * status: 500, reason: Internal Server Error, body: {"code":"100500","msg":"系统内部错误"}
 * </pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
@Getter
public class OpenFeignException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private static final String DEFAULT_ERROR_CODE = "ERROR";

    /**
     * 错误码。
     */
    private final String code;

    /**
     * 附加数据。
     */
    private final transient Object data;

    /**
     * 创建 Feign 调用异常。
     *
     * @param cause 原因异常
     */
    public OpenFeignException(Throwable cause) {
        super(cause);
        this.code = DEFAULT_ERROR_CODE;
        this.data = null;
    }

    /**
     * 创建 Feign 调用异常。
     *
     * @param message 异常消息
     */
    public OpenFeignException(String message) {
        super(message);
        this.code = DEFAULT_ERROR_CODE;
        this.data = null;
    }

    /**
     * 创建 Feign 调用异常。
     *
     * @param code    错误码
     * @param message 异常消息
     */
    public OpenFeignException(String code, String message) {
        super(message);
        this.code = code;
        this.data = null;
    }

    /**
     * 创建 Feign 调用异常。
     *
     * @param code  错误码
     * @param cause 原因异常
     */
    public OpenFeignException(String code, Throwable cause) {
        super(cause);
        this.code = code;
        this.data = null;
    }

    /**
     * 创建 Feign 调用异常。
     *
     * @param code    错误码
     * @param message 异常消息
     * @param cause   原因异常
     */
    public OpenFeignException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.data = null;
    }

    /**
     * 创建 Feign 调用异常。
     *
     * @param code                错误码
     * @param message             异常消息
     * @param cause               原因异常
     * @param enableSuppression   是否启用异常抑制
     * @param writableStackTrace  是否写入栈跟踪
     */
    public OpenFeignException(String code, String message, Throwable cause,
                              boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
        this.code = code;
        this.data = null;
    }
}
