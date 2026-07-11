package com.njydsz.pmis.cronjob.core.scheduler;

import com.njydsz.pmis.cronjob.config.CronjobProperties;
import com.njydsz.pmis.cronjob.metrics.CronjobMetrics;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 自适应批量调度器（P1-1）。
 *
 * <p>根据系统实时负载指标动态调整 JobScanner 的 batchSize：
 * <ul>
 *   <li>CPU 使用率高 → 缩减 batchSize，降低调度压力</li>
 *   <li>内存使用率高 → 缩减 batchSize，防止 OOM</li>
 *   <li>线程池活跃度高 → 缩减 batchSize，避免任务积压</li>
 *   <li>系统空闲 → 放大 batchSize，提升吞吐量</li>
 * </ul>
 *
 * <h3>负载评分模型</h3>
 * <p>综合 CPU、内存、线程池活跃度三项指标计算负载评分（0-1）：
 * <pre>
 *   loadScore = cpuUsage * 0.4 + memUsage * 0.3 + poolActive * 0.3
 * </pre>
 * <p>batchSize 计算公式：
 * <pre>
 *   batchSize = maxBatchSize - (maxBatchSize - minBatchSize) * loadScore
 * </pre>
 *
 * <h3>安全发布</h3>
 * <p>通过 {@link AtomicInteger} 安全发布当前 batchSize，JobScanner 每次扫描时读取最新值。
 * 调整频率为每 {@code evalIntervalSeconds} 秒一次，避免频繁波动。
 *
 * <p>仅在 {@code pmis.cronjob.adaptive-batch.enabled=true} 时启用。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnBean(name = "cronjobMetrics")
@ConditionalOnProperty(name = "pmis.cronjob.adaptive-batch.enabled", havingValue = "true")
public class AdaptiveBatchScheduler {

    private final CronjobProperties cronjobProperties;
    private final ObjectProvider<CronjobMetrics> cronjobMetricsProvider;

    /** 当前自适应 batchSize（JobScanner 读取此值） */
    private final AtomicInteger currentBatchSize = new AtomicInteger();

    /** 线程池活跃度（由 DefaultTaskDispatcher 更新，0-100） */
    private final AtomicInteger poolActivePct = new AtomicInteger(0);

    private final MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
    private final OperatingSystemMXBean osMXBean = ManagementFactory.getOperatingSystemMXBean();
    private final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

    @PostConstruct
    public void init() {
        CronjobProperties.AdaptiveBatch config = cronjobProperties.getAdaptiveBatch();
        // 初始值使用配置的 batchSize
        currentBatchSize.set(cronjobProperties.getScanner().getBatchSize());
        log.info("[AdaptiveBatch] 初始化完成, initialBatchSize={} min={} max={} cpuThreshold={} memThreshold={} poolThreshold={}",
                currentBatchSize.get(), config.getMinBatchSize(), config.getMaxBatchSize(),
                config.getCpuThreshold(), config.getMemThreshold(), config.getPoolActiveThreshold());
    }

    /**
     * 定时评估系统负载并调整 batchSize。
     *
     * <p>使用 Spring @Scheduled 注解，间隔由 {@code evalIntervalSeconds} 控制。
     */
    @Scheduled(fixedDelayString = "#{${pmis.cronjob.adaptive-batch.eval-interval-seconds:10} * 1000}")
    public void evaluateAndAdjust() {
        try {
            CronjobProperties.AdaptiveBatch config = cronjobProperties.getAdaptiveBatch();
            double cpuUsage = getCpuUsage();
            double memUsage = getMemUsage();
            double poolActive = poolActivePct.get();

            // 计算负载评分（0-1，越高表示负载越重）
            double loadScore = calculateLoadScore(cpuUsage, memUsage, poolActive, config);

            // 根据 loadScore 计算 batchSize
            int newBatchSize = calculateBatchSize(loadScore, config);
            int oldBatchSize = currentBatchSize.getAndSet(newBatchSize);

            if (newBatchSize != oldBatchSize) {
                log.info("[AdaptiveBatch] batchSize 调整: {} -> {} (cpu={}%, mem={}%, pool={}%, loadScore={})",
                        oldBatchSize, newBatchSize,
                        String.format("%.1f", cpuUsage), String.format("%.1f", memUsage),
                        String.format("%.1f", poolActive), String.format("%.3f", loadScore));
            }

            // 更新 Prometheus 指标
            CronjobMetrics metrics = cronjobMetricsProvider.getIfAvailable();
            if (metrics != null) {
                metrics.setAdaptiveBatchSize(newBatchSize);
                metrics.setSystemLoadScore(loadScore);
            }
        } catch (Exception e) {
            log.warn("[AdaptiveBatch] 评估异常, 保持当前 batchSize={}: {}",
                    currentBatchSize.get(), e.getMessage());
        }
    }

    /**
     * 获取当前自适应 batchSize。
     *
     * <p>JobScanner 调用此方法替代直接读取配置值。
     *
     * @return 当前建议的 batchSize
     */
    public int getCurrentBatchSize() {
        return currentBatchSize.get();
    }

    /**
     * 更新线程池活跃度（由 DefaultTaskDispatcher 定期调用）。
     *
     * @param activeThreads 活跃线程数
     * @param maxThreads    最大线程数
     */
    public void updatePoolActive(int activeThreads, int maxThreads) {
        if (maxThreads <= 0) {
            return;
        }
        int pct = (int) Math.min(100.0, (double) activeThreads / maxThreads * 100);
        poolActivePct.set(pct);
    }

    /**
     * 获取 CPU 使用率（百分比，0-100）。
     *
     * <p>使用 {@link com.sun.management.OperatingSystemMXBean#getCpuLoad()}，
     * 返回 -1 时回退为 0。
     */
    private double getCpuUsage() {
        try {
            if (osMXBean instanceof com.sun.management.OperatingSystemMXBean sunOs) {
                double load = sunOs.getCpuLoad();
                return load >= 0 ? load * 100 : 0;
            }
        } catch (Exception ignored) {
            // 降级处理
        }
        return 0;
    }

    /**
     * 获取堆内存使用率（百分比，0-100）。
     */
    private double getMemUsage() {
        try {
            long used = memoryMXBean.getHeapMemoryUsage().getUsed();
            long max = memoryMXBean.getHeapMemoryUsage().getMax();
            if (max <= 0) {
                return 0;
            }
            return (double) used / max * 100;
        } catch (Exception ignored) {
            return 0;
        }
    }

    /**
     * 计算综合负载评分（0-1）。
     *
     * <p>当任一指标超过对应阈值时，该项权重放大；均未超过时，按基线权重计算。
     */
    private double calculateLoadScore(double cpuUsage, double memUsage, double poolActive,
                                       CronjobProperties.AdaptiveBatch config) {
        // 归一化各项指标到 0-1
        double cpuScore = Math.min(1.0, cpuUsage / 100.0);
        double memScore = Math.min(1.0, memUsage / 100.0);
        double poolScore = Math.min(1.0, poolActive / 100.0);

        // 超阈值项加权放大
        double cpuWeight = cpuUsage > config.getCpuThreshold() ? 0.5 : 0.4;
        double memWeight = memUsage > config.getMemThreshold() ? 0.4 : 0.3;
        double poolWeight = poolActive > config.getPoolActiveThreshold() ? 0.4 : 0.3;

        // 归一化权重
        double totalWeight = cpuWeight + memWeight + poolWeight;
        return (cpuScore * cpuWeight + memScore * memWeight + poolScore * poolWeight) / totalWeight;
    }

    /**
     * 根据 loadScore 计算 batchSize。
     *
     * <pre>
     *   batchSize = maxBatchSize - (maxBatchSize - minBatchSize) * loadScore
     * </pre>
     */
    private int calculateBatchSize(double loadScore, CronjobProperties.AdaptiveBatch config) {
        int range = config.getMaxBatchSize() - config.getMinBatchSize();
        int batchSize = (int) Math.round(config.getMaxBatchSize() - range * loadScore);
        return Math.max(config.getMinBatchSize(), Math.min(config.getMaxBatchSize(), batchSize));
    }

    @PreDestroy
    public void shutdown() {
        log.info("[AdaptiveBatch] 关闭, 当前 batchSize={}", currentBatchSize.get());
    }
}
