package com.njydsz.pmis.literule.api;

/**
 * 规则接口
 *
 * <p>所有规则（Java 编码规则 / 表达式规则 / 数据库配置规则）均实现此接口。
 * 引擎遍历已注册规则，调用 {@link #evaluate(RuleContext)} 进行评估。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
public interface Rule {

    /** 默认优先级（数值越小优先级越高） */
    int DEFAULT_PRIORITY = 100;

    /**
     * 规则编码（全局唯一）
     *
     * @return 规则编码
     */
    String getCode();

    /**
     * 规则名称（中文）
     *
     * @return 规则名称
     */
    String getName();

    /**
     * 规则类别（如 EVM / COST / BENCH / UTILIZATION）
     *
     * @return 规则类别
     */
    String getCategory();

    /**
     * 优先级（数值越小越先执行，默认 100）
     *
     * @return 优先级
     */
    default int getPriority() {
        return DEFAULT_PRIORITY;
    }

    /**
     * 作用域（影响范围）
     *
     * <p>用于场景过滤：当 {@link RuleContext#getScenario()} 非空且非 "DEFAULT" 时，
     * 仅评估 scope 为 null/"ALL" 或与 scenario 匹配的规则。
     * 默认返回 null 表示适用于全部场景。
     *
     * @return 作用域；null 表示适用于全部场景
     * @since 1.3.0
     */
    default String getScope() {
        return null;
    }

    /**
     * 规则定义快照（用于灰度路由、统计、Trace）
     *
     * <p>默认返回 null，表示该规则为编码规则（无动态 RuleDefinition）。
     * 表达式规则 / 决策表规则应覆盖此方法返回原始定义。
     *
     * @return 规则定义；null 表示编码规则
     * @since 1.4.0
     */
    default RuleDefinition getRuleDefinition() {
        return null;
    }

    /**
     * 互斥组（Mutex Group）
     *
     * <p>同一互斥组内，按优先级（priority 升序）遍历，首个命中的规则执行后，
     * 同组其余规则跳过评估。null 表示该规则不归属任何互斥组。
     *
     * <p>典型场景：同一业务维度配置多条不同阈值的规则，仅希望最严重的那个生效。
     * 例如：金额>1000（RED）与 金额>500（YELLOW）归属同一互斥组，避免重复告警。
     *
     * @return 互斥组名称；null 或空串表示无互斥组
     * @since 1.5.0
     */
    default String getMutexGroup() {
        return null;
    }

    /**
     * 租户 ID（多租户运行时隔离）
     *
     * <p>1.5.0 起启用运行时租户过滤：{@link com.njydsz.pmis.literule.core.DefaultRuleEngine}
     * 在评估前会比较 {@code rule.getTenantId()} 与 {@link RuleContext#getTenantId()}，
     * 仅当两者匹配时才评估该规则。
     *
     * <p>默认返回 {@code 1L}（单租户部署），向后兼容。
     * {@link com.njydsz.pmis.literule.impl.ExpressionRule} 等基于 {@link RuleDefinition}
     * 的规则会覆写此方法返回定义中的 tenantId。
     *
     * @return 租户 ID；默认 1L
     * @since 1.5.0
     */
    default long getTenantId() {
        return 1L;
    }

    /**
     * 评估规则
     *
     * @param context 规则上下文（事实数据）
     * @return 评估结果；未触发时返回 {@link RuleResult#notTriggered(String)}
     */
    RuleResult evaluate(RuleContext context);
}
