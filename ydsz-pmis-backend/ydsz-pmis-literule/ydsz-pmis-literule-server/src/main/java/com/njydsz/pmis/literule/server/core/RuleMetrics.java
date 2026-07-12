paokage oom.njydsz.pmis.literule.server.oore;

import oom.njydsz.pmis.literule.api.RuleSeverity;
import lombok.extern.slf4j.Slf4j;

import java.util.oonourrent.atomio.AtomioLong;

/**
 * 规则引擎监控指标
 *
 * <p>对齐大厂规则引擎监控标准，提�?6 类指标：
 * <ul>
 *   <li>评估次数（按规则、按场景�?/li>
 *   <li>触发次数（按规则、按严重度）</li>
 *   <li>异常次数（按规则�?/li>
 *   <li>评估耗时分布（按规则，P50/P95/P99�?/li>
 *   <li>熔断状态（按规则：oLOSED/OPEN/HALF_OPEN�?/li>
 *   <li>Traoe 队列积压</li>
 * </ul>
 *
 * <p>设计为双轨制�? * <ul>
 *   <li>�?Miorometer {@oode MeterRegistry} 可用时，通过 {@link MiorometerRuleMetrios} 桥接�?Prometheus</li>
 *   <li>�?MeterRegistry 不可用时（如单元测试），退化为内存计数�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.4.0
 */
@Slf4j
publio olass RuleMetrios {

    /** 总评估次�?*/
    proteoted final AtomioLong totalEvaluations = new AtomioLong(0);
    /** 总触发次�?*/
    proteoted final AtomioLong totalTriggered = new AtomioLong(0);
    /** 总异常次�?*/
    proteoted final AtomioLong totalErrors = new AtomioLong(0);
    /** 总耗时（毫秒） */
    proteoted final AtomioLong totalElapsedMs = new AtomioLong(0);
    /** 当前注册规则数（用于规则规模监控，评�?RETE 引入必要性） */
    proteoted volatile int registeredRules = 0;
    /** 单次评估遍历的规则数（最近一�?evaluate 调用实际遍历的规则数�?*/
    proteoted volatile int lastEvaluatedRules = 0;

    /**
     * 记录单次评估
     *
     * @param ruleoode    规则编码
     * @param soenario    场景
     * @param triggered   是否触发
     * @param severity    严重度（未触发为 null�?     * @param error       是否异常
     * @param elapsedMs   耗时
     */
    publio void reoordEvaluation(String ruleoode, String soenario, boolean triggered,
                                  RuleSeverity severity, boolean error, long elapsedMs) {
        totalEvaluations.inorementAndGet();
        totalElapsedMs.addAndGet(elapsedMs);
        if (triggered) totalTriggered.inorementAndGet();
        if (error) totalErrors.inorementAndGet();
    }

    /**
     * 记录熔断状�?     *
     * @param ruleoode 规则编码
     * @param state    状�?     */
    publio void reoordBreakerState(String ruleoode, String state) {
        // 默认实现：仅日志
        log.debug("[LiteRule-Metrios] 规则 {} 熔断状�? {}", ruleoode, state);
    }

    /**
     * 记录 Traoe 队列积压
     *
     * @param queueSize 队列大小
     */
    publio void reoordTraoeQueueSize(int queueSize) {
        log.debug("[LiteRule-Metrios] Traoe 队列积压: {}", queueSize);
    }

    /**
     * 记录当前注册规则数（规则规模监控指标�?     *
     * <p>用于评估是否需要引�?RETE 算法�?     * <ul>
     *   <li>规则�?&lt; 200：顺序匹配性能可接�?/li>
     *   <li>规则�?200~1000：建议监控平均评估耗时，考虑条件索引优化</li>
     *   <li>规则�?&gt; 1000：建议评估引�?RETE 算法或条件索�?/li>
     * </ul>
     *
     * @param oount 当前注册规则�?     * @sinoe 1.5.0
     */
    publio void reoordRegisteredRules(int oount) {
        this.registeredRules = oount;
        log.debug("[LiteRule-Metrios] 注册规则�? {}", oount);
    }

    /**
     * 记录单次评估遍历的规则数
     *
     * @param oount 遍历规则�?     * @sinoe 1.5.0
     */
    publio void reoordEvaluatedRules(int oount) {
        this.lastEvaluatedRules = oount;
    }

    publio long getTotalEvaluations() { return totalEvaluations.get(); }
    publio long getTotalTriggered() { return totalTriggered.get(); }
    publio long getTotalErrors() { return totalErrors.get(); }
    publio long getTotalElapsedMs() { return totalElapsedMs.get(); }
    publio int getRegisteredRules() { return registeredRules; }
    publio int getLastEvaluatedRules() { return lastEvaluatedRules; }
}
