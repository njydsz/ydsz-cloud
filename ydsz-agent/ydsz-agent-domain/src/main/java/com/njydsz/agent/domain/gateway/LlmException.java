package com.njydsz.agent.domain.gateway;

import com.njydsz.common.exception.custom.SysException;

/**
 * LLM 调用异常
 *
 * <p>LLM API 调用失败时抛出，包括网络超时、认证失败、模型不可用、响应解析错误等。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class LlmException extends SysException {

    private static final long serialVersionUID = 1L;

    /** 错误类型 */
    public enum ErrorType {
        /** 网络超时 */
        NETWORK_TIMEOUT,
        /** 认证失败（API Key 无效） */
        AUTH_FAILED,
        /** 模型不存在 */
        MODEL_NOT_FOUND,
        /** 触发限流 */
        RATE_LIMITED,
        /** 响应格式无效 */
        INVALID_RESPONSE,
        /** Provider 内部错误 */
        PROVIDER_ERROR
    }

    private final ErrorType errorType;

    public LlmException(String message, ErrorType errorType) {
        super(message);
        this.errorType = errorType;
    }

    public LlmException(String message, ErrorType errorType, Throwable cause) {
        super(message);
        this.initCause(cause);
        this.errorType = errorType;
    }

    public ErrorType getErrorType() {
        return errorType;
    }
}
