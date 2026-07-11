package com.njydsz.pmis.agent.server.orchestration;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 业务事实（只读上下文） */
    private Map<String, Object> facts = new HashMap<>();
    /** 中间结果：agentType -> result（任何 Agent 写完即对其他 Agent 可见） */
    private Map<String, Object> scratch = new HashMap<>();
    /** 决策路径追踪：每一步一个 entry */
    private List<TraceEntry> trace = new ArrayList<>();

    /**
     * 构造黑板并初始化事实。
     *
     * @param facts 初始事实，可空
     */
    public AgentBlackboard(Map<String, Object> facts) {
        if (facts != null) this.facts = new HashMap<>(facts);
    }

    /**
     * 取事实。
     *
     * @param key 事实键
     * @return 事实值；不存在返回 null
     */
    public Object fact(String key) {
        return facts.get(key);
    }

    /**
     * 取中间结果。
     *
     * @param agentType Agent 类型
     * @return 该 Agent 的中间结果；不存在返回 null
     */
    public Object scratch(String agentType) {
        return scratch.get(agentType);
    }

    /**
     * 写入中间结果。
     *
     * @param agentType Agent 类型
     * @param result    中间结果
     */
    public void putScratch(String agentType, Object result) {
        scratch.put(agentType, result);
    }

    /**
     * 追加决策路径。
     *
     * @param agentType  Agent 类型
     * @param mode       编排模式，可空
     * @param score      得分，可空
     * @param confidence 置信度，可空
     * @param note       备注
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
        /** 序列化版本号 */
        @Serial
        private static final long serialVersionUID = 1L;
        /** Agent 类型 */
        private String agentType;
        /** 编排模式码（OrchestrationMode.code） */
        private String mode;
        /** 得分 */
        private BigDecimal score;
        /** 置信度 */
        private BigDecimal confidence;
        /** 备注 */
        private String note;
        /** 时间戳（毫秒） */
        private long ts;
    }
}
