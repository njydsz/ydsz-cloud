package com.njydsz.pmis.agent.dto.tool;

import lombok.Builder;
import lombok.Data;

/**
 * 单次 LLM 调用的 token 使用量（P2-4 落地）。
 *
 * <p>由 {@code TokenQuotaAspect} 在 LlmProvider.chat 调用后构造，
 * 通过 {@code TokenCounter.estimate} 估算 prompt 和 completion 的 token 数。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-4)
 */
@Data
@Builder
public class TokenUsage {

    /** 租户 ID */
    private String tenantId;

    /** 链路 ID */
    private String traceId;

    /** Agent 类型 */
    private String agentType;

    /** LLM Provider 名称 */
    private String provider;

    /** 模型名称 */
    private String model;

    /** 业务引用 */
    private String bizRef;

    /** 输入 token 数（估算） */
    private int promptTokens;

    /** 输出 token 数（估算） */
    private int completionTokens;

    /** 总 token 数 = prompt + completion */
    private int totalTokens;

    /** 调用耗时（毫秒） */
    private long costMs;

    /** 调用人 ID */
    private String callerId;

    /** 调用人姓名 */
    private String callerName;

    /**
     * 计算总 token 数。
     *
     * @return prompt + completion
     */
    public int computeTotal() {
        return promptTokens + completionTokens;
    }
}
