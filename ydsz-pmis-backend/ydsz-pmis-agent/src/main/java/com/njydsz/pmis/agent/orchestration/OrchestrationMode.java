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
    /** 顺序执行：前一个 Agent 的输出作为下一个 Agent 的输入 */
    SEQUENTIAL("SEQUENTIAL", "顺序执行"),
    /** 并行执行：所有 Agent 同时跑，最后合并到黑板 */
    PARALLEL("PARALLEL", "并行执行"),
    /** 投票融合：多 Agent 独立打分后按权重加权融合 */
    VOTING("VOTING", "投票融合"),
    /** 级联执行：按置信度阈值选择输出，未达标时触发下一个 Agent 兜底 */
    CASCADE("CASCADE", "级联执行");

    /** 状态码 */
    private final String code;
    /** 描述 */
    private final String desc;

    OrchestrationMode(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 获取状态码。
     *
     * @return 状态码
     */
    public String getCode() { return code; }
    /**
     * 获取描述。
     *
     * @return 描述
     */
    public String getDesc() { return desc; }

    /**
     * 根据状态码解析枚举。
     *
     * @param code 状态码，大小写不敏感，为 null 时返回 null
     * @return 匹配到的枚举值；未匹配返回 null
     */
    public static OrchestrationMode fromCode(String code) {
        if (code == null) return null;
        for (OrchestrationMode m : values()) {
            if (m.code.equalsIgnoreCase(code)) return m;
        }
        return null;
    }
}
