package com.njydsz.pmis.agent.engine.trace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * Trace 事件（P2-3 落地）。
 *
 * <p>表示 Agent 执行链路中的一个事件节点，用于 Trace 可视化和回放。
 * 对标 LangSmith Trace Event / Langfuse Step。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0 (P2-3)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TraceEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 事件类型（STEP_START / THOUGHT / ACTION / OBSERVATION / SUCCESS / FAILED / STEP_END 等） */
    private String type;

    /** 节点名称（如 "step-1"、"bpmn_validate"） */
    private String nodeName;

    /** 事件消息描述 */
    private String message;

    /** 事件时间戳（毫秒） */
    private long timestamp;

    /** 事件附加数据 */
    private Map<String, Object> data;
}
