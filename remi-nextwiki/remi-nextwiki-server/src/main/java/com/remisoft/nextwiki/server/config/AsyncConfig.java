package com.remisoft.nextwiki.server.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import com.remisoft.common.file.storage.IFileStorageProvider;
import com.remisoft.nextwiki.domain.repository.FileNodeRepository;
import com.remisoft.nextwiki.server.health.NextwikiHealthIndicator;
import com.remisoft.nextwiki.server.metrics.NextwikiMetrics;

/**
 * NextWiki 基础设施配置
 * <p>
 * 启用缓存和异步支持。RestTemplate 由 remi-common-notify 统一提供，
 * 异步线程池由 remi-common-thread 统一管理，通过 YAML 配置。
 *
 * <p>同时注册 {@link NextwikiProperties} 配置属性绑定，
 * 替代各 Service 中散落的 {@code @Value} 注入。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Configuration
@EnableCaching
@EnableAsync
@EnableConfigurationProperties(NextwikiProperties.class)
public class AsyncConfig {

    /**
     * P1-1: 健康检查 Bean 注册（统一模式，不使用 @Component）
     */
    @Bean
    @ConditionalOnClass(HealthIndicator.class)
    @ConditionalOnMissingBean(NextwikiHealthIndicator.class)
    public NextwikiHealthIndicator nextwikiHealthIndicator(
            FileNodeRepository fileNodeRepository,
            NextwikiMetrics nextwikiMetrics,
            ObjectProvider<IFileStorageProvider> fileStorageProvider) {
        NextwikiHealthIndicator indicator = new NextwikiHealthIndicator(fileNodeRepository, nextwikiMetrics);
        IFileStorageProvider provider = fileStorageProvider.getIfAvailable();
        if (provider != null) {
            indicator.setFileStorageProvider(provider);
        }
        return indicator;
    }
}
