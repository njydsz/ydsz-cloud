package com.njydsz.pmis.workflow.dmn;

/**
 * DMN 命中策略枚举
 *
 * <p>定义决策表的命中策略，决定当多条规则同时匹配时的返回行为。
 *
 * <p>对标 OMG DMN 1.3+ 规范与 Camunda/Flowable DMN 实现。
 *
 * <ul>
 *   <li>{@link #UNIQUE} — 仅允许一条规则命中，多条命中报错</li>
 *   <li>{@link #FIRST} — 返回第一条命中的规则（按定义顺序）</li>
 *   <li>{@link #PRIORITY} — 按输出值优先级返回第一条命中
 *       <p>P2-10 修复：按首个输出列的 {@code allowedValues} 顺序对命中行排序，
 *       返回排名第一的那条。若 {@code allowedValues} 未定义，回退为 FIRST 语义并告警。</li>
 *   <li>{@link #ANY} — 返回任意一条命中（多条命中不报错，但所有命中行输出值必须相同）</li>
 *   <li>{@link #COLLECT} — 返回所有命中行，按聚合运算符聚合</li>
 *   <li>{@link #RULE_ORDER} — P2-10 新增：返回所有命中行（按规则定义顺序，不排序）</li>
 *   <li>{@link #OUTPUT_ORDER} — P2-10 新增：返回所有命中行（按输出值优先级排序）
 *       <p>按输出列的 {@code allowedValues} 顺序对所有命中行排序后返回全部。</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
public enum DmnHitPolicy {

    /** 仅允许一条命中，多条命中报错 */
    UNIQUE("唯一命中"),

    /** 返回第一条命中（按定义顺序） */
    FIRST("首条命中"),

    /** 按输出值优先级返回第一条命中（首个输出列的 allowedValues 决定优先级） */
    PRIORITY("优先级命中"),

    /** 返回任意一条命中（多条命中不报错） */
    ANY("任意命中"),

    /** 返回所有命中行，按聚合运算符聚合 */
    COLLECT("聚合命中"),

    /** P2-10: 返回所有命中行（按规则定义顺序，不排序） */
    RULE_ORDER("规则顺序命中"),

    /** P2-10: 返回所有命中行（按输出值优先级排序） */
    OUTPUT_ORDER("输出顺序命中");

    /** 策略描述 */
    private final String desc;

    DmnHitPolicy(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }
}
