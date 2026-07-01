package com.njydsz.pmis.agent.orchestration;

/**
 * 多智能体编排模式
 *
 * <p>借鉴 AgentScope 多智能体协同设计思想，提供 4 种编排范式：
 * <ul>
 *   <li>SEQUENTIAL 顺序执行：前一个 Agent 的输出作为下一个 Agent 的输入，按声明顺序串行执行</li>
 *   <li>PARALLEL  并行执行：所有 Agent 同时跑（线程池），最后合并到黑板</li>
 *   <li>VOTING    投票融合：多 Agent 独立打分后按权重加权融合（适合多视角风险评估）</li>
 *   <li>CASCADE   级联执行：按置信度阈值选择输出，未达标时触发下一个 Agent 兜底</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum OrchestrationMode {
    SEQUENTIAL("SEQUENTIAL", "顺序执行"),
    PARALLEL("PARALLEL", "并行执行"),
    VOTING("VOTING", "投票融合"),
    CASCADE("CASCADE", "级联执行");

    private final String code;
    private final String desc;

    OrchestrationMode(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() { return code; }
    public String getDesc() { return desc; }

    public static OrchestrationMode fromCode(String code) {
        if (code == null) return null;
        for (OrchestrationMode m : values()) {
            if (m.code.equalsIgnoreCase(code)) return m;
        }
        return null;
    }
}
