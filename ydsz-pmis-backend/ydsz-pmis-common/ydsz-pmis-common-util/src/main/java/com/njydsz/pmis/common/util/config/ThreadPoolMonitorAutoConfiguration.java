package com.njydsz.pmis.common.util.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

import lombok.extern.slf4j.Slf4j;

/**
 * 线程池监控自动配置
 *
 * <p>当 Micrometer 可用时，自动注册线程池监控指标。
 * 当 Micrometer 不可用时，降级为日志输出。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@AutoConfiguration
public class ThreadPoolMonitorAutoConfiguration {

    /**
     * 线程池监控注册器
     *
     * <p>提供线程池指标采集能力，包括：
     * <ul>
     *   <li>活跃线程数</li>
     *   <li>队列大小</li>
     *   <li>已完成任务数</li>
     *   <li>线程池大小</li>
     * </ul>
     *
     * @return 线程池监控注册器
     */
    @Bean
    @ConditionalOnMissingBean
    public ThreadPoolMonitor threadPoolMonitor() {
        return new ThreadPoolMonitor();
    }

    /**
     * 线程池监控器
     *
     * <p>支持注册多个命名线程池实例，并提供统一的指标采集接口。
     * 当 Micrometer MeterRegistry 可用时，可通过 {@link #registerWithMeterRegistry} 注册指标。
     */
    public static class ThreadPoolMonitor {

        private final Map<String, ThreadPoolExecutor> registeredPools = new ConcurrentHashMap<>();
        private volatile boolean micrometerRegistered = false;

        /**
         * 注册线程池实例
         *
         * @param name     线程池名称
         * @param executor 线程池实例
         */
        public void register(String name, ThreadPoolExecutor executor) {
            registeredPools.put(name, executor);
            log.info("Thread pool registered for monitoring: {}", name);
        }

        /**
         * 将所有已注册线程池的指标注册到 Micrometer MeterRegistry
         *
         * <p>注册以下 Gauge 指标（前缀 pmis.threadpool）：
         * <ul>
         *   <li>pool.size - 当前线程池大小</li>
         *   <li>pool.active - 活跃线程数</li>
         *   <li>pool.completed - 已完成任务数</li>
         *   <li>pool.queue.size - 队列大小</li>
         *   <li>pool.queue.capacity - 队列剩余容量</li>
         * </ul>
         *
         * @param registry Micrometer MeterRegistry
         */
        public void registerWithMeterRegistry(MeterRegistry registry) {
            if (registry == null || micrometerRegistered) {
                return;
            }
            for (Map.Entry<String, ThreadPoolExecutor> entry : registeredPools.entrySet()) {
                String poolName = entry.getKey();
                ThreadPoolExecutor executor = entry.getValue();
                Tags tags = Tags.of("pool", poolName);

                Gauge.builder("pmis.threadpool.pool.size", executor, ThreadPoolExecutor::getPoolSize)
                        .tags(tags).register(registry);
                Gauge.builder("pmis.threadpool.pool.active", executor, ThreadPoolExecutor::getActiveCount)
                        .tags(tags).register(registry);
                Gauge.builder("pmis.threadpool.pool.completed", executor, ThreadPoolExecutor::getCompletedTaskCount)
                        .tags(tags).register(registry);
                Gauge.builder("pmis.threadpool.pool.queue.size", executor, e -> e.getQueue().size())
                        .tags(tags).register(registry);
                Gauge.builder("pmis.threadpool.pool.queue.capacity", executor, e -> e.getQueue().remainingCapacity())
                        .tags(tags).register(registry);
                Gauge.builder("pmis.threadpool.pool.largest.size", executor, ThreadPoolExecutor::getLargestPoolSize)
                        .tags(tags).register(registry);
            }
            micrometerRegistered = true;
            log.info("Thread pool metrics registered with Micrometer: {} pools", registeredPools.size());
        }

        /**
         * 获取所有已注册线程池的状态快照
         *
         * @return Map&lt;poolName, statusMap&gt;
         */
        public Map<String, Map<String, Number>> getPoolStatuses() {
            Map<String, Map<String, Number>> statuses = new LinkedHashMap<>();
            for (Map.Entry<String, ThreadPoolExecutor> entry : registeredPools.entrySet()) {
                ThreadPoolExecutor executor = entry.getValue();
                Map<String, Number> status = new LinkedHashMap<>();
                status.put("poolSize", executor.getPoolSize());
                status.put("activeCount", executor.getActiveCount());
                status.put("completedTaskCount", executor.getCompletedTaskCount());
                status.put("taskCount", executor.getTaskCount());
                status.put("queueSize", executor.getQueue().size());
                status.put("queueRemainingCapacity", executor.getQueue().remainingCapacity());
                status.put("largestPoolSize", executor.getLargestPoolSize());
                status.put("maximumPoolSize", executor.getMaximumPoolSize());
                status.put("corePoolSize", executor.getCorePoolSize());
                status.put("isShutdown", executor.isShutdown() ? 1 : 0);
                status.put("isTerminated", executor.isTerminated() ? 1 : 0);
                statuses.put(entry.getKey(), status);
            }
            return statuses;
        }

        /**
         * 注销线程池实例
         *
         * @param name 线程池名称
         */
        public void unregister(String name) {
            registeredPools.remove(name);
        }

        /**
         * 获取已注册线程池数量
         *
         * @return 线程池数量
         */
        public int getRegisteredPoolCount() {
            return registeredPools.size();
        }
    }
}
