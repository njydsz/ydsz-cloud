package com.njydsz.common.thread.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.njydsz.common.thread.health.ThreadHealthIndicator;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

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
 *   <li>优雅关闭：实现 {@link DisposableBean}，shutdown 时等待任务完成</li>
 *   <li>健康检查：自动注册 {@link ThreadHealthIndicator}</li>
 * </ul>
 *
 * <p>注入方式：
 * <pre>{@code
 * @Resource(name = "ioExecutor")
 * private ThreadPoolTaskExecutor ioExecutor;
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see ThreadPoolProperties
 * @see ThreadHealthIndicator
 */
@AutoConfiguration
@EnableConfigurationProperties(ThreadPoolProperties.class)
@ConditionalOnProperty(prefix = "ydsz.thread", name = "enabled", matchIfMissing = true)
public class ThreadPoolAutoConfiguration implements InitializingBean, DisposableBean, BeanFactoryAware {

    private static final Logger log = LoggerFactory.getLogger(ThreadPoolAutoConfiguration.class);

    private final ThreadPoolProperties properties;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;
    private ConfigurableListableBeanFactory beanFactory;
    private final Map<String, ThreadPoolTaskExecutor> executors = new LinkedHashMap<>();

    /**
     * 构造线程池自动配置。
     *
     * @param properties            线程池配置
     * @param meterRegistryProvider Micrometer 注册表（可选，用于指标绑定）
     */
    public ThreadPoolAutoConfiguration(ThreadPoolProperties properties,
                                       ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.properties = properties;
        this.meterRegistryProvider = meterRegistryProvider;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        if (beanFactory instanceof ConfigurableListableBeanFactory) {
            this.beanFactory = (ConfigurableListableBeanFactory) beanFactory;
        } else {
            throw new IllegalStateException(
                "BeanFactory must be ConfigurableListableBeanFactory, but was: "
                + (beanFactory != null ? beanFactory.getClass().getName() : "null"));
        }
    }

    @Override
    public void afterPropertiesSet() {
        if (properties.getPools() == null || properties.getPools().isEmpty()) {
            log.info("ydsz-thread: 未配置线程池，跳过初始化");
            return;
        }
        properties.getPools().forEach((name, config) -> {
            ThreadPoolTaskExecutor executor = createExecutor(name, config);
            executors.put(name, executor);
            beanFactory.registerSingleton(name + "Executor", executor);
            log.info("ydsz-thread: 注册线程池 [{}] (core={}, max={}, queue={}, prefix={}, reject={})",
                name, config.getCoreSize(), config.getMaxSize(), config.getQueueCapacity(),
                config.getThreadNamePrefix(), config.getRejectPolicy());
        });
        bindMetrics();
    }

    @Override
    public void destroy() {
        for (Map.Entry<String, ThreadPoolTaskExecutor> entry : executors.entrySet()) {
            log.info("ydsz-thread: 关闭线程池 [{}]", entry.getKey());
            entry.getValue().shutdown();
        }
        executors.clear();
    }

    /**
     * 注册线程池健康检查（可选，依赖 spring-boot-health）。
     *
     * @return ThreadHealthIndicator 实例
     */
    @Bean
    @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
    @ConditionalOnMissingBean(name = "threadHealthIndicator")
    public ThreadHealthIndicator threadHealthIndicator() {
        return new ThreadHealthIndicator();
    }

    /**
     * 获取已注册的线程池映射（不可变）。
     *
     * @return 线程池名到执行器的映射
     */
    public Map<String, ThreadPoolTaskExecutor> getExecutors() {
        return Collections.unmodifiableMap(executors);
    }

    private ThreadPoolTaskExecutor createExecutor(String name, ThreadPoolProperties.PoolConfig config) {
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

    private void bindMetrics() {
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry == null || executors.isEmpty()) {
            return;
        }
        executors.forEach((name, executor) -> {
            Gauge.builder("executor.active", executor, e -> (double) e.getActiveCount())
                .tag("name", name)
                .register(registry);
            try {
                ThreadPoolExecutor pool = executor.getThreadPoolExecutor();
                Gauge.builder("executor.queue.size", pool, p -> (double) p.getQueue().size())
                    .tag("name", name)
                    .register(registry);
                Gauge.builder("executor.completed", pool, p -> (double) p.getCompletedTaskCount())
                    .tag("name", name)
                    .register(registry);
                Gauge.builder("executor.pool.size", pool, p -> (double) p.getPoolSize())
                    .tag("name", name)
                    .register(registry);
            } catch (Exception e) {
                log.warn("ydsz-thread: 绑定线程池 [{}] 底层指标失败", name, e);
            }
        });
        log.info("ydsz-thread: Micrometer 指标绑定完成 (pools={})", executors.size());
    }
}
