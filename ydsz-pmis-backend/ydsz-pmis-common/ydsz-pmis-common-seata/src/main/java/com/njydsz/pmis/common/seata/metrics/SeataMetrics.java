package com.njydsz.pmis.common.seata.metrics;

import java.util.concurrent.atomic.AtomicLong;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import org.springframework.beans.factory.ObjectProvider;

import com.njydsz.pmis.common.seata.api.TransactionType;

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
 * @author ydsz-pmis-team
 * @since 3.5.0
 */
public class SeataMetrics {

    private final ObjectProvider<MeterRegistry> registryProvider;

    private final AtomicLong activeTxCount = new AtomicLong(0);
    private final AtomicLong confirmRetryCount = new AtomicLong(0);
    private final AtomicLong cancelRetryCount = new AtomicLong(0);
    private final AtomicLong sagaCompensationCount = new AtomicLong(0);

    public SeataMetrics(ObjectProvider<MeterRegistry> registryProvider) {
        this.registryProvider = registryProvider;
    }

    /**
     * 记录事务开始
     */
    public void recordTxStart(TransactionType type) {
        activeTxCount.incrementAndGet();
    }

    /**
     * 记录事务完成
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
                    .record(java.time.Duration.ofMillis(durationMs));
        }
    }

    public void recordConfirmRetry() {
        confirmRetryCount.incrementAndGet();
        MeterRegistry registry = registryProvider.getIfAvailable();
        if (registry != null) {
            Counter.builder("seata.tcc.confirm.retry")
                    .register(registry)
                    .increment();
        }
    }

    public void recordCancelRetry() {
        cancelRetryCount.incrementAndGet();
        MeterRegistry registry = registryProvider.getIfAvailable();
        if (registry != null) {
            Counter.builder("seata.tcc.cancel.retry")
                    .register(registry)
                    .increment();
        }
    }

    public void recordSagaCompensation() {
        sagaCompensationCount.incrementAndGet();
        MeterRegistry registry = registryProvider.getIfAvailable();
        if (registry != null) {
            Counter.builder("seata.saga.compensation.count")
                    .register(registry)
                    .increment();
        }
    }

    public long getActiveTxCount() {
        return activeTxCount.get();
    }

    public long getConfirmRetryCount() {
        return confirmRetryCount.get();
    }

    public long getCancelRetryCount() {
        return cancelRetryCount.get();
    }

    public long getSagaCompensationCount() {
        return sagaCompensationCount.get();
    }
}
