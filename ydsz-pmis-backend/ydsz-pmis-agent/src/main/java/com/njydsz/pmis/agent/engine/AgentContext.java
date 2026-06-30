package com.njydsz.pmis.agent.engine;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Agent 输入上下文（统一模型）
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentContext {
    /** 业务类型 */
    private String bizType;
    /** 业务 ID */
    private Long bizId;
    /** 业务引用（编码/名称） */
    private String bizRef;
    /** 调用人 ID */
    private Long callerId;
    /** 调用人姓名 */
    private String callerName;
    /** 来源 */
    private String source;
    /** 业务自定义输入参数（按 Agent 自行解释） */
    private Map<String, Object> params;
}
