package com.remisoft.common.thread.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

import com.remisoft.common.thread.health.ThreadHealthIndicator;
import com.remisoft.common.thread.config.ThreadPoolProperties.PoolConfig;
import com.remisoft.common.thread.config.ThreadPoolProperties.PoolType;

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
 * @author remi-team
 * @since 1.0.0
 * @see ThreadPoolProperties
 * @see ThreadHealthIndicator
 */
@AutoConfiguration
@EnableConfigurationProperties(ThreadPoolProperties.class)
@ConditionalOnProperty(prefix = "remi.thread", name = "enabled", matchIfMissing = true)
public class ThreadPoolAutoConfiguration implements InitializingBean, DisposableBean, BeanFactoryAware {

    private static final Logger log = LoggerFactory.getLogger(ThreadPoolAutoConfiguration.class);

    private final ThreadPoolProperties properties;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;
    private ConfigurableListableBeanFactory beanFactory;
    private final Map<String, Object> allExecutors = new LinkedHashMap<>();
    private final Map<String, ThreadPoolTaskExecutor> executors = new LinkedHashMap<>();
    private final Map<String, ExecutorService> virtualExecutors = new LinkedHashMap<>();

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

    /**
     * 注入并校验 BeanFactory。
     *
     * <p>本配置需要在 {@link #afterPropertiesSet()} 阶段动态注册线程池单例，
     * 因此必须持有 {@link ConfigurableListableBeanFactory}（普通 {@link BeanFactory}
     * 不具备 {@code registerSingleton} 能力）。类型不匹配时**快速失败**，
     * 避免延迟到初始化阶段才报错。
     *
     * @param beanFactory Spring 容器工厂，必须是 {@link ConfigurableListableBeanFactory} 实现
     * @throws IllegalStateException 当传入类型不是 {@link ConfigurableListableBeanFactory} 时抛出
     */
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

    /**
     * 按配置批量创建线程池并注册为容器单例。
     *
     * <p>遍历 {@link ThreadPoolProperties#getPools()}，按 {@link PoolType} 分流：
     * {@code VIRTUAL} 走 JDK 21 虚拟线程（每任务一线程，无队列与拒绝策略），
     * 其余走平台线程池 {@link ThreadPoolTaskExecutor}。
     * 注册的 Bean 名统一为 <b>{@code 配置键 + "Executor"}</b>，注入时需与之对应。
     *
     * <p><b>副作用</b>：直接调用 {@code beanFactory.registerSingleton} 动态注册，
     * 这些 Bean 不参与 Spring 的依赖注入与 AOP 代理；最后调用 {@link #bindMetrics()}
     * 绑定 Micrometer 指标。
     *
     * <p><b>边界</b>：未配置任何线程池时仅打印日志并静默返回，不视为异常；
     * 同名配置键会覆盖已有单例，需由配置层保证键唯一。
     */
    @Override
    public void afterPropertiesSet() {
        if (properties.getPools() == null || properties.getPools().isEmpty()) {
            log.info("remi-thread: 未配置线程池，跳过初始化");
            return;
        }
        properties.getPools().forEach((name, config) -> {
            if (config.getType() == PoolType.VIRTUAL) {
                ExecutorService executor = createVirtualExecutor(name, config);
                virtualExecutors.put(name, executor);
                allExecutors.put(name, executor);
                beanFactory.registerSingleton(name + "Executor", executor);
                log.info("remi-thread: 注册虚拟线程池 [{}] (prefix={})", name, config.getThreadNamePrefix());
            } else {
                ThreadPoolTaskExecutor executor = createExecutor(name, config);
                executors.put(name, executor);
                allExecutors.put(name, executor);
                beanFactory.registerSingleton(name + "Executor", executor);
                log.info("remi-thread: 注册线程池 [{}] (core={}, max={}, queue={}, prefix={}, reject={})",
                    name, config.getCoreSize(), config.getMaxSize(), config.getQueueCapacity(),
                    config.getThreadNamePrefix(), config.getRejectPolicy());
            }
        });
        bindMetrics();
    }

    /**
     * 容器关闭时优雅停止全部线程池。
     *
     * <p>对平台线程池调用 {@code shutdown()}：因创建时已设置
     * {@code waitForTasksToCompleteOnShutdown=true} 与 {@code awaitTerminationSeconds}，
     * 会先拒绝新任务、再等待存量任务执行完毕（超时后强制结束）。
     * 虚拟线程池同样调用 {@code shutdown()} 停止接收新任务。
     *
     * <p><b>注意</b>：本方法仅停止线程池，<b>不</b>从 BeanFactory 注销已注册的单例；
     * 随后清空内部三个映射，使实例不可再用。方法不抛异常，保证关闭流程不被中断。
     */
    @Override
    public void destroy() {
        executors.forEach((name, executor) -> {
            log.info("remi-thread: 关闭线程池 [{}]", name);
            executor.shutdown();
        });
        virtualExecutors.forEach((name, executor) -> {
            log.info("remi-thread: 关闭虚拟线程池 [{}]", name);
            executor.shutdown();
        });
        executors.clear();
        virtualExecutors.clear();
        allExecutors.clear();
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

    /**
     * 创建虚拟线程池（JDK 21+）。
     *
     * @param name   线程池名称
     * @param config 线程池配置
     * @return 虚拟线程 ExecutorService
     */
    private ExecutorService createVirtualExecutor(String name, PoolConfig config) {
        return Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name(config.getThreadNamePrefix(), 0).factory()
        );
    }

    private ThreadPoolTaskExecutor createExecutor(String name, PoolConfig config) {
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
        if (registry == null || allExecutors.isEmpty()) {
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
                log.warn("remi-thread: 绑定线程池 [{}] 底层指标失败", name, e);
            }
        });
        log.info("remi-thread: Micrometer 指标绑定完成 (platform={}, virtual={})",
            executors.size(), virtualExecutors.size());
    }
}
