package com.njydsz.pmis.agent.domain.gateway;

import com.njydsz.pmis.common.exception.custom.InfrastructureException;

/**
 * LLM 调用异常
 *
 * <p>LLM API 调用失败时抛出，包括网络超时、认证失败、模型不可用、响应解析错误等。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class LlmException extends InfrastructureException {

    private static final long serialVersionUID = 1L;

    /** 错误类型 */
    public enum ErrorType {
        NETWORK_TIMEOUT,
        AUTH_FAILED,
        MODEL_NOT_FOUND,
        RATE_LIMITED,
        INVALID_RESPONSE,
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
