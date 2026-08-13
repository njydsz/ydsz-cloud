package com.njydsz.common.thread.config;

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
import org.springframework.context.annotation.Role;
import org.springframework.core.Ordered;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.njydsz.common.thread.config.ThreadPoolProperties.PoolConfig;
import com.njydsz.common.thread.config.ThreadPoolProperties.PoolType;
import com.njydsz.common.thread.metrics.ThreadPoolMetrics;
import com.njydsz.common.thread.metrics.VirtualThreadMetrics;

/**
 * 线程池 Bean 定义注册器。
 *
 * <p>实现 {@link BeanDefinitionRegistryPostProcessor}，在所有常规 BeanDefinition 加载完成后、
 * Bean 实例化之前，动态注册线程池 + 指标 BeanDefinition。
 *
 * <p>作为独立 {@code @Component} 而非内部类，确保 {@code ApplicationContextRunner} 测试
 * 和 Spring Boot 自动装配均能正确识别并调用本注册器。
 *
 * @author ydsz-team
 * @since 1.2.0
 */
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
@ConditionalOnMissingBean(name = "threadPoolRegistrar")
public class ThreadPoolRegistrar implements BeanDefinitionRegistryPostProcessor, Ordered {

    private static final Logger log = LoggerFactory.getLogger(ThreadPoolRegistrar.class);

    private final ThreadPoolProperties properties;

    public ThreadPoolRegistrar(ThreadPoolProperties properties) {
        this.properties = properties;
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
                BeanDefinition bd = BeanDefinitionBuilder
                        .rootBeanDefinition(ExecutorService.class)
                        .setFactoryMethodOnBean("createVirtualExecutor", "threadPoolExecutorFactory")
                        .addConstructorArgValue(name)
                        .addConstructorArgValue(config)
                        .setRole(BeanDefinition.ROLE_INFRASTRUCTURE)
                        .getBeanDefinition();
                registry.registerBeanDefinition(beanName, bd);

                // 注册虚拟线程池指标 Bean
                String metricsBeanName = beanName + "Metrics";
                if (!registry.containsBeanDefinition(metricsBeanName)) {
                    BeanDefinition metricsBd = BeanDefinitionBuilder
                            .rootBeanDefinition(VirtualThreadMetrics.class)
                            .addConstructorArgReference(beanName)
                            .addConstructorArgValue(name)
                            .addConstructorArgValue(config.getMetricPrefix())
                            .setRole(BeanDefinition.ROLE_INFRASTRUCTURE)
                            .getBeanDefinition();
                    registry.registerBeanDefinition(metricsBeanName, metricsBd);
                }

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

                // 注册平台线程池指标 Bean
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

                log.info("ydsz-thread: 注册线程池 [{}] (core={}, max={}, queue={}, prefix={}, reject={}, taskDecorators={})",
                        beanName, config.getCoreSize(), config.getMaxSize(), config.getQueueCapacity(),
                        config.getThreadNamePrefix(), config.getRejectPolicy(),
                        config.getTaskDecoratorBeanNames() == null ? 0 : config.getTaskDecoratorBeanNames().size());
            }

            log.info("ydsz-thread: 注册指标绑定器 [{}Metrics]", beanName);
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
}
