package com.njydsz.pmis.agent.orchestration;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Agent 黑板（Blackboard Pattern）
 *
 * <p>多 Agent 编排时共享上下文的事实存储。
 * <ul>
 *   <li>facts       - 业务侧沉淀的事实（多 Agent 可见只读）</li>
 *   <li>scratch     - 编排过程中的临时中间结果（按 agentType 维度隔离）</li>
 *   <li>trace       - 决策路径追踪：每一步 Agent 的输出按时间序</li>
 * </ul>
 *
 * <p>所有 Agent 在同一黑板上读 / 写，最终由协调器汇总。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
public class AgentBlackboard implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 业务事实（只读上下文） */
    private Map<String, Object> facts = new HashMap<>();
    /** 中间结果：agentType -> result（任何 Agent 写完即对其他 Agent 可见） */
    private Map<String, Object> scratch = new HashMap<>();
    /** 决策路径追踪：每一步一个 entry */
    private java.util.List<TraceEntry> trace = new java.util.ArrayList<>();

    public AgentBlackboard(Map<String, Object> facts) {
        if (facts != null) this.facts = new HashMap<>(facts);
    }

    /**
     * 取事实
     */
    public Object fact(String key) {
        return facts.get(key);
    }

    /**
     * 取中间结果
     */
    public Object scratch(String agentType) {
        return scratch.get(agentType);
    }

    /**
     * 写入中间结果
     */
    public void putScratch(String agentType, Object result) {
        scratch.put(agentType, result);
    }

    /**
     * 追加决策路径
     */
    public void appendTrace(String agentType, OrchestrationMode mode, BigDecimal score,
                            BigDecimal confidence, String note) {
        TraceEntry e = new TraceEntry();
        e.setAgentType(agentType);
        e.setMode(mode == null ? null : mode.getCode());
        e.setScore(score);
        e.setConfidence(confidence);
        e.setNote(note);
        e.setTs(System.currentTimeMillis());
        trace.add(e);
    }

    @Data
    @NoArgsConstructor
    public static class TraceEntry implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private String agentType;
        private String mode;
        private BigDecimal score;
        private BigDecimal confidence;
        private String note;
        private long ts;
    }
}
