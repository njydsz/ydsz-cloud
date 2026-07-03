package com.njydsz.pmis.workflow.dmn;

/**
 * DMN 命中策略枚举
 *
 * <p>定义决策表的命中策略，决定当多条规则同时匹配时的返回行为。
 * <ul>
 *   <li>{@link #UNIQUE} — 仅允许一条规则命中，多条命中报错</li>
 *   <li>{@link #FIRST} — 返回第一条命中的规则（按定义顺序）</li>
 *   <li>{@link #PRIORITY} — 按输出值优先级返回第一条命中</li>
 *   <li>{@link #ANY} — 返回任意一条命中（多条命中不报错）</li>
 *   <li>{@link #COLLECT} — 返回所有命中行，按聚合运算符聚合</li>
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

    /** 按输出值优先级返回第一条命中 */
    PRIORITY("优先级命中"),

    /** 返回任意一条命中（多条命中不报错） */
    ANY("任意命中"),

    /** 返回所有命中行，按聚合运算符聚合 */
    COLLECT("聚合命中");

    /** 策略描述 */
    private final String desc;

    DmnHitPolicy(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }
}
