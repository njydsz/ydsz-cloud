package com.njydsz.pmis.literule.orchestrator;

/**
 * 规则链类型枚举
 *
 * <p>定义规则编排的四种核心语义：
 * <ul>
 *   <li>{@link #THEN} - 顺序执行：节点依次串行执行，收集触发结果</li>
 *   <li>{@link #WHEN} - 并行执行：节点并发执行（基于 CompletableFuture），收集触发结果</li>
 *   <li>{@link #IF} - 条件执行：先对条件表达式求值，为 true 才执行动作规则</li>
 *   <li>{@link #SWITCH} - 分支选择：从上下文中取分支 key，执行对应分支规则</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
public enum RuleChainType {

    /** 顺序执行 */
    THEN("顺序"),

    /** 并行执行 */
    WHEN("并行"),

    /** 条件执行 */
    IF("条件"),

    /** 分支选择 */
    SWITCH("分支");

    /** 类型描述（中文） */
    private final String desc;

    RuleChainType(String desc) {
        this.desc = desc;
    }

    /**
     * 获取类型描述
     *
     * @return 描述文本
     */
    public String getDesc() {
        return desc;
    }
}
