package com.njydsz.common.thread.config;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

import jakarta.validation.constraints.Min;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;
import org.springframework.core.Ordered;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.njydsz.common.thread.config.ThreadPoolProperties.PoolConfig;
import com.njydsz.common.thread.config.ThreadPoolProperties.PoolType;
import com.njydsz.common.thread.health.ThreadHealthIndicator;
import com.njydsz.common.thread.metrics.MeteredRejectedHandler;
import com.njydsz.common.thread.metrics.ThreadPoolMetrics;
import com.njydsz.common.thread.metrics.VirtualThreadMetrics;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 统一线程池自动配置。
 *
 * <p>根据 {@link ThreadPoolProperties#getPools()} 配置动态创建并注册多个
 * {@link ThreadPoolTaskExecutor} / {@link ExecutorService} Bean，
 * Bean 名称为 {@code key + "Executor"}。
 *
 * <p>功能特性：
 * <ul>
 *   <li>按业务隔离：每个线程池独立的 coreSize/maxSize/queue/rejectPolicy</li>
 *   <li>Micrometer 指标：active/queueSize/completed/rejected Gauge + Counter，
 *       前缀 {@code ydzz.executor}，自动注册 {@link ThreadPoolMetrics} /
 *       {@link VirtualThreadMetrics} Bean</li>
 *   <li>优雅关闭：shutdown 时等待任务完成</li>
 *   <li>健康检查：自动注册 {@link ThreadHealthIndicator}</li>
 *   <li>TaskDecorator 支持：通过 {@code task-decorator-bean-names} 配置上下文传播</li>
 * </ul>
 *
 * <p>注入方式：
 * <pre>{@code
 * @Resource(name = "ioExecutor")
 * private ThreadPoolTaskExecutor ioExecutor;
 * }</pre>
 *
 * <p><b>v1.3.0 变更：</b>
 * <ul>
 *   <li>新增 {@link ThreadPoolMetrics} / {@link VirtualThreadMetrics} 自动注册</li>
 *   <li>新增 {@link MeteredRejectedHandler} 自动包装拒绝策略</li>
 *   <li>新增 TaskDecorator 配置支持</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see ThreadPoolProperties
 * @see ThreadHealthIndicator
 */
@AutoConfiguration
@EnableConfigurationProperties(ThreadPoolProperties.class)
@ConditionalOnProperty(prefix = "ydsz.thread", name = "enabled", matchIfMissing = true)
public class ThreadPoolAutoConfiguration implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(ThreadPoolAutoConfiguration.class);

    /**
     * 延迟注入 ApplicationContext，支持 {@code ApplicationContextRunner} 测试场景。
     * <p>使用字段注入而非构造器注入，避免测试时缺少默认构造器的问题。
     */
    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    @Override
    public void afterSingletonsInstantiated() {
        if (applicationContext != null) {
            log.info("[ydsz-thread] 自动配置完成，已管理平台线程池: {}",
                    applicationContext.getBeansOfType(ThreadPoolTaskExecutor.class).keySet());
        }
    }

    /**
     * 获取全部已注册的平台线程池（Bean 名称 → 线程池）。
     *
     * <p>供下游模块（如消息通道 Bulkhead 隔离）按名称查找线程池并组装为业务 Map。
     * 虚拟线程池（{@link ExecutorService}）不在此返回范围内。
     *
     * @return Bean 名称 → ThreadPoolTaskExecutor 的映射；无线程池时返回空 Map
     * @since 1.2.1
     */
    public Map<String, ThreadPoolTaskExecutor> getExecutors() {
        if (applicationContext == null) {
            return java.util.Collections.emptyMap();
        }
        return applicationContext.getBeansOfType(ThreadPoolTaskExecutor.class);
    }

    /**
     * 线程池与指标绑定器的后处理器：在线程池初始化完成后为其包装 {@link MeteredRejectedHandler}，
     * 使拒绝事件自动计入 Micrometer。
     *
     * <p>通过 BeanPostProcessor 而非构造器注入避免循环依赖：
     * ThreadPoolTaskExecutor → 拒绝策略 → MeteredRejectedHandler → ThreadPoolMetrics → ThreadPoolTaskExecutor。
     *
     * <p>v1.3.0 重构：{@link ThreadPoolRegistrar} 已提取为独立组件类。
     *
     * @return 装配后处理器
     * @since 1.3.0
     */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConditionalOnMissingBean(name = "threadPoolMetricsPostProcessor")
    public BeanPostProcessor threadPoolMetricsPostProcessor() {
        return new ThreadPoolMetricsPostProcessor();
    }

    /**
     * 线程池执行器工厂。
     *
     * <p>供 BeanDefinition 的 factory-method 使用，负责创建具体的线程池实例。
     */
    public static class ThreadPoolExecutorFactory {

        private org.springframework.context.ApplicationContext applicationContext;

        /**
         * 注入 ApplicationContext，供 TaskDecorator 配置使用。
         */
        public void setApplicationContext(org.springframework.context.ApplicationContext applicationContext) {
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

        private RejectedExecutionHandler createRejectHandler(ThreadPoolProperties.RejectPolicy policy) {
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
    }

    /**
     * 线程池指标装配后处理器。
     *
     * <p>在所有 Bean 初始化完成后，为每个平台线程池包装 {@link MeteredRejectedHandler}，
     * 实现拒绝事件自动计入 Micrometer 指标。
     *
     * <p>虚拟线程池无法使用原生拒绝策略（虚拟线程池从不拒绝），因此无需包装。
     *
     * <p>冲突防护：仅处理名称以 "Executor" 结尾、存在配套 Metrics Bean 的平台线程池，
     * 避免误处理业务自定义的 ThreadPoolTaskExecutor Bean。
     *
     * @since 1.3.0
     */
    public static class ThreadPoolMetricsPostProcessor implements BeanPostProcessor, BeanFactoryAware {

        private static final Logger log = LoggerFactory.getLogger(ThreadPoolMetricsPostProcessor.class);

        private BeanFactory beanFactory;

        @Override
        public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
            this.beanFactory = beanFactory;
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
            // 仅处理平台线程池（虚拟线程池没有原生拒绝策略）
            if (!(bean instanceof ThreadPoolTaskExecutor)) {
                return bean;
            }

            // 仅处理 ydsz-common-thread 管理的 Bean：
            //   1. 名称以 "Executor" 结尾
            //   2. 存在配套的 "<beanName>Metrics" Bean
            if (!beanName.endsWith("Executor") || beanFactory == null) {
                return bean;
            }

            // 排除工厂本身
            if ("threadPoolExecutorFactory".equals(beanName)) {
                return bean;
            }

            String metricsBeanName = beanName + "Metrics";
            if (!beanFactory.containsBean(metricsBeanName)) {
                // 不存在配套 Metrics Bean，说明不是 ydzz-common-thread 管理的线程池
                return bean;
            }

            try {
                Object metricsBean = beanFactory.getBean(metricsBeanName);
                if (!(metricsBean instanceof ThreadPoolMetrics)) {
                    return bean;
                }

                ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) bean;
                ThreadPoolMetrics metrics = (ThreadPoolMetrics) metricsBean;

                // 通过底层 ThreadPoolExecutor 获取拒绝策略（ThreadPoolTaskExecutor 本身不提供 getter）
                ThreadPoolExecutor threadPoolExecutor = executor.getThreadPoolExecutor();
                RejectedExecutionHandler currentHandler = threadPoolExecutor.getRejectedExecutionHandler();
                if (currentHandler == null) {
                    log.warn("ydsz-thread: 线程池 [{}] 拒绝策略为 null，跳过指标包装", beanName);
                    return bean;
                }

                // 避免重复包装
                if (currentHandler instanceof MeteredRejectedHandler) {
                    return bean;
                }

                MeteredRejectedHandler meteredHandler =
                        new MeteredRejectedHandler(currentHandler, metrics);
                threadPoolExecutor.setRejectedExecutionHandler(meteredHandler);
                log.info("ydsz-thread: 已为线程池 [{}] 装配指标感知拒绝策略 ([{}] → MeteredRejectedHandler)",
                        beanName, currentHandler.getClass().getSimpleName());
            } catch (Exception e) {
                log.warn("ydsz-thread: 为线程池 [{}] 装配指标感知拒绝策略失败: {}",
                        beanName, e.getMessage());
            }

            return bean;
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
