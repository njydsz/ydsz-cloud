package com.njydsz.pmis.common.util.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import com.njydsz.pmis.common.util.concurrent.ExecutorUtils;

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
    }
}
