package com.njydsz.pmis.literule.ai;

import com.njydsz.pmis.common.ai.LlmException;

/**
 * LLM 调用异常（P2-15 AI 增强）
 *
 * <p>当 LLM 提供方调用失败（网络/超时/鉴权/限流/响应格式异常）时抛出。
 * 业务层应捕获此异常后降级到规则模板或返回空推荐，
 * 避免 LLM 不可用时整个规则引擎流程崩溃。
 *
 * <p><b>P0-2 架构优化</b>：继承 {@link LlmException}（common 模块统一异常），
 * 保持向后兼容（literule 内部代码仍可 catch LLMException）。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
public class LLMException extends LlmException {

    private static final long serialVersionUID = 1L;

    public LLMException(String provider, String message) {
        super(provider, message);
    }

    public LLMException(String provider, int statusCode, String message) {
        super(provider, statusCode, message);
    }

    public LLMException(String provider, String message, Throwable cause) {
        super(provider, message, cause);
    }
}
