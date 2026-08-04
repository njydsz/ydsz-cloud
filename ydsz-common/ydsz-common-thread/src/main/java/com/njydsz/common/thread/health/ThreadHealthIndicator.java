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

    /**
     * 注入应用上下文，供 {@link #health()} 运行时按类型检索线程池 Bean。
     *
     * <p>采用运行时检索而非构造注入，是为了同时覆盖
     * {@code ThreadPoolAutoConfiguration} 动态注册的线程池单例。
     *
     * @param applicationContext Spring 应用上下文，由容器回调注入
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * 汇总全部线程池的运行时状态作为健康检查结果。
     *
     * <p>明细以 {@code <beanName>.<指标>} 为键输出 active / queueSize / poolSize /
     * completed / threadNamePrefix 五项。
     *
     * <p><b>状态判定</b>：
     * <ul>
     *   <li>上下文未就绪或容器内无线程池 —— UP（视为"无需检查"，不阻塞应用启动）</li>
     *   <li>任一线程池取底层 {@link ThreadPoolExecutor} 抛异常 —— DOWN，
     *       并在 {@code <beanName>.error} 中记录异常信息</li>
     *   <li>其余情况 —— UP</li>
     * </ul>
     *
     * <p><b>注意</b>：单个线程池失败不会中断遍历，其余线程池指标仍会完整采集；
     * 本方法只读取状态，无副作用。
     *
     * @return 健康检查结果，始终非 {@code null}
     */
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
