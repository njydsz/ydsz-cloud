package com.njydsz.common.audit.config;

import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

import lombok.RequiredArgsConstructor;

/**
 * 审计模块异步配置
 *
 * <p>为审计事件监听器（{@code @Async("auditAsyncExecutor")}）和异步记录器提供异步执行支持。
 *
 * <p>设计说明：
 * <ul>
 *   <li>使用 {@link EnableAsync} 注解驱动异步执行框架</li>
 *   <li>通过 {@link AsyncConfigurer} 覆盖默认的 {@code SimpleAsyncTaskExecutor}，
 *       强制使用审计专用线程池 {@code auditAsyncExecutor}，避免线程无限制创建</li>
 *   <li>本配置类在审计模块启用时自动加载，业务方无需额外配置</li>
 * </ul>
 *
 * <h3>与主业务线程池的隔离</h3>
 * <p>审计异步线程池独立于业务线程池（如 ydsz-rpc-pool、ydsz-task-pool），
 * 避免审计写入 IO 阻塞影响核心业务链路。
 *
 * @author ydsz-team
 * @since 1.2.0
 */
@AutoConfiguration(after = AuditAutoConfiguration.class)
@ConditionalOnProperty(prefix = "ydsz.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableAsync
@RequiredArgsConstructor
public class AuditAsyncConfiguration implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AuditAsyncConfiguration.class);

    private final Executor auditAsyncExecutor;

    /**
     * 将审计专用线程池设为 @Async 默认执行器
     *
     * <p>当业务方未显式指定线程池名称时（即使用 @Async 不带参数），
     * 使用此线程池而非 Spring 默认的 SimpleAsyncTaskExecutor。
     *
     * @return 默认异步任务执行器
     */
    @Override
    public Executor getAsyncExecutor() {
        log.debug("[AuditAsync] @Async 默认执行器配置完成 → auditAsyncExecutor");
        return auditAsyncExecutor;
    }

    /**
     * 配置异步任务的异常处理器
     *
     * <p>异步审计任务异常仅记录日志，不回溯调用处，避免影响业务主链路。
     *
     * @return 异步异常处理器
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new SimpleAsyncUncaughtExceptionHandler();
    }
}
