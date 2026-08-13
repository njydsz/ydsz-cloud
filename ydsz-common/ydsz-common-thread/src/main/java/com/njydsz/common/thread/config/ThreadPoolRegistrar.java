package com.njydsz.common.thread.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Role;
import org.springframework.core.Ordered;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.njydsz.common.thread.config.ThreadPoolProperties.PoolConfig;
import com.njydsz.common.thread.config.ThreadPoolProperties.PoolType;
import com.njydsz.common.thread.metrics.MeteredVirtualExecutorService;
import com.njydsz.common.thread.metrics.ThreadPoolMetrics;
import com.njydsz.common.thread.metrics.ThreadPoolTimerMetrics;
import com.njydsz.common.thread.metrics.VirtualThreadMetrics;

/**
 * 线程池 Bean 定义注册器。
 *
 * <p>实现 {@link BeanDefinitionRegistryPostProcessor}，在所有常规 BeanDefinition 加载完成后、
 * Bean 实例化之前，动态注册线程池 + 指标 BeanDefinition。
 *
 * <p>作为独立类而非内部类，确保 {@code ApplicationContextRunner} 测试
 * 和 Spring Boot 自动装配均能正确识别并调用本注册器。
 *
 * <p>v1.3.1 修复：
 * <ul>
 *   <li>支持 {@code @Bean} 方式由 {@link ThreadPoolAutoConfiguration} 显式注册</li>
 *   <li>虚拟线程池自动包装 {@link MeteredVirtualExecutorService} 实现计数</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.2.0
 */
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
@ConditionalOnMissingBean(name = "threadPoolRegistrar")
public class ThreadPoolRegistrar implements BeanDefinitionRegistryPostProcessor, Ordered, ApplicationContextAware {

    private static final Logger log = LoggerFactory.getLogger(ThreadPoolRegistrar.class);

    private final ThreadPoolProperties properties;
    private ApplicationContext applicationContext;

    public ThreadPoolRegistrar(ThreadPoolProperties properties) {
        this.properties = properties;
    }

    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(@NonNull BeanDefinitionRegistry registry) throws BeansException {
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

        // 注册线程池 + 指标 Bean
        String prefix = properties.getBeanNamePrefix() != null ? properties.getBeanNamePrefix() : "";
        for (Map.Entry<String, PoolConfig> entry : properties.getPools().entrySet()) {
            String name = entry.getKey();
            PoolConfig config = entry.getValue();
            String beanName = prefix + name + "Executor";

            if (registry.containsBeanDefinition(beanName)) {
                log.warn("ydsz-thread: Bean [{}] 已存在，跳过注册（可能与业务 Bean 命名冲突）", beanName);
                continue;
            }

            if (config.getType() == PoolType.VIRTUAL) {
                registerVirtualThreadPool(registry, name, config, beanName);
            } else {
                registerPlatformThreadPool(registry, name, config, beanName);
            }
        }
    }

    @Override
    public void postProcessBeanFactory(@NonNull ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // 无需额外处理
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    // ====================== private helpers ======================

    /**
     * 注册虚拟线程池及其指标绑定器。
     *
     * <p>v1.3.1：通过 ObjectProvider 延迟获取原始 ExecutorService 和 VirtualThreadMetrics，
     * 然后包装为 {@link MeteredVirtualExecutorService}，解决计数器空转问题。
     */
    private void registerVirtualThreadPool(BeanDefinitionRegistry registry, String name,
                                            PoolConfig config, String beanName) {
        // 注册原始的虚拟线程池内部 Bean
        String innerExecutorBeanName = beanName + "_Inner";
        BeanDefinition bd = BeanDefinitionBuilder
                .rootBeanDefinition(ExecutorService.class)
                .setFactoryMethodOnBean("createVirtualExecutor", "threadPoolExecutorFactory")
                .addConstructorArgValue(name)
                .addConstructorArgValue(config)
                .setRole(BeanDefinition.ROLE_INFRASTRUCTURE)
                .getBeanDefinition();
        registry.registerBeanDefinition(innerExecutorBeanName, bd);

        // 注册虚拟线程池指标 Bean
        String metricsBeanName = beanName + "Metrics";
        if (!registry.containsBeanDefinition(metricsBeanName)) {
            BeanDefinition metricsBd = BeanDefinitionBuilder
                    .rootBeanDefinition(VirtualThreadMetrics.class)
                    .addConstructorArgValue(name)
                    .addConstructorArgValue(config.getMetricPrefix())
                    .setRole(BeanDefinition.ROLE_INFRASTRUCTURE)
                    .getBeanDefinition();
            registry.registerBeanDefinition(metricsBeanName, metricsBd);
        }

        // 注册带指标追踪的包装器作为最终暴露的 Bean
        BeanDefinition wrappedBd = BeanDefinitionBuilder
                .rootBeanDefinition(MeteredVirtualExecutorService.class)
                .addConstructorArgReference(innerExecutorBeanName)
                .addConstructorArgReference(metricsBeanName)
                .setRole(BeanDefinition.ROLE_INFRASTRUCTURE)
                .getBeanDefinition();
        registry.registerBeanDefinition(beanName, wrappedBd);

        log.info("ydsz-thread: 注册虚拟线程池 [{}] (prefix={}, inner={}, metrics={}, wrapped={})",
                beanName, config.getThreadNamePrefix(), innerExecutorBeanName,
                metricsBeanName, beanName);
    }

    /**
     * 注册平台线程池及其指标绑定器。
     *
     * <p>v1.3.1 变更：新增 {@link ThreadPoolTimerMetrics} Bean 注册，
     * 用于任务执行耗时与排队时长的指标追踪。
     */
    private void registerPlatformThreadPool(BeanDefinitionRegistry registry, String name,
                                             PoolConfig config, String beanName) {
        BeanDefinition bd = BeanDefinitionBuilder
                .rootBeanDefinition(ThreadPoolTaskExecutor.class)
                .setFactoryMethodOnBean("createTaskExecutor", "threadPoolExecutorFactory")
                .addConstructorArgValue(name)
                .addConstructorArgValue(config)
                .setRole(BeanDefinition.ROLE_INFRASTRUCTURE)
                .getBeanDefinition();
        registry.registerBeanDefinition(beanName, bd);

        // 注册平台线程池核心指标 Bean
        String metricsBeanName = beanName + "Metrics";
        if (!registry.containsBeanDefinition(metricsBeanName)) {
            BeanDefinition metricsBd = BeanDefinitionBuilder
                    .rootBeanDefinition(ThreadPoolMetrics.class)
                    .addConstructorArgReference(beanName)
                    .addConstructorArgValue(name)
                    .addConstructorArgValue(config.getMetricPrefix())
                    .setRole(BeanDefinition.ROLE_INFRASTRUCTURE)
                    .getBeanDefinition();
            registry.registerBeanDefinition(metricsBeanName, metricsBd);
        }

        // 注册平台线程池耗时指标 Bean（v1.3.1 新增）
        String timerMetricsBeanName = beanName + "TimerMetrics";
        if (!registry.containsBeanDefinition(timerMetricsBeanName)) {
            BeanDefinition timerMetricsBd = BeanDefinitionBuilder
                    .rootBeanDefinition(ThreadPoolTimerMetrics.class)
                    .addConstructorArgValue(name)
                    .addConstructorArgValue(config.getMetricPrefix())
                    .setRole(BeanDefinition.ROLE_INFRASTRUCTURE)
                    .getBeanDefinition();
            registry.registerBeanDefinition(timerMetricsBeanName, timerMetricsBd);
        }

        log.info("ydsz-thread: 注册线程池 [{}] (core={}, max={}, queue={}, prefix={}, reject={}, taskDecorators={})",
                beanName, config.getCoreSize(), config.getMaxSize(), config.getQueueCapacity(),
                config.getThreadNamePrefix(), config.getRejectPolicy(),
                config.getTaskDecoratorBeanNames() == null ? 0 : config.getTaskDecoratorBeanNames().size());
    }

    /**
     * 获取本模块管理的线程池 Bean 名称集合（以配置 key 为键）。
     *
     * <p>供下游模块使用，避免通过 Bean 名称字符串拼接或类型扫描的方式查找。
     *
     * @return 配置 key → Bean 名称的映射，key 对应 {@link ThreadPoolProperties#getPools()} 的 key
     * @since 1.3.1
     */
    public Map<String, String> getManagedBeanNames() {
        Map<String, String> result = new LinkedHashMap<>();
        if (properties.getPools() == null || properties.getPools().isEmpty()) {
            return result;
        }
        String prefix = properties.getBeanNamePrefix() != null ? properties.getBeanNamePrefix() : "";
        for (String key : properties.getPools().keySet()) {
            result.put(key, prefix + key + "Executor");
        }
        return result;
    }
}
