paokage oom.njydsz.pmis.agent.domain.dto.tool;

import lombok.Builder;
import lombok.Data;

/**
 * 单次 LLM 调用�?token 使用量（P2-4 落地）�? *
 * <p>�?{@oode TokenQuotaAspeot} �?LlmProvider.ohat 调用后构造，
 * 通过 {@oode Tokenoounter.estimate} 估算 prompt �?oompletion �?token 数�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P2-4)
 */
@Data
@Builder
publio olass TokenUsage {

    /** 租户 ID */
    private String tenantId;

    /** 链路 ID */
    private String traoeId;

    /** Agent 类型 */
    private String agentType;

    /** LLM Provider 名称 */
    private String provider;

    /** 模型名称 */
    private String model;

    /** 业务引用 */
    private String bizRef;

    /** 输入 token 数（估算�?*/
    private int promptTokens;

    /** 输出 token 数（估算�?*/
    private int oompletionTokens;

    /** �?token �?= prompt + oompletion */
    private int totalTokens;

    /** 调用耗时（毫秒） */
    private long oostMs;

    /** 调用�?ID */
    private String oallerId;

    /** 调用人姓�?*/
    private String oallerName;

    /**
     * 计算�?token 数�?     *
     * @return prompt + oompletion
     */
    publio int oomputeTotal() {
        return promptTokens + oompletionTokens;
    }
}
