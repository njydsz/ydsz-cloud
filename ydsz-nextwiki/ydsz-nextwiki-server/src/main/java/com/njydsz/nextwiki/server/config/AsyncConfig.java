package com.njydsz.nextwiki.server.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableAsync;

import com.njydsz.common.file.storage.IFileStorageProvider;
import com.njydsz.common.file.virus.ClamAvVirusScanner;
import com.njydsz.common.file.virus.VirusScanner;
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
 * @since 26.10.01
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

  /**
   * P1-4: ClamAV 病毒扫描器注册为 VirusScanner SPI 实现。
   *
   * <p>通过 {@code @Primary} 标记，common-file 的 {@code FileConfiguration} 检测到已有
   * {@link VirusScanner} Bean 后自动跳过 {@code NoOpVirusScanner} 装配。 扫描器配置从
   * {@link NextwikiProperties.VirusScanConfig} 读取（host/port/enabled）。
   */
  @Bean
  @Primary
  public VirusScanner clamAvVirusScanner(NextwikiProperties properties) {
    NextwikiProperties.VirusScanConfig virusScan = properties.getVirusScan();
    return new ClamAvVirusScanner(
        virusScan.getHost(),
        virusScan.getPort(),
        100L * 1024 * 1024, // 100MB 文件大小上限
        virusScan.isEnabled());
  }
}
