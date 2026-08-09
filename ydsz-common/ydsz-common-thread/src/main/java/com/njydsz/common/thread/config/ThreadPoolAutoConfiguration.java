package com.njydsz.common.thread.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;
import org.springframework.core.Ordered;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.njydsz.common.thread.config.ThreadPoolProperties.PoolConfig;
import com.njydsz.common.thread.config.ThreadPoolProperties.PoolType;
import com.njydsz.common.thread.health.ThreadHealthIndicator;
import com.njydsz.common.thread.metrics.ThreadPoolMetrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

/**
 * 统一线程池自动配置。
 *
 * <p>根据 {@link ThreadPoolProperties#getPools()} 配置动态创建并注册多个
 * {@link ThreadPoolTaskExecutor} Bean，Bean 名称为 {@code key + "Executor"}。
 *
 * <p>功能特性：
 * <ul>
 *   <li>按业务隔离：每个线程池独立的 coreSize/maxSize/queue/rejectPolicy</li>
 *   <li>Micrometer 指标：active/queueSize/completed/poolSize Gauge</li>
 *   <li>优雅关闭：shutdown 时等待任务完成</li>
 *   <li>健康检查：自动注册 {@link ThreadHealthIndicator}</li>
 * </ul>
 *
 * <p>注入方式：
 * <pre>{@code
 * @Resource(name = "ioExecutor")
 * private ThreadPoolTaskExecutor ioExecutor;
 * }</pre>
 *
 * <p><b>v1.2.0 变更：</b>使用 {@link BeanDefinitionRegistryPostProcessor} 在 Bean 定义阶段注册线程池，
 * 替代 v1.x 的 {@code registerSingleton} 动态注册方式。新方式下线程池 Bean 参与完整的 Spring Bean 生命周期，
 * 包括依赖注入、AOP 代理、{@code @PreDestroy} 等注解处理。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see ThreadPoolProperties
 * @see ThreadHealthIndicator
 */
@AutoConfiguration
@EnableConfigurationProperties(ThreadPoolProperties.class)
@ConditionalOnProperty(prefix = "ydsz.thread", name = "enabled", matchIfMissing = true)
public class ThreadPoolAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ThreadPoolAutoConfiguration.class);

    /**
     * 注册线程池 Bean 定义注册器。
     *
     * <p>v1.2.0 引入：使用 {@link BeanDefinitionRegistryPostProcessor} 在容器刷新阶段
     * 动态注册 BeanDefinition，替代 v1.x 的 {@code registerSingleton} 动态注册方式。
     *
     * @param properties 线程池配置
     * @return Bean 定义注册器
     */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConditionalOnMissingBean(name = "threadPoolRegistrar")
    public ThreadPoolRegistrar threadPoolRegistrar(ThreadPoolProperties properties) {
        return new ThreadPoolRegistrar(properties);
    }

    /**
     * 线程池 Bean 定义注册器。
     *
     * <p>实现 {@link BeanDefinitionRegistryPostProcessor}，在所有常规 BeanDefinition 加载完成后、
     * Bean 实例化之前，动态注册线程池 BeanDefinition。
     */
    public static class ThreadPoolRegistrar implements BeanDefinitionRegistryPostProcessor, Ordered {

        private final ThreadPoolProperties properties;

        public ThreadPoolRegistrar(ThreadPoolProperties properties) {
            this.properties = properties;
        }

        @Override
        public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
            if (properties.getPools() == null || properties.getPools().isEmpty()) {
                log.info("ydsz-thread: 未配置线程池，跳过动态注册");
                return;
            }

            // 先注册工厂 Bean
            if (!registry.containsBeanDefinition("threadPoolExecutorFactory")) {
                registry.registerBeanDefinition("threadPoolExecutorFactory",
                        BeanDefinitionBuilder.rootBeanDefinition(ThreadPoolExecutorFactory.class)
                                .setRole(BeanDefinition.ROLE_INFRASTRUCTURE)
                                .getBeanDefinition());
            }

            // 注册线程池 Bean
            properties.getPools().forEach((name, config) -> {
                String beanName = name + "Executor";
                if (registry.containsBeanDefinition(beanName)) {
                    log.info("ydsz-thread: Bean [{}] 已存在，跳过注册", beanName);
                    return;
                }

                if (config.getType() == PoolType.VIRTUAL) {
                    BeanDefinition bd = BeanDefinitionBuilder
                            .rootBeanDefinition(ExecutorService.class)
                            .setFactoryMethodOnBean("createVirtualExecutor", "threadPoolExecutorFactory")
                            .addConstructorArgValue(name)
                            .addConstructorArgValue(config)
                            .setRole(BeanDefinition.ROLE_INFRASTRUCTURE)
                            .getBeanDefinition();
                    registry.registerBeanDefinition(beanName, bd);
                    log.info("ydsz-thread: 注册虚拟线程池 [{}] (prefix={})", beanName, config.getThreadNamePrefix());
                } else {
                    BeanDefinition bd = BeanDefinitionBuilder
                            .rootBeanDefinition(ThreadPoolTaskExecutor.class)
                            .setFactoryMethodOnBean("createTaskExecutor", "threadPoolExecutorFactory")
                            .addConstructorArgValue(name)
                            .addConstructorArgValue(config)
                            .setRole(BeanDefinition.ROLE_INFRASTRUCTURE)
                            .getBeanDefinition();
                    registry.registerBeanDefinition(beanName, bd);
                    log.info("ydsz-thread: 注册线程池 [{}] (core={}, max={}, queue={}, prefix={}, reject={})",
                            beanName, config.getCoreSize(), config.getMaxSize(), config.getQueueCapacity(),
                            config.getThreadNamePrefix(), config.getRejectPolicy());
                }
            });
        }

        @Override
        public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
            // 无需额外处理
        }

        @Override
        public int getOrder() {
            return Ordered.LOWEST_PRECEDENCE;
        }
    }

    /**
     * 线程池执行器工厂。
     *
     * <p>供 BeanDefinition 的 factory-method 使用，负责创建具体的线程池实例。
     */
    public static class ThreadPoolExecutorFactory {

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
            executor.setBeanName(name + "Executor");
            executor.initialize();
            return executor;
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
}
