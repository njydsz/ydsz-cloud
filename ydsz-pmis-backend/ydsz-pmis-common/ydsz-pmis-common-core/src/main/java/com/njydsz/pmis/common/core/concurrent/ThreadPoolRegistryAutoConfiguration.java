package com.njydsz.pmis.common.core.concurrent;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * 线程池注册中心自动配置
 *
 * @author Marvin Lee
 * @since 3.5.0
 */
@AutoConfiguration
@ConditionalOnClass(ThreadPoolRegistry.class)
@ConditionalOnProperty(prefix = "pmis.threadpool", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ThreadPoolRegistryAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ThreadPoolRegistry threadPoolRegistry(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        return new ThreadPoolRegistry(meterRegistryProvider.getIfAvailable());
    }
}
