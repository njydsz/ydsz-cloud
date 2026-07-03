package com.njydsz.pmis.literule.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LiteRule 配置属性
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Data
@ConfigurationProperties(prefix = "pmis.literule")
public class LiteRuleProperties {

    /** 是否启用自动注册内置规则 */
    private boolean autoRegisterBuiltinRules = true;

    /** 是否启用规则热加载（监听 RuleConfigRefreshEvent） */
    private boolean hotReloadEnabled = true;

    /** 是否启用执行统计 */
    private boolean statsEnabled = true;

    /** 是否启用 dry-run 仿真 */
    private boolean dryRunEnabled = true;

    /** 是否启用表达式沙箱（限制危险函数和类访问） */
    private boolean sandboxEnabled = true;

    /** 是否启用执行轨迹记录（1.4.0） */
    private boolean traceEnabled = true;

    /** 异步 Trace 队列容量 */
    private int traceQueueCapacity = 5000;

    /** 异步 Trace 批量写入大小 */
    private int traceBatchSize = 100;

    /** 异步 Trace 刷新间隔（毫秒） */
    private long traceFlushIntervalMs = 2000;

    /** 单规则执行超时（毫秒，0 表示不限制，1.4.0） */
    private long ruleTimeoutMs = 0;

    /** 规则熔断错误率阈值（0~1.0，达到阈值时熔断该规则，1.4.0） */
    private double circuitBreakerErrorRate = 0.5;

    /** 规则熔断最小评估次数（达到该次数后才计算错误率，1.4.0） */
    private int circuitBreakerMinEvaluations = 100;

    /**
     * 是否启用规则灰度路由（1.4.0）
     *
     * <p>启用后，对带 canaryRatio > 0 且配置了候选表达式的规则，
     * 按比例将流量分到候选版本，结果会被标记 canary=true。
     */
    private boolean canaryEnabled = true;

    /**
     * 是否启用规则冲突检测（1.4.0）
     *
     * <p>启用后，规则保存前会检测与现有规则的潜在冲突
     * （条件重复、严重度矛盾、命名冲突）。
     */
    private boolean conflictDetectionEnabled = true;

    /**
     * ERROR 级别冲突是否阻塞保存（1.4.0）
     *
     * <p>true：检测到 CONTRADICTORY_SEVERITY 等确定性冲突时抛异常阻塞保存；
     * false：仅记录日志，不阻塞保存。
     */
    private boolean conflictDetectionBlockOnError = true;
}
