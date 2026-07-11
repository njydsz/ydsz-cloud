package com.njydsz.pmis.literule.server.core;

import com.njydsz.pmis.literule.api.RuleSeverity;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 规则引擎监控指标
 *
 * <p>对齐大厂规则引擎监控标准，提供 6 类指标：
 * <ul>
 *   <li>评估次数（按规则、按场景）</li>
 *   <li>触发次数（按规则、按严重度）</li>
 *   <li>异常次数（按规则）</li>
 *   <li>评估耗时分布（按规则，P50/P95/P99）</li>
 *   <li>熔断状态（按规则：CLOSED/OPEN/HALF_OPEN）</li>
 *   <li>Trace 队列积压</li>
 * </ul>
 *
 * <p>设计为双轨制：
 * <ul>
 *   <li>当 Micrometer {@code MeterRegistry} 可用时，通过 {@link MicrometerRuleMetrics} 桥接到 Prometheus</li>
 *   <li>当 MeterRegistry 不可用时（如单元测试），退化为内存计数器</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Slf4j
public class RuleMetrics {

    /** 总评估次数 */
    protected final AtomicLong totalEvaluations = new AtomicLong(0);
    /** 总触发次数 */
    protected final AtomicLong totalTriggered = new AtomicLong(0);
    /** 总异常次数 */
    protected final AtomicLong totalErrors = new AtomicLong(0);
    /** 总耗时（毫秒） */
    protected final AtomicLong totalElapsedMs = new AtomicLong(0);
    /** 当前注册规则数（用于规则规模监控，评估 RETE 引入必要性） */
    protected volatile int registeredRules = 0;
    /** 单次评估遍历的规则数（最近一次 evaluate 调用实际遍历的规则数） */
    protected volatile int lastEvaluatedRules = 0;

    /**
     * 记录单次评估
     *
     * @param ruleCode    规则编码
     * @param scenario    场景
     * @param triggered   是否触发
     * @param severity    严重度（未触发为 null）
     * @param error       是否异常
     * @param elapsedMs   耗时
     */
    public void recordEvaluation(String ruleCode, String scenario, boolean triggered,
                                  RuleSeverity severity, boolean error, long elapsedMs) {
        totalEvaluations.incrementAndGet();
        totalElapsedMs.addAndGet(elapsedMs);
        if (triggered) totalTriggered.incrementAndGet();
        if (error) totalErrors.incrementAndGet();
    }

    /**
     * 记录熔断状态
     *
     * @param ruleCode 规则编码
     * @param state    状态
     */
    public void recordBreakerState(String ruleCode, String state) {
        // 默认实现：仅日志
        log.debug("[LiteRule-Metrics] 规则 {} 熔断状态: {}", ruleCode, state);
    }

    /**
     * 记录 Trace 队列积压
     *
     * @param queueSize 队列大小
     */
    public void recordTraceQueueSize(int queueSize) {
        log.debug("[LiteRule-Metrics] Trace 队列积压: {}", queueSize);
    }

    /**
     * 记录当前注册规则数（规则规模监控指标）
     *
     * <p>用于评估是否需要引入 RETE 算法：
     * <ul>
     *   <li>规则数 &lt; 200：顺序匹配性能可接受</li>
     *   <li>规则数 200~1000：建议监控平均评估耗时，考虑条件索引优化</li>
     *   <li>规则数 &gt; 1000：建议评估引入 RETE 算法或条件索引</li>
     * </ul>
     *
     * @param count 当前注册规则数
     * @since 1.5.0
     */
    public void recordRegisteredRules(int count) {
        this.registeredRules = count;
        log.debug("[LiteRule-Metrics] 注册规则数: {}", count);
    }

    /**
     * 记录单次评估遍历的规则数
     *
     * @param count 遍历规则数
     * @since 1.5.0
     */
    public void recordEvaluatedRules(int count) {
        this.lastEvaluatedRules = count;
    }

    public long getTotalEvaluations() { return totalEvaluations.get(); }
    public long getTotalTriggered() { return totalTriggered.get(); }
    public long getTotalErrors() { return totalErrors.get(); }
    public long getTotalElapsedMs() { return totalElapsedMs.get(); }
    public int getRegisteredRules() { return registeredRules; }
    public int getLastEvaluatedRules() { return lastEvaluatedRules; }
}
