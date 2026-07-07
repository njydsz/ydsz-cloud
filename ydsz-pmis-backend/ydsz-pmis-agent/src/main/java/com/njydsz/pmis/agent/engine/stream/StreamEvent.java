package com.njydsz.pmis.agent.engine.stream;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * ReAct 流式输出事件（P2-1 落地）
 *
 * <p>对应 ReAct 循环中各关键节点的回调事件，用于推送给 SSE 客户端或其他流式消费者。
 * 对标 Coze / Dify 的 Chat Stream Event，让前端能够实时展示「思考中 → 调用工具 → 观察 → 最终回答」全过程。
 *
 * <p>事件类型（{@link Type}）：
 * <ul>
 *   <li>{@link Type#STEP_START}     - 步骤开始（携带 stepIndex）</li>
 *   <li>{@link Type#THOUGHT}       - LLM 思考完成（携带 thought 文本）</li>
 *   <li>{@link Type#ACTION}         - LLM 决策动作（携带 action + parameters）</li>
 *   <li>{@link Type#TOOL_DELTA}    - 工具执行流式片段（可选，携带 chunk 文本）</li>
 *   <li>{@link Type#OBSERVATION}    - 工具执行结果（携带 observation 文本）</li>
 *   <li>{@link Type#FINAL_ANSWER}   - 最终答案（携带 finalAnswer 文本）</li>
 *   <li>{@link Type#LLM_DELTA}      - LLM 输出增量片段（携带 delta 文本，可选）</li>
 *   <li>{@link Type#STEP_END}       - 步骤结束（携带 stepIndex）</li>
 *   <li>{@link Type#DONE}           - 整个 ReAct 循环完成（携带 success + totalSteps）</li>
 *   <li>{@link Type#ERROR}          - 异常终止（携带 failureReason）</li>
 * </ul>
 *
 * <p>SSE 推送示例：
 * <pre>
 * event: STEP_START
 * data: {"stepIndex":1}
 *
 * event: THOUGHT
 * data: {"stepIndex":1,"thought":"需要校验 BPMN XML 结构"}
 *
 * event: ACTION
 * data: {"stepIndex":1,"action":"bpmn_validate","parameters":{"bpmnXml":"..."}}
 *
 * event: OBSERVATION
 * data: {"stepIndex":1,"observation":"校验通过"}
 *
 * event: FINAL_ANSWER
 * data: {"stepIndex":2,"finalAnswer":"<bpmn:definitions>...</bpmn:definitions>"}
 *
 * event: DONE
 * data: {"success":true,"totalSteps":2}
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-1)
 */
@Data
public class StreamEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 事件类型 */
    private Type type;
    /** 步骤序号（1-based，DONE/ERROR 时为最后一步） */
    private int stepIndex;
    /** 事件载荷（不同 type 对应不同字段） */
    private Map<String, Object> payload;
    /** 事件时间戳（毫秒） */
    private long timestamp;

    /** 事件类型枚举 */
    public enum Type {
        /** 步骤开始 */
        STEP_START,
        /** LLM 思考完成 */
        THOUGHT,
        /** LLM 决策动作 */
        ACTION,
        /** 工具执行流式片段（可选） */
        TOOL_DELTA,
        /** 工具执行观察结果 */
        OBSERVATION,
        /** LLM 输出增量片段（可选） */
        LLM_DELTA,
        /** 最终答案 */
        FINAL_ANSWER,
        /** 步骤结束 */
        STEP_END,
        /** 整个 ReAct 循环完成 */
        DONE,
        /** 异常终止 */
        ERROR,
        /**
         * 心跳保活（P2-5）。
         *
         * <p>LLM 调用耗时较长时（如 10s+），客户端可能因超时断开 SSE 连接。
         * 心跳事件定期推送，告知客户端服务端仍在工作，防止中间代理 / 浏览器超时断连。
         */
        HEARTBEAT
    }

    /** 构造简单事件（仅 type） */
    public static StreamEvent of(Type type) {
        StreamEvent e = new StreamEvent();
        e.type = type;
        e.timestamp = System.currentTimeMillis();
        e.payload = Map.of();
        return e;
    }

    /** 构造带步骤的事件 */
    public static StreamEvent of(Type type, int stepIndex) {
        StreamEvent e = of(type);
        e.stepIndex = stepIndex;
        return e;
    }

    /** 构造带步骤和载荷的事件 */
    public static StreamEvent of(Type type, int stepIndex, Map<String, Object> payload) {
        StreamEvent e = of(type, stepIndex);
        e.payload = payload == null ? Map.of() : payload;
        return e;
    }

    /** 构造 DONE 事件 */
    public static StreamEvent done(int totalSteps, boolean success) {
        StreamEvent e = of(Type.DONE, totalSteps);
        e.payload = Map.of(
                "success", success,
                "totalSteps", totalSteps);
        return e;
    }

    /** 构造 ERROR 事件 */
    public static StreamEvent error(int stepIndex, String reason) {
        StreamEvent e = of(Type.ERROR, stepIndex);
        e.payload = Map.of(
                "success", false,
                "failureReason", reason == null ? "" : reason);
        return e;
    }

    /**
     * 构造 HEARTBEAT 事件（P2-5）。
     *
     * <p>心跳事件仅携带时间戳，无业务数据，用于保活。
     *
     * @return HEARTBEAT 事件
     */
    public static StreamEvent heartbeat() {
        return of(Type.HEARTBEAT);
    }
}
