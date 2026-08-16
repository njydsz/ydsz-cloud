package com.njydsz.common.docs.config;

import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.njydsz.common.docs.health.DocsHealthIndicator;
import com.njydsz.common.docs.parser.registry.DocumentParserRegistry;
import com.njydsz.common.docs.security.pii.PiiDetector;
import com.njydsz.common.docs.service.AsyncDocumentParser;

/**
 * 文档处理模块自动配置类
 *
 * <p>作为 ydsz-common-docs 模块的自动配置入口， 通过 Spring Boot AutoConfiguration 机制激活文档解析、预处理、安全扫描和 PII 检测等能力。
 *
 * <p><b>配置开关：</b> {@code ydsz.docs.enabled=true}（默认启用）
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(DocsProperties.class)
@ConditionalOnProperty(
    prefix = "ydsz.docs",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class DocsAutoConfiguration {

  public DocsAutoConfiguration(DocsProperties properties) {
    log.info(
        "[DocsAutoConfiguration] 文档处理模块已启用 | 安全扫描={} | PII检测={} | 预处理={}",
        properties.isSecurityScanEnabled(),
        properties.isPiiDetectionEnabled(),
        properties.isPreprocessEnabled());
  }

  /**
   * 装配文档模块健康探针，暴露解析器注册情况、PII 检测器状态与异步队列水位。
   *
   * <p>仅在 classpath 存在 Actuator health 相关类时生效， 使本模块可被无 Actuator 的应用（如纯批处理任务）直接依赖而不报错。
   *
   * @param parserRegistry 解析器注册表，用于探测已支持的文档格式
   * @param piiDetectors PII 检测器列表，用于探测检测规则是否加载成功
   * @param properties 文档模块配置，用于在健康详情中回显各功能开关
   * @param asyncDocumentParser 异步解析器，用于探测队列积压情况
   * @return 文档模块健康探针
   */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
  public DocsHealthIndicator docsHealthIndicator(
      DocumentParserRegistry parserRegistry,
      List<PiiDetector> piiDetectors,
      DocsProperties properties,
      AsyncDocumentParser asyncDocumentParser) {
    return new DocsHealthIndicator(parserRegistry, piiDetectors, properties, asyncDocumentParser);
  }
}
