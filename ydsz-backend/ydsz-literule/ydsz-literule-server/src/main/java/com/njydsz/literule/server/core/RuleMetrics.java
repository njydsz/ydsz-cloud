package com.njydsz.literule.server.core;

import com.njydsz.literule.api.RuleSeverity;

/**
 * 规则引擎监控指标接口。
 *
 * <p>P1-5: 从类重构为接口，解决 Java 单继承限制。
 * {@link MicrometerRuleMetrics} 可同时继承 {@code AbstractModuleMetrics} 和实现本接口，
 * 满足 ArchUnit R25 架构规则（所有 *Metrics 类必须继承 AbstractModuleMetrics）。
 *
 * <p>双轨制实现：
 * <ul>
 *   <li>{@link MicrometerRuleMetrics} — Micrometer 实现，暴露 Prometheus 指标</li>
 *   <li>{@link InMemoryRuleMetrics} — 内存计数器实现，测试/降级场景使用</li>
 * </ul>
 *
 * @since 1.1.0
 * @author ydsz-team
 */
public interface RuleMetrics {

    /**
     * 记录单次评估
     */
    void recordEvaluation(String ruleCode, String scenario, boolean triggered,
                          RuleSeverity severity, boolean error, long elapsedMs);

    /**
     * 记录熔断状态
     */
    void recordBreakerState(String ruleCode, String state);

    /**
     * 记录 Trace 队列积压
     */
    void recordTraceQueueSize(int queueSize);

    /**
     * 记录当前注册规则数
     */
    void recordRegisteredRules(int count);

    /**
     * 记录单次评估遍历的规则数
     */
    void recordEvaluatedRules(int count);

    /**
     * 记录慢规则告警
     */
    void recordSlowRule(String ruleCode, long elapsedMs, long thresholdMs);

    /**
     * 获取累计评估次数（健康检查读取入口）
     *
     * @return 累计评估次数
     * @since 1.0.0
     */
    long getTotalEvaluations();

    /**
     * 获取累计触发次数（健康检查读取入口）
     *
     * @return 累计触发次数
     * @since 1.0.0
     */
    long getTotalTriggered();

    /**
     * 获取累计异常次数（健康检查读取入口）
     *
     * @return 累计异常次数
     * @since 1.0.0
     */
    long getTotalErrors();

    /**
     * 获取当前注册规则数（健康检查读取入口）
     *
     * @return 当前注册规则数
     * @since 1.0.0
     */
    int getRegisteredRules();

    /**
     * 获取最近一次评估遍历的规则数（统计快照读取入口）
     *
     * @return 最近一次评估遍历的规则数
     * @since 1.0.0
     */
    int getLastEvaluatedRules();
}
