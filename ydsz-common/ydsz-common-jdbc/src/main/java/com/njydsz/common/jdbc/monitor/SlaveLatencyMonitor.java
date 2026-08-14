package com.njydsz.common.jdbc.monitor;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import com.njydsz.common.jdbc.config.ReadWriteSplittingProperties.LatencyCheck;

import lombok.extern.slf4j.Slf4j;

/**
 * 从库延迟监控服务
 *
 * <p>后台周期性检测各从库复制延迟，维护延迟超标从库的摘除/恢复状态。
 *
 * <p>设计要点：
 * <ul>
 *   <li>基于 ScheduledExecutorService 周期性执行检测</li>
 *   <li>使用 ConcurrentHashMap 维护各从库的连续超标计数</li>
 *   <li>连续 N 次超标才触发摘除（防抖动）</li>
 *   <li>被摘除的从库进入恢复检测阶段，延迟恢复正常后重新加入路由池</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class SlaveLatencyMonitor {

    private final SlaveLatencyDetector detector;
    private final LatencyCheck config;
    private final ScheduledExecutorService scheduler;
    private final Map<String, DataSource> slaveDataSources;
    private final Duration latencyThreshold;
    private final int failureThreshold;

    /** 各从库连续超标计数 */
    private final Map<String, Integer> failureCounts = new ConcurrentHashMap<>();
    /** 当前被摘除的从库集合（延迟超标） */
    private final Set<String> excludedSlaves = ConcurrentHashMap.newKeySet();

    /** 缓存的健康从库快照 — 每次检测周期更新，读操作无锁零分配 */
    private volatile Set<String> healthySlavesCache;

    public SlaveLatencyMonitor(SlaveLatencyDetector detector,
                                Map<String, DataSource> slaveDataSources,
                                LatencyCheck config) {
        this.detector = detector;
        this.slaveDataSources = new ConcurrentHashMap<>(slaveDataSources);
        this.config = config;
        this.latencyThreshold = config.getThreshold();
        this.failureThreshold = config.getFailureThreshold();
        this.scheduler = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "slave-latency-monitor");
            t.setDaemon(true);
            return t;
        }, new ThreadPoolExecutor.CallerRunsPolicy());
        // 初始状态：假设所有从库均健康，检测周期会修正
        this.healthySlavesCache = Set.copyOf(slaveDataSources.keySet());
    }

    /**
     * 启动延迟监控
     */
    public void start() {
        if (!config.isEnabled() || slaveDataSources.isEmpty()) {
            return;
        }
        scheduler.scheduleWithFixedDelay(this::checkAllSlaves,
                config.getInterval().toMillis(),
                config.getInterval().toMillis(),
                TimeUnit.MILLISECONDS);
        log.info("SlaveLatencyMonitor started: interval={}, threshold={}",
                config.getInterval(), latencyThreshold);
    }

    /**
     * 停止延迟监控
     */
    public void stop() {
        scheduler.shutdownNow();
    }

    /**
     * 判断从库是否健康（未被摘除）
     *
     * <p>读取由检测周期维护的缓存快照，无锁无分配，适合高频路由决策调用。
     *
     * @param slaveName 从库名称
     * @return true 表示健康，可以路由
     */
    public boolean isHealthy(String slaveName) {
        Set<String> cache = healthySlavesCache;
        return cache != null && cache.contains(slaveName);
    }

    /**
     * 获取当前可用的从库名称列表（排除延迟超标的）
     *
     * <p>返回不可变缓存快照，调用方无需额外同步。
     * 每次检测周期（{@link LatencyCheck#getInterval()}）更新一次，
     * 读操作无锁无分配，适合高频路由决策场景。
     *
     * @return 健康从库名称集合（不可变）
     */
    public Set<String> getHealthySlaves() {
        return healthySlavesCache;
    }

    /**
     * 检测所有从库的延迟，并在完成后更新健康缓存快照
     */
    private void checkAllSlaves() {
        slaveDataSources.forEach(this::checkSingleSlave);
        // 一次性更新缓存快照，保证路由决策看到一致的健康状态
        healthySlavesCache = Set.copyOf(
                slaveDataSources.keySet().stream()
                        .filter(s -> !excludedSlaves.contains(s))
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()));
    }

    /**
     * 检测单个从库的延迟并更新状态
     */
    private void checkSingleSlave(String slaveName, DataSource dataSource) {
        try {
            if (!detector.isSupported(dataSource)) {
                return;
            }

            Optional<Duration> latency = detector.detect(dataSource);

            if (latency.isEmpty()) {
                // 无法检测延迟（可能复制停止），保持当前状态
                return;
            }

            Duration currentLatency = latency.get();
            if (currentLatency.compareTo(latencyThreshold) > 0) {
                // 延迟超标
                int count = failureCounts.merge(slaveName, 1, Integer::sum);
                log.warn("从库 {} 延迟超标: {}s, 连续次数: {}/{}",
                        slaveName, currentLatency.getSeconds(), count, failureThreshold);
                if (count >= failureThreshold && !excludedSlaves.contains(slaveName)) {
                    excludedSlaves.add(slaveName);
                    log.warn("从库 {} 因延迟超标被摘除（连续 {} 次超过阈值 {}s）",
                            slaveName, count, latencyThreshold.getSeconds());
                }
            } else {
                // 延迟正常，重置计数
                failureCounts.remove(slaveName);
                if (excludedSlaves.remove(slaveName)) {
                    log.info("从库 {} 延迟恢复正常（{}s），已重新加入路由池",
                            slaveName, currentLatency.getSeconds());
                }
            }
        } catch (Exception e) {
            log.warn("从库 {} 延迟检测异常: {}", slaveName, e.getMessage());
            // 检测异常视为不健康，但不立即摘除（由连续阈值控制）
        }
    }

    /**
     * 运行时动态注册从库
     *
     * <p>新注册从库默认视为健康，下个检测周期会根据实际延迟修正状态。
     *
     * @param slaveName   从库名称
     * @param dataSource 数据源
     */
    public void registerSlave(String slaveName, DataSource dataSource) {
        slaveDataSources.put(slaveName, dataSource);
        // 新从库默认加入健康池，下个检测周期会验证
        Set<String> updated = new java.util.HashSet<>(healthySlavesCache);
        updated.add(slaveName);
        healthySlavesCache = Set.copyOf(updated);
    }

    /**
     * 运行时动态移除从库
     *
     * @param slaveName 从库名称
     */
    public void removeSlave(String slaveName) {
        slaveDataSources.remove(slaveName);
        excludedSlaves.remove(slaveName);
        failureCounts.remove(slaveName);
        // 从健康缓存中移除
        Set<String> updated = new java.util.HashSet<>(healthySlavesCache);
        updated.remove(slaveName);
        healthySlavesCache = Set.copyOf(updated);
    }
}
