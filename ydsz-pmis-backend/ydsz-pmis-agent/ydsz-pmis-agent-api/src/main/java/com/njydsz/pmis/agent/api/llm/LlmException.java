package com.njydsz.pmis.agent.api.llm;

/**
 * LLM 调用异常（P0-2 架构优化，2026-07-12 迁移到 agent 模块）。
 *
 * <p>当 LLM 提供方调用失败（网络/超时/鉴权/限流/响应格式异常）时抛出。
 * 业务层应捕获此异常后降级到规则模板或返回空推荐，
 * 避免 LLM 不可用时整个业务流程崩溃。
 *
 * <p>合并自 literule 模块的 {@code LLMException}，统一异常体系。
 *
 * @author ydsz-pmis-team
 * @since 1.6.0 (P0-2)
 */
public class LlmException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 提供方 */
    private final String provider;

    /** HTTP 状态码（0 表示非 HTTP 错误） */
    private final int statusCode;

    public LlmException(String provider, String message) {
        super(message);
        this.provider = provider;
        this.statusCode = 0;
    }

    public LlmException(String provider, int statusCode, String message) {
        super(message);
        this.provider = provider;
        this.statusCode = statusCode;
    }

    public LlmException(String provider, String message, Throwable cause) {
        super(message, cause);
        this.provider = provider;
        this.statusCode = 0;
    }

    public String getProvider() {
        return provider;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
