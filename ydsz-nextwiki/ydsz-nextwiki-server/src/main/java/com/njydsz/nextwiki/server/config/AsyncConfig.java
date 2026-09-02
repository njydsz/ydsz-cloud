package com.njydsz.nextwiki.server.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import com.njydsz.common.file.storage.IFileStorageProvider;
import com.njydsz.nextwiki.domain.repository.FileNodeRepository;
import com.njydsz.nextwiki.server.health.NextwikiHealthIndicator;

/**
 * NextWiki 基础设施配置
 *
 * <p>启用缓存和异步支持。RestTemplate 由 ydsz-common-notify 统一提供， 异步线程池由 ydsz-common-thread 统一管理，通过 YAML 配置。
 *
 * <p>同时注册 {@link NextwikiProperties} 配置属性绑定， 替代各 Service 中散落的 {@code @Value} 注入。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Configuration
@EnableCaching
@EnableAsync
@EnableConfigurationProperties(NextwikiProperties.class)
public class AsyncConfig {

  /** P1-1: 健康检查 Bean 注册（统一模式，不使用 @Component） */
  @Bean
  @ConditionalOnClass(HealthIndicator.class)
  @ConditionalOnMissingBean(NextwikiHealthIndicator.class)
  public NextwikiHealthIndicator nextwikiHealthIndicator(
      FileNodeRepository fileNodeRepository,
      ObjectProvider<IFileStorageProvider> fileStorageProvider) {
    NextwikiHealthIndicator indicator = new NextwikiHealthIndicator(fileNodeRepository);
    IFileStorageProvider provider = fileStorageProvider.getIfAvailable();
    if (provider != null) {
      indicator.setFileStorageProvider(provider);
    }
    return indicator;
  }
}
