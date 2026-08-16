package com.njydsz.common.seata.metrics;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.ObjectProvider;
import com.njydsz.common.seata.api.TransactionType;

/**
 * 分布式事务指标采集
 *
 * <p>通过 Micrometer 上报事务指标到 Prometheus：
 * <ul>
 *   <li>{@code seata.tx.count{type,result}} - 事务执行计数</li>
 *   <li>{@code seata.tx.duration{type}} - 事务执行耗时 Timer（P50/P90/P99）</li>
 *   <li>{@code seata.tcc.confirm.retry} - TCC Confirm 重试次数</li>
 *   <li>{@code seata.tcc.cancel.retry} - TCC Cancel 重试次数</li>
 *   <li>{@code seata.saga.compensation.count} - SAGA 补偿次数</li>
 *   <li>{@code seata.tx.active} - 活跃事务数 Gauge</li>
 * </ul>
 *
 * <p><b>降级策略</b>：当 MeterRegistry 不可用时，内部仍维护计数器和活跃事务数，
 * 并通过 {@link #getActiveTxCount()} 暴露。一旦 Registry 可用（如动态加载），
 * 后续指标上报自动恢复。Gauge 在首次 Registry 可用时注册。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class SeataMetrics {

    private static final String ACTIVE_TX_GAUGE_NAME = "seata.tx.active";

    private final ObjectProvider<MeterRegistry> registryProvider;

    private final AtomicLong activeTxCount = new AtomicLong(0);

    /** Gauge 注册标记（保证只注册一次） */
    private volatile boolean gaugeRegistered = false;

    /**
     * 构造分布式事务指标采集器
     *
     * @param registryProvider Micrometer MeterRegistry 提供者（可选，不可用时降级为内存计数器）
     */
    public SeataMetrics(ObjectProvider<MeterRegistry> registryProvider) {
        this.registryProvider = registryProvider;
    }

    /**
     * 记录事务开始，活跃事务数加一
     *
     * @param type 事务类型（LOCAL/TCC/SEATA_AT/SAGA）
     */
    public void recordTxStart(TransactionType type) {
        activeTxCount.incrementAndGet();
        ensureGaugeRegistered();
    }

    /**
     * 记录事务完成，活跃事务数减一，并上报计数和耗时指标
     *
     * @param type       事务类型
     * @param result     执行结果（如 "success"、"fail"）
     * @param durationMs 事务执行耗时（毫秒）
     */
    public void recordTxComplete(TransactionType type, String result, long durationMs) {
        activeTxCount.decrementAndGet();

        MeterRegistry registry = registryProvider.getIfAvailable();
        if (registry != null) {
            Counter.builder("seata.tx.count")
                    .tag("type", type.name())
                    .tag("result", result)
                    .register(registry)
                    .increment();

            Timer.builder("seata.tx.duration")
                    .tag("type", type.name())
                    .register(registry)
                    .record(Duration.ofMillis(durationMs));
        }
    }

    /**
     * 记录 TCC Confirm 重试次数
     */
    public void recordConfirmRetry() {
        MeterRegistry registry = registryProvider.getIfAvailable();
        if (registry != null) {
            Counter.builder("seata.tcc.confirm.retry")
                    .register(registry)
                    .increment();
        }
    }

    /**
     * 记录 TCC Cancel 重试次数
     */
    public void recordCancelRetry() {
        MeterRegistry registry = registryProvider.getIfAvailable();
        if (registry != null) {
            Counter.builder("seata.tcc.cancel.retry")
                    .register(registry)
                    .increment();
        }
    }

    /**
     * 记录 SAGA 补偿执行次数
     */
    public void recordSagaCompensation() {
        MeterRegistry registry = registryProvider.getIfAvailable();
        if (registry != null) {
            Counter.builder("seata.saga.compensation.count")
                    .register(registry)
                    .increment();
        }
    }

    /**
     * 获取当前活跃事务数
     *
     * <p>无论 MeterRegistry 是否可用，此方法始终返回当前活跃事务数量。
     * 当 Registry 可用时，该值还会通过 {@code seata.tx.active} Gauge 自动上报。
     *
     * @return 活跃事务数
     */
    public long getActiveTxCount() {
        return activeTxCount.get();
    }

    /**
     * 确保活跃事务数 Gauge 已注册（线程安全，仅注册一次）
     *
     * <p>采用懒注册策略：首次事务开始时检查 Registry 可用性并注册 Gauge。
     * 如果 Registry 后续才可用，下次事务开始时自动注册。
     */
    private void ensureGaugeRegistered() {
        if (gaugeRegistered) {
            return;
        }
        synchronized (this) {
            if (gaugeRegistered) {
                return;
            }
            MeterRegistry registry = registryProvider.getIfAvailable();
            if (registry != null) {
                Gauge.builder(ACTIVE_TX_GAUGE_NAME, activeTxCount, AtomicLong::get)
                        .description("Current active distributed transaction count")
                        .register(registry);
                gaugeRegistered = true;
            }
        }
    }
}
