package com.njydsz.common.batch.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.njydsz.common.batch.launcher.JobLauncher;
import com.njydsz.common.batch.properties.BatchProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * 批处理模块自动配置
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BatchProperties.class)
@ConditionalOnClass(JobLauncher.class)
@ConditionalOnProperty(prefix = "ydsz.batch", name = "enabled", havingValue = "true", matchIfMissing = true)
public class BatchAutoConfiguration {

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    public JobLauncher jobLauncher(BatchProperties properties) {
        log.info("Initializing job launcher, threadPoolSize={}", properties.getDefaultThreadPoolSize());
        return new JobLauncher();
    }
}
