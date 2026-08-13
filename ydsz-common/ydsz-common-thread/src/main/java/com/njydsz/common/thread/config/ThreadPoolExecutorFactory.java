package com.njydsz.common.thread.config;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskDecorator;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.njydsz.common.thread.config.ThreadPoolProperties.PoolConfig;
import com.njydsz.common.thread.config.ThreadPoolProperties.RejectPolicy;

/**
 * 线程池执行器工厂。
 *
 * <p>供 {@link ThreadPoolRegistrar} 的工厂方法调用，负责创建具体的线程池实例。
 *
 * <p>v1.3.0 重构：从 {@link ThreadPoolAutoConfiguration} 内部类提取为独立组件，
 * 确保 {@code BeanDefinitionRegistryPostProcessor} 可在测试环境中正确运行。
 *
 * @author ydsz-team
 * @since 1.2.0
 */
public class ThreadPoolExecutorFactory {

    private static final Logger log = LoggerFactory.getLogger(ThreadPoolExecutorFactory.class);

    private org.springframework.context.ApplicationContext applicationContext;

    /**
     * 注入 ApplicationContext，供 TaskDecorator 配置使用。
     */
    public void setApplicationContext(@NonNull org.springframework.context.ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * 创建虚拟线程池（JDK 21+）。
     */
    public ExecutorService createVirtualExecutor(String name, PoolConfig config) {
        log.info("ydsz-thread: 创建虚拟线程池 [{}]", name);
        return Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name(config.getThreadNamePrefix(), 0).factory()
        );
    }

    /**
     * 创建平台线程池。
     *
     * <p>v1.3.0 变更：移除显式 {@code setBeanName} 调用（由 BeanDefinition 统一管理），
     * 新增 TaskDecorator 支持。
     */
    public ThreadPoolTaskExecutor createTaskExecutor(String name, PoolConfig config) {
        log.info("ydsz-thread: 创建线程池 [{}] (core={}, max={}, queue={})",
                name, config.getCoreSize(), config.getMaxSize(), config.getQueueCapacity());
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(config.getCoreSize());
        executor.setMaxPoolSize(config.getMaxSize());
        executor.setQueueCapacity(config.getQueueCapacity());
        executor.setThreadNamePrefix(config.getThreadNamePrefix());
        executor.setRejectedExecutionHandler(createRejectHandler(config.getRejectPolicy()));
        executor.setAwaitTerminationSeconds(config.getAwaitTerminationSeconds());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAllowCoreThreadTimeOut(config.isAllowCoreThreadTimeOut());
        executor.setKeepAliveSeconds(config.getKeepAliveSeconds());

        // TaskDecorator 支持：跨线程传播上下文
        applyTaskDecorators(executor, name, config);

        executor.initialize();
        return executor;
    }

    private void applyTaskDecorators(ThreadPoolTaskExecutor executor, String name, PoolConfig config) {
        List<String> decoratorBeanNames = config.getTaskDecoratorBeanNames();
        if (decoratorBeanNames == null || decoratorBeanNames.isEmpty()) {
            return;
        }
        if (applicationContext == null) {
            log.warn("ydsz-thread: ApplicationContext 未注入，无法配置 TaskDecorator (pool={})", name);
            return;
        }

        // 根据 Bean 名称解析 TaskDecorator
        List<TaskDecorator> decorators = decoratorBeanNames.stream()
                .filter(beanName -> {
                    if (!applicationContext.containsBean(beanName)) {
                        log.warn("ydsz-thread: TaskDecorator Bean [{}] 不存在，跳过 (pool={})", beanName, name);
                        return false;
                    }
                    return true;
                })
                .map(beanName -> {
                    Object bean = applicationContext.getBean(beanName);
                    if (bean instanceof TaskDecorator) {
                        return (TaskDecorator) bean;
                    }
                    log.warn("ydsz-thread: Bean [{}] 不是 TaskDecorator 类型，跳过 (pool={})", beanName, name);
                    return null;
                })
                .filter(java.util.Objects::nonNull)
                .toList();

        if (!decorators.isEmpty()) {
            // 应用一个或多个 TaskDecorator 的链式包装
            executor.setTaskDecorator(decorators.size() == 1
                    ? decorators.get(0)
                    : new CompositeTaskDecorator(decorators));
            log.info("ydsz-thread: 已为线程池 [{}] 启用 TaskDecorator: {}", name, decoratorBeanNames);
        }
    }

    private RejectedExecutionHandler createRejectHandler(RejectPolicy policy) {
        if (policy == null) {
            return new ThreadPoolExecutor.CallerRunsPolicy();
        }
        switch (policy) {
            case ABORT:
                return new ThreadPoolExecutor.AbortPolicy();
            case CALLER_RUNS:
                return new ThreadPoolExecutor.CallerRunsPolicy();
            case DISCARD_OLDEST:
                return new ThreadPoolExecutor.DiscardOldestPolicy();
            case DISCARD:
                return new ThreadPoolExecutor.DiscardPolicy();
            default:
                return new ThreadPoolExecutor.CallerRunsPolicy();
        }
    }

    /**
     * 组合式 TaskDecorator：将多个 TaskDecorator 串联执行。
     *
     * <p>只有在用户配置了多个 TaskDecorator Bean 名称时才使用。
     */
    public static class CompositeTaskDecorator implements TaskDecorator {

        private final List<TaskDecorator> decorators;

        public CompositeTaskDecorator(List<TaskDecorator> decorators) {
            this.decorators = decorators;
        }

        @Override
        public Runnable decorate(Runnable runnable) {
            Runnable result = runnable;
            for (TaskDecorator decorator : decorators) {
                result = decorator.decorate(result);
            }
            return result;
        }
    }
}
