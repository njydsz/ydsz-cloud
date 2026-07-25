package com.njydsz.common.thread.health;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 线程池健康检查指标。
 *
 * <p>运行时从 {@link ApplicationContext} 获取所有 {@link ThreadPoolTaskExecutor} Bean，
 * 报告各线程池的 active/queueSize/completed/poolSize 状态。
 *
 * <p>当任何线程池无法获取底层 {@link ThreadPoolExecutor} 时，健康状态为 DOWN。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class ThreadHealthIndicator implements HealthIndicator, ApplicationContextAware {

    private static final Logger log = LoggerFactory.getLogger(ThreadHealthIndicator.class);

    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public Health health() {
        Map<String, Object> details = new LinkedHashMap<>();
        boolean anyDown = false;

        if (applicationContext == null) {
            return Health.up().withDetail("status", "ApplicationContext not initialized").build();
        }

        Map<String, ThreadPoolTaskExecutor> executors =
            applicationContext.getBeansOfType(ThreadPoolTaskExecutor.class);

        if (executors.isEmpty()) {
            return Health.up().withDetail("pools", "none").build();
        }

        for (Map.Entry<String, ThreadPoolTaskExecutor> entry : executors.entrySet()) {
            String beanName = entry.getKey();
            ThreadPoolTaskExecutor executor = entry.getValue();
            try {
                ThreadPoolExecutor pool = executor.getThreadPoolExecutor();
                details.put(beanName + ".active", pool.getActiveCount());
                details.put(beanName + ".queueSize", pool.getQueue().size());
                details.put(beanName + ".poolSize", pool.getPoolSize());
                details.put(beanName + ".completed", pool.getCompletedTaskCount());
                details.put(beanName + ".threadNamePrefix", executor.getThreadNamePrefix());
            } catch (Exception e) {
                log.warn("线程池 [{}] 健康检查失败", beanName, e);
                details.put(beanName + ".error", e.getMessage());
                anyDown = true;
            }
        }

        if (anyDown) {
            return Health.down().withDetails(details).build();
        }
        return Health.up().withDetails(details).build();
    }
}
