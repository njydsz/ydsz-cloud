package com.njydsz.agent.domain.teamrun;

/**
 * Team Run 协作模式枚举。
 *
 * <p>定义多 Agent 之间的协作方式。</p>
 *
 * @author ydsz-agent
 * @since 1.0.0
 */
public enum TeamRunPattern {

    /**
     * 顺序执行模式。
     *
     * <p>Agent 按顺序依次执行，前一个 Agent 的输出作为后一个 Agent 的输入。
     * 适用于流水线式任务处理。</p>
     */
    SEQUENTIAL("SEQUENTIAL", "顺序执行"),

    /**
     * 并行执行模式。
     *
     * <p>所有 Agent 同时执行，各自独立处理任务。
     * 适用于可并行化的独立子任务。</p>
     */
    PARALLEL("PARALLEL", "并行执行"),

    /**
     * 层级模式（Leader-Worker）。
     *
     * <p>Leader Agent 分配任务给多个 Worker Agent，Worker 完成后由 Leader 汇总。
     * 适用于需要任务分发和结果汇总的场景。</p>
     */
    HIERARCHICAL("HIERARCHICAL", "层级模式"),

    /**
     * 协商模式。
     *
     * <p>Agent 之间可以相互通信、协商，最终达成共识。
     * 适用于需要多视角分析、投票决策的场景。</p>
     */
    NEGOTIATION("NEGOTIATION", "协商模式");

    private final String code;
    private final String description;

    TeamRunPattern(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据状态码查找枚举。
     *
     * @param code 状态码
     * @return 对应枚举，未找到返回 null
     */
    public static TeamRunPattern fromCode(String code) {
        for (TeamRunPattern pattern : values()) {
            if (pattern.code.equals(code)) {
                return pattern;
            }
        }
        return null;
    }
}
