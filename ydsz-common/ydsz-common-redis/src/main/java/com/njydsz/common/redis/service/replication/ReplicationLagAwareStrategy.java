package com.njydsz.common.redis.service.replication;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.annotation.PreDestroy;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.TaskScheduler;

/**
 * Redis 复制延迟感知读写路由策略。
 *
 * <p>在 Redis 主从复制（replication）场景下，后台哨兵线程周期性地向 Redis 写入探测标记并立即读取，
 * 测量主从复制的实际延迟。当延迟超过配置阈值时，{@link #shouldReadFromMaster(String)} 返回 true，
 * 提示调用方路由读操作到主节点，避免读到复制滞后的过期数据。
 *
 * <p><b>核心机制：</b>
 * <ul>
 *   <li>哨兵线程每隔 {@code probeIntervalMs} 毫秒写入一个毫秒级时间戳到 Redis key
 *       （{@code __replication_lag_probe__}），然后立即读取该 key；</li>
 *   <li>如果读到的值与当前时间差超过 {@code maxAcceptableLagMs}，判定为延迟过高；</li>
 *   <li>采用滑动窗口平滑策略（最近 {@code windowSize} 次探测取平均），防止抖动误判。</li>
 * </ul>
 *
 * <p><b>配置项：</b>
 * <pre>{@code
 * ydsz:
 *   redis:
 *     replication:
 *       lag-aware:
 *         enabled: true                    # 启用延迟感知
 *         probe-interval-ms: 1000         # 探测间隔（默认 1000ms）
 *         max-acceptable-lag-ms: 50       # 可接受的最大复制延迟（默认 50ms）
 *         window-size: 5                  # 滑动窗口大小（默认 5 次）
 * }</pre>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * if (lagAwareStrategy.shouldReadFromMaster(cacheKey)) {
 *     // 从主库读取（强制 master 路由）
 *     redisTemplate.execute((RedisCallback<T>) conn -> {
 *         conn.setReadFrom(ReadFrom.UPSTREAM);
 *         return conn.stringCommands().get(key.getBytes());
 *     });
 * }
 * }</pre>
 *
 * <p><b>v1.2.0 变更：</b>新增组件，对标 Redis 大厂实践中的"复制延迟感知读"模式。
 *
 * <p><b>迁移说明：</b>自 v1.1.0 起标记废弃，计划 v2.0.0 移除。
 * 当前无业务消费方。如需强制读主能力，请使用客户端 {@code ReadFrom.UPSTREAM} 配置。
 *
 * @author ydsz-team
 * @since 1.2.0
 * @deprecated 自 v1.1.0 起无消费方，计划 v2.0.0 移除。替代方案：客户端 ReadFrom.UPSTREAM 强制读主。
 */
@Slf4j
@Deprecated(since = "1.1.0", forRemoval = true)
@ConditionalOnClass(StringRedisTemplate.class)
@ConditionalOnProperty(prefix = "ydsz.redis.replication.lag-aware", name = "enabled", havingValue = "true")
public class ReplicationLagAwareStrategy {

    /** 探测 key（位于 Redis 中） */
    private static final String PROBE_KEY = "__replication_lag_probe__";

    /** 探测 key TTL（秒），确保异常退出时自动清理 */
    private static final int PROBE_KEY_TTL_SECONDS = 60;

    private final StringRedisTemplate redisTemplate;

    private final TaskScheduler scheduler;

    private final ReplicationLagProperties properties;

    /** 延迟样本队列（环形缓冲区） */
    private final long[] lagSamples;

    private volatile int sampleIndex = 0;

    private volatile int sampleCount = 0;

    /** 缓存的平均延迟（毫秒），原子可见 */
    private final AtomicLong cachedAvgLagMs = new AtomicLong(0);

    /** 探测任务 future */
    private ScheduledFuture<?> probeFuture;

    /** 哨兵线程运行标记 */
    private volatile boolean running = true;

    /** 单例缓存：同一 ClassLoader 内共享 */
    private static final Map<String, ReplicationLagAwareStrategy> INSTANCES = new ConcurrentHashMap<>();

    /**
     * 默认构造函数（使用默认配置 + 自动注入 StringRedisTemplate）。
     *
     * @param redisTemplate        Redis 模板
     * @param schedulerProvider    调度器提供者（可选）
     */
    public ReplicationLagAwareStrategy(StringRedisTemplate redisTemplate,
                                        ObjectProvider<TaskScheduler> schedulerProvider) {
        this(redisTemplate, schedulerProvider.getIfAvailable(), ReplicationLagProperties.defaults());
    }

    /**
     * 完整构造函数。
     *
     * @param redisTemplate  Redis 模板
     * @param scheduler      调度线程池
     * @param properties     延迟感知配置
     */
    public ReplicationLagAwareStrategy(StringRedisTemplate redisTemplate,
                                        TaskScheduler scheduler,
                                        ReplicationLagProperties properties) {
        this.redisTemplate = redisTemplate;
        this.scheduler = scheduler;
        this.properties = properties != null ? properties : ReplicationLagProperties.defaults();
        this.lagSamples = new long[this.properties.getWindowSize()];
        startProbe();
    }

    /**
     * 启动后台探测线程。
     */
    private void startProbe() {
        if (scheduler == null) {
            log.warn("ReplicationLagAwareStrategy: TaskScheduler 不可用，停止后台哨兵，降级为始终从主节点读");
            cachedAvgLagMs.set(0); // 禁用，始终返回 false（可接受延迟内）
            return;
        }

        long intervalMs = properties.getProbeIntervalMs();
        probeFuture = scheduler.scheduleAtFixedRate(this::probeLag, intervalMs);
        log.info("ReplicationLagAwareStrategy: 哨兵启动成功 (interval={}ms, maxLag={}ms, windowSize={})",
                intervalMs, properties.getMaxAcceptableLagMs(), properties.getWindowSize());
    }

    /**
     * 单次探测：写入时间戳，立刻读回，计算延迟。
     */
    private void probeLag() {
        if (!running) {
            return;
        }
        try {
            long now = System.currentTimeMillis();
            // 写入探测 key
            redisTemplate.opsForValue().set(
                    PROBE_KEY, String.valueOf(now), Duration.ofSeconds(PROBE_KEY_TTL_SECONDS));
            // 读回探测 key（会路由到从库，除非有强制 master 配置）
            String readBack = redisTemplate.opsForValue().get(PROBE_KEY);
            long lag;
            if (readBack == null) {
                // 读不到说明延迟很大（复制还没完成或路由失败）
                lag = Long.MAX_VALUE / 2; // 避免溢出
            } else {
                long writtenTime = Long.parseLong(readBack);
                lag = now - writtenTime;
                if (lag < 0) {
                    lag = 0; // NTP 时钟回拨保护
                }
            }
            recordSample(lag);
        } catch (Exception e) {
            log.warn("ReplicationLagAwareStrategy: 探测失败", e);
            recordSample(Long.MAX_VALUE / 2); // 异常时视为高延迟
        }
    }

    /**
     * 记录延迟样本到环形缓冲区。
     */
    private synchronized void recordSample(long lagMs) {
        lagSamples[sampleIndex] = lagMs;
        sampleIndex = (sampleIndex + 1) % lagSamples.length;
        if (sampleCount < lagSamples.length) {
            sampleCount++;
        }
        // 计算滑动平均
        if (sampleCount > 0) {
            long sum = 0;
            for (int i = 0; i < sampleCount; i++) {
                sum += lagSamples[i];
            }
            cachedAvgLagMs.set(sum / sampleCount);
        }
    }

    /**
     * 判断指定 key 的读操作是否应当走主节点（复制延迟过高时）。
     *
     * <p>判定逻辑：当前滑动窗口平均延迟大于等于 {@code maxAcceptableLagMs} 时返回 true，
     * 调用方应将读操作路由到主节点。若延迟感知未启用或 TaskScheduler 不可用，永远返回 false。
     *
     * @param cacheKey 业务 cache key（当前实现未按 key 区分，保留参数便于将来实现 hash tag 隔离）
     * @return true 表示延迟过高、应从主节点读取；false 表示延迟可接受、可从从节点读取
     */
    public boolean shouldReadFromMaster(String cacheKey) {
        return cachedAvgLagMs.get() >= properties.getMaxAcceptableLagMs();
    }

    /**
     * 获取当前平均复制延迟（毫秒）。
     *
     * @return 滑动窗口平均延迟
     */
    public long getCurrentAvgLagMs() {
        return cachedAvgLagMs.get();
    }

    /**
     * 获取最近 N 次原始探测样本（最近在前）。
     *
     * @return 延迟样本数组（毫秒），长度 <= windowSize
     */
    public synchronized long[] getRecentSamples() {
        long[] result = new long[sampleCount];
        for (int i = 0; i < sampleCount; i++) {
            int idx = (sampleIndex - sampleCount + i + lagSamples.length) % lagSamples.length;
            result[i] = lagSamples[idx];
        }
        return result;
    }

    /**
     * 优雅停止后台哨兵任务。
     */
    @PreDestroy
    public void shutdown() {
        running = false;
        if (probeFuture != null) {
            probeFuture.cancel(false);
        }
        // 清理探测 key
        try {
            redisTemplate.delete(PROBE_KEY);
        } catch (Exception ignored) {
            // 忽略清理失败
        }
        INSTANCES.values().remove(this);
        log.info("ReplicationLagAwareStrategy: 哨兵已停止");
    }

    /**
     * 延迟感知配置属性。
     */
    @Data
    public static class ReplicationLagProperties {
        /** 探测间隔（毫秒），默认 1000 */
        private long probeIntervalMs = 1000;
        /** 可接受的最大复制延迟（毫秒），默认 50 */
        private long maxAcceptableLagMs = 50;
        /** 滑动窗口大小，默认 5 */
        private int windowSize = 5;

        public static ReplicationLagProperties defaults() {
            return new ReplicationLagProperties();
        }
    }
}
