package com.njydsz.pmis.common.core.concurrent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * 线程池统一注册中心与监控
 *
 * <p>集中管理应用中所有创建的线程池，提供：
 * <ul>
 *   <li>统一注册与命名（便于排查线程泄漏）</li>
 *   <li>Micrometer 指标采集（活跃线程数、队列大小、已完成任务数等）</li>
 *   <li>应用关闭时优雅停机（{@code @PreDestroy}）</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * @Service
 * public class MyService {
 *     private final ExecutorService executor;
 *
 *     public MyService(ThreadPoolRegistry registry) {
 *         this.executor = registry.createFixed("biz-order", 4, 8, 100);
 *     }
 * }
 * }</pre>
 *
 * @author Marvin Lee
 * @since 3.5.0
 */
public class ThreadPoolRegistry implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(ThreadPoolRegistry.class);

    private final Map<String, ExecutorService> registeredPools = new ConcurrentHashMap<>();
    private final MeterRegistry meterRegistry;

    public ThreadPoolRegistry(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * 注册线程池并绑定监控指标
     *
     * @param name     线程池名称（唯一标识）
     * @param executor 线程池实例
     */
    public void register(String name, ExecutorService executor) {
        ExecutorService existing = registeredPools.putIfAbsent(name, executor);
        if (existing != null) {
            log.warn("Thread pool already registered with name: {}, skipping", name);
            return;
        }
        log.info("Registered thread pool: {}", name);

        if (executor instanceof ThreadPoolExecutor tpe && meterRegistry != null) {
            bindMetrics(name, tpe);
        }
    }

    /**
     * 获取已注册的线程池
     */
    public ExecutorService get(String name) {
        return registeredPools.get(name);
    }

    /**
     * 获取所有已注册的线程池名称
     */
    public java.util.Set<String> getPoolNames() {
        return java.util.Collections.unmodifiableSet(registeredPools.keySet());
    }

    /**
     * 获取线程池状态快照
     */
    public Map<String, ThreadPoolStats> getStats() {
        Map<String, ThreadPoolStats> stats = new java.util.HashMap<>();
        registeredPools.forEach((name, executor) -> {
            if (executor instanceof ThreadPoolExecutor tpe) {
                stats.put(name, ThreadPoolStats.from(tpe));
            }
        });
        return stats;
    }

    private void bindMetrics(String name, ThreadPoolExecutor tpe) {
        String prefix = "pmis.threadpool." + name;
        Gauge.builder(prefix + ".active", tpe, ThreadPoolExecutor::getActiveCount)
                .description("Active thread count")
                .register(meterRegistry);
        Gauge.builder(prefix + ".queue.size", tpe, e -> e.getQueue().size())
                .description("Queue size")
                .register(meterRegistry);
        Gauge.builder(prefix + ".pool.size", tpe, ThreadPoolExecutor::getPoolSize)
                .description("Current pool size")
                .register(meterRegistry);
        Gauge.builder(prefix + ".core.pool.size", tpe, ThreadPoolExecutor::getCorePoolSize)
                .description("Core pool size")
                .register(meterRegistry);
        Gauge.builder(prefix + ".max.pool.size", tpe, ThreadPoolExecutor::getMaximumPoolSize)
                .description("Maximum pool size")
                .register(meterRegistry);
        Gauge.builder(prefix + ".completed.tasks", tpe, ThreadPoolExecutor::getCompletedTaskCount)
                .description("Completed task count")
                .register(meterRegistry);
        Gauge.builder(prefix + ".largest.pool.size", tpe, ThreadPoolExecutor::getLargestPoolSize)
                .description("Largest pool size ever reached")
                .register(meterRegistry);
    }

    @Override
    public void destroy() {
        log.info("Shutting down {} registered thread pools", registeredPools.size());
        registeredPools.forEach((name, executor) -> {
            log.info("Shutting down thread pool: {}", name);
            executor.shutdown();
            try {
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    log.warn("Thread pool {} did not terminate gracefully, forcing shutdown", name);
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        });
        registeredPools.clear();
    }

    /**
     * 线程池状态快照
     */
    public record ThreadPoolStats(
            int activeCount,
            int poolSize,
            int corePoolSize,
            int maximumPoolSize,
            int queueSize,
            long completedTaskCount,
            long largestPoolSize
    ) {
        public static ThreadPoolStats from(ThreadPoolExecutor tpe) {
            return new ThreadPoolStats(
                    tpe.getActiveCount(),
                    tpe.getPoolSize(),
                    tpe.getCorePoolSize(),
                    tpe.getMaximumPoolSize(),
                    tpe.getQueue().size(),
                    tpe.getCompletedTaskCount(),
                    tpe.getLargestPoolSize()
            );
        }
    }
}
