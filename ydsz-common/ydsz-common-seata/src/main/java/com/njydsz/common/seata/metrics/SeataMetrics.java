package com.njydsz.common.seata.metrics;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.ObjectProvider;

import com.njydsz.common.seata.api.TransactionType;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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
 * <p>当 MeterRegistry 不可用时降级为内存计数器。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class SeataMetrics {

    private final ObjectProvider<MeterRegistry> registryProvider;

    private final AtomicLong activeTxCount = new AtomicLong(0);

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
     * @return 活跃事务数
     */
    public long getActiveTxCount() {
        return activeTxCount.get();
    }

}
