package com.njydsz.pmis.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务自动配置
 *
 * <p>提供自定义 {@link ThreadPoolTaskExecutor} 替代 Spring Boot 默认配置，
 * 解决默认线程池队列无上限 OOM 风险、线程名无前缀排障困难、无拒绝策略等问题。
 *
 * <p>同时实现 {@link AsyncConfigurer} 提供 {@link AsyncUncaughtExceptionHandler}，
 * 防止 {@code @Async void} 方法异常被 {@code SimpleAsyncUncaughtExceptionHandler} 静默吞掉。
 *
 * <p>覆盖 9 处 {@code @Async} 方法：OperationLogListener、SensitiveOperationListener、
 * LoginAuditListener、DataExportAuditListener、AgentServiceImpl、
 * ProjectInitiationFlowListener、BudgetAlertEventListener、ProjectChangeExecutedEventListener、
 * DataExportAuditAspect。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncAutoConfiguration implements AsyncConfigurer {

    /**
     * 自定义异步线程池
     *
     * <p>配置策略：
     * <ul>
     *   <li>核心线程数 8：日常审计/事件监听并发量</li>
     *   <li>最大线程数 32：突发流量扩容上限</li>
     *   <li>队列容量 500：防止无界队列 OOM</li>
     *   <li>拒绝策略 CallerRunsPolicy：队列满时由调用线程执行，实现反压</li>
     *   <li>线程名前缀 pmis-async-：便于线程 dump 排障</li>
     *   <li>优雅停机：等待 30s 处理完在途任务</li>
     * </ul>
     *
     * <p>Bean 名 applicationTaskExecutor 覆盖 Spring Boot 默认，
     * 使 {@code @Async}（未指定 executor 时）和 {@code ApplicationListener} 等均使用此线程池。
     *
     * @param mdcTaskDecorator MDC 上下文传递装饰器（在 CommonAutoConfiguration 中定义）
     * @return ThreadPoolTaskExecutor 实例
     */
    @Bean(name = "applicationTaskExecutor", destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "applicationTaskExecutor")
    public ThreadPoolTaskExecutor applicationTaskExecutor(TaskDecorator mdcTaskDecorator) {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(8);
        exec.setMaxPoolSize(32);
        exec.setQueueCapacity(500);
        exec.setKeepAliveSeconds(60);
        exec.setThreadNamePrefix("pmis-async-");
        exec.setWaitForTasksToCompleteOnShutdown(true);
        exec.setAwaitTerminationSeconds(30);
        exec.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        exec.setTaskDecorator(mdcTaskDecorator);
        exec.initialize();
        return exec;
    }

    /**
     * 返回 null 让 Spring 使用名为 applicationTaskExecutor 的默认 {@link Executor} Bean
     *
     * @return null（使用默认 Bean）
     */
    @Override
    public Executor getAsyncExecutor() {
        return null;
    }

    /**
     * 异步未捕获异常处理器
     *
     * <p>{@code @Async void} 方法抛出的异常不会被调用方感知，
     * 默认仅由 {@code SimpleAsyncUncaughtExceptionHandler} 打印一行 error 日志。
     * 此处统一记录方法名、参数、完整堆栈，便于排障。
     *
     * @return 异常处理器
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> {
            log.error("[Async] 未捕获异常 method={} params={}",
                    method.getDeclaringClass().getSimpleName() + "." + method.getName(),
                    params != null ? params.length : 0, ex);
        };
    }
}
