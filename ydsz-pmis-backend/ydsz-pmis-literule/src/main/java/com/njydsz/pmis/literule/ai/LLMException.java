package com.njydsz.pmis.literule.ai;

/**
 * LLM 调用异常（P2-15 AI 增强）
 *
 * <p>当 LLM 提供方调用失败（网络/超时/鉴权/限流/响应格式异常）时抛出。
 * 业务层应捕获此异常后降级到规则模板或返回空推荐，
 * 避免 LLM 不可用时整个规则引擎流程崩溃。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
public class LLMException extends RuntimeException {

    private static final String serialVersionUID = "1";

    /** 提供方 */
    private final String provider;

    /** HTTP 状态码（0 表示非 HTTP 错误） */
    private final int statusCode;

    public LLMException(String provider, String message) {
        super(message);
        this.provider = provider;
        this.statusCode = 0;
    }

    public LLMException(String provider, int statusCode, String message) {
        super(message);
        this.provider = provider;
        this.statusCode = statusCode;
    }

    public LLMException(String provider, String message, Throwable cause) {
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
