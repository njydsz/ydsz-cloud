package com.njydsz.pmis.common.config;

import com.njydsz.pmis.common.constant.AsyncExecutorNames;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步线程池定制配置
 *
 * <p>按业务用途拆分三个独立线程池，替代 Spring Boot 默认的
 * {@code applicationTaskExecutor}（核心 8 / 队列 Integer.MAX_VALUE），
 * 避免高并发审计/导出场景下队列无限堆积导致 OOM。
 *
 * <h3>线程池分工</h3>
 * <table>
 *   <tr><th>Bean 名称</th><th>核心/最大</th><th>队列</th><th>拒绝策略</th><th>用途</th></tr>
 *   <tr><td>{@link AsyncExecutorNames#AUDIT}</td><td>2 / 4</td><td>500</td><td>CallerRunsPolicy</td><td>审计日志（不能丢，降级同步）</td></tr>
 *   <tr><td>{@link AsyncExecutorNames#EXPORT}</td><td>1 / 2</td><td>10</td><td>AbortPolicy</td><td>数据导出（CPU 密集，快速拒绝）</td></tr>
 *   <tr><td>{@link AsyncExecutorNames#AGENT}</td><td>2 / 8</td><td>100</td><td>CallerRunsPolicy</td><td>AI Agent 调用（IO 密集，高并发）</td></tr>
 * </table>
 *
 * <p>每个线程池均注入 {@link TaskDecorator}（由 {@link CommonAutoConfiguration#mdcTaskDecorator()}
 * 注册），确保异步线程继承主线程的 MDC 上下文（traceId 等）。
 *
 * <p>所有线程池均开启 {@code waitForTasksToCompleteOnShutdown}，确保应用关闭时
 * 已提交的任务能执行完毕，避免审计日志/导出数据丢失。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@ConditionalOnClass(ThreadPoolTaskExecutor.class)
public class AsyncThreadPoolConfig {

    /** MDC 任务装饰器（可选注入，缺失时不影响线程池创建） */
    private final ObjectProvider<TaskDecorator> taskDecoratorProvider;

    public AsyncThreadPoolConfig(ObjectProvider<TaskDecorator> taskDecoratorProvider) {
        this.taskDecoratorProvider = taskDecoratorProvider;
    }

    /**
     * 审计日志线程池
     *
     * <p>核心 2 / 最大 4 / 队列 500，CallerRunsPolicy 拒绝策略：
     * 审计日志不能丢失，队列满时降级为同步执行（由调用线程直接运行）。
     * 优雅关闭等待 30 秒。
     *
     * @return 审计日志线程池
     */
    @Bean(name = AsyncExecutorNames.AUDIT)
    public ThreadPoolTaskExecutor auditExecutor() {
        ThreadPoolTaskExecutor executor = buildExecutor(
                2, 4, 500, "audit-async-",
                new ThreadPoolExecutor.CallerRunsPolicy(), 30);
        log.info("[AsyncThreadPool] 审计日志线程池已创建: core=2 max=4 queue=500 policy=CallerRunsPolicy");
        return executor;
    }

    /**
     * 数据导出线程池
     *
     * <p>核心 1 / 最大 2 / 队列 10，AbortPolicy 拒绝策略：
     * 导出任务 CPU 密集且耗时长，小队列快速拒绝，前端提示"导出任务繁忙"。
     * 优雅关闭等待 120 秒（导出任务耗时长，需更长等待）。
     *
     * @return 数据导出线程池
     */
    @Bean(name = AsyncExecutorNames.EXPORT)
    public ThreadPoolTaskExecutor exportExecutor() {
        ThreadPoolTaskExecutor executor = buildExecutor(
                1, 2, 10, "export-async-",
                new ThreadPoolExecutor.AbortPolicy(), 120);
        log.info("[AsyncThreadPool] 数据导出线程池已创建: core=1 max=2 queue=10 policy=AbortPolicy");
        return executor;
    }

    /**
     * AI Agent 调用线程池
     *
     * <p>核心 2 / 最大 8 / 队列 100，CallerRunsPolicy 拒绝策略：
     * AI 调用为 IO 密集型，支持高并发；队列满时降级为同步执行。
     * 优雅关闭等待 60 秒。
     *
     * @return AI Agent 调用线程池
     */
    @Bean(name = AsyncExecutorNames.AGENT)
    public ThreadPoolTaskExecutor agentExecutor() {
        ThreadPoolTaskExecutor executor = buildExecutor(
                2, 8, 100, "agent-async-",
                new ThreadPoolExecutor.CallerRunsPolicy(), 60);
        log.info("[AsyncThreadPool] AI Agent 线程池已创建: core=2 max=8 queue=100 policy=CallerRunsPolicy");
        return executor;
    }

    /**
     * 统一构建线程池并应用公共配置（MDC 装饰器、优雅关闭）。
     *
     * @param corePoolSize       核心线程数
     * @param maxPoolSize        最大线程数
     * @param queueCapacity      队列容量
     * @param threadNamePrefix   线程名前缀
     * @param rejectionPolicy    拒绝策略
     * @param awaitTerminationSeconds 优雅关闭等待秒数
     * @return 已初始化的线程池
     */
    private ThreadPoolTaskExecutor buildExecutor(int corePoolSize, int maxPoolSize,
                                                  int queueCapacity, String threadNamePrefix,
                                                  RejectedExecutionHandler rejectionPolicy,
                                                  int awaitTerminationSeconds) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setRejectedExecutionHandler(rejectionPolicy);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(awaitTerminationSeconds);
        // 注入 MDC 任务装饰器，继承主线程 traceId 等上下文
        TaskDecorator decorator = taskDecoratorProvider.getIfAvailable();
        if (decorator != null) {
            executor.setTaskDecorator(decorator);
        }
        executor.initialize();
        return executor;
    }

    /**
     * 启动后打印线程池配置摘要，便于排查问题。
     */
    @PostConstruct
    public void logConfigSummary() {
        log.info("[AsyncThreadPool] 配置就绪: auditExecutor(2/4/500) exportExecutor(1/2/10) agentExecutor(2/8/100)");
    }

    /**
     * 关闭前打印日志，线程池将由 Spring 容器触发优雅关闭。
     */
    @PreDestroy
    public void logShutdown() {
        log.info("[AsyncThreadPool] 应用关闭中，线程池开始优雅等待已提交任务完成");
    }
}
