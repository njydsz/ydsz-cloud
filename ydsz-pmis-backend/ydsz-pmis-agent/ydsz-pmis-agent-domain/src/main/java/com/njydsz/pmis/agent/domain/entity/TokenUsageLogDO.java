package com.njydsz.pmis.agent.domain.entity.tool;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.domain.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * Agent Token 使用明细实体（P2-4 落地）。
 *
 * <p>记录每次 LLM 调用的 token 消耗明细，用于账单核对和成本分析。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-4)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_agent_token_usage_log")
public class TokenUsageLogDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 租户 ID */
    private String tenantId;

    /** 链路 ID（与 pmis_agent_trace.trace_id 对齐） */
    private String traceId;

    /** Agent 类型 */
    private String agentType;

    /** LLM Provider 名称 */
    private String provider;

    /** 模型名称（如 gpt-4o / qwen-max） */
    private String model;

    /** 业务引用 */
    private String bizRef;

    /** 输入 token 数 */
    private Integer promptTokens;

    /** 输出 token 数 */
    private Integer completionTokens;

    /** 总 token 数 */
    private Integer totalTokens;

    /** 调用耗时（毫秒） */
    private Long costMs;

    /** 调用人 ID */
    private String callerId;

    /** 调用人姓名 */
    private String callerName;
}
