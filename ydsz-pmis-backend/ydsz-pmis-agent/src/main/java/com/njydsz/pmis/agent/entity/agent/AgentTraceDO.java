package com.njydsz.pmis.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * Agent 全链路 Tracing 实体（P2-3 落地）。
 *
 * <p>对应 {@code pmis_agent_trace} 表，记录每个 Agent 执行的关键节点 span。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-3)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_agent_trace")
public class AgentTraceDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 链路 ID（与 AgentContext.traceId / Brave traceId 对齐） */
    private String traceId;

    /** 本 span ID（雪花算法字符串） */
    private String spanId;

    /** 父 span ID（AGENT_START 为根，parent=null） */
    private String parentSpanId;

    /** Agent 类型 */
    private String agentType;

    /** 业务类型 */
    private String bizType;

    /** 业务 ID */
    private String bizId;

    /** 业务引用 */
    private String bizRef;

    /** Span 名称：AGENT_START/STEP_START/LLM_THOUGHT/LLM_ACTION/TOOL_OBSERVATION/FINAL_ANSWER/STEP_END/AGENT_END/AGENT_ERROR */
    private String spanName;

    /** ReAct 步骤序号（1-based；非 ReAct 节点为 0） */
    private Integer stepIndex;

    /** Span 状态：SUCCESS / FAILED */
    private String status;

    /** 输入数据 JSON */
    private String inputData;

    /** 输出数据 JSON */
    private String outputData;

    /** 错误信息（status=FAILED 时填） */
    private String errorMsg;

    /** 本 span 耗时（毫秒） */
    private Long costMs;

    /** 第三方大模型 provider trace ID */
    private String providerTraceId;

    /** 租户 ID */
    private String tenantId;
}
