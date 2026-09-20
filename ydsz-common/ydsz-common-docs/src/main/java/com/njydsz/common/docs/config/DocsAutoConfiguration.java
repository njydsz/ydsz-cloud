package com.njydsz.common.docs.config;

import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

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
 * <p><b>生命周期状态：</b>production · 稳定（Sprint 1 已补齐全部解析器集成测试，储备义务解除）。
 *
 * <p><b>SPI 扩展能力：</b>文档读取与结构化解析（文本提取 + 分节 + 表格 + 图片），PII 检测，安全扫描。
 *
 * <p><b>明确不负责：</b>
 *
 * <ul>
 *   <li>embedding / 向量化（由 ydsz-agent 的 DocumentChunker + VectorStore 承接）
 *   <li>文档格式互转（由外部 LibreOffice / OnlyOffice 服务承接）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
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
  // CHECKSTYLE.OFF: RegexpSinglelineJava — 字符串常量（注解/反射类名），非代码引用
  @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
  // CHECKSTYLE.ON: RegexpSinglelineJava
  public DocsHealthIndicator docsHealthIndicator(
      DocumentParserRegistry parserRegistry,
      List<PiiDetector> piiDetectors,
      DocsProperties properties,
      AsyncDocumentParser asyncDocumentParser) {
    return new DocsHealthIndicator(parserRegistry, piiDetectors, properties, asyncDocumentParser);
  }

  /**
   * 默认的异步解析线程池，仅在应用层未定义名为 {@code docsAsyncExecutor} 的 Bean 时自动装配。
   *
   * <p>使用 {@link ThreadPoolTaskExecutor} 以获得 Spring 容器托管的生命周期（{@code @PreDestroy} 自动关闭）。
   * 线程池大小与队列容量由 {@code ydsz.docs.async-pool-size} 与 {@code ydsz.docs.async-queue-capacity} 控制。
   *
   * <p>应用方可通过声明同名 Bean 覆盖本默认实现，从而实现更细粒度的调优（如自定义拒绝策略）。
   *
   * @param properties 文档模块配置属性
   * @return 线程池 TaskExecutor
   */
  @Bean(name = "docsAsyncExecutor", destroyMethod = "shutdown")
  @ConditionalOnMissingBean(name = "docsAsyncExecutor")
  // CHECKSTYLE.OFF: RegexpSinglelineJava — L5 业务模块提供默认线程池 Bean，
  // 应用方可通过同名 Bean 覆盖。使用 ThreadPoolTaskExecutor 以便 Spring 容器托管生命周期
  public ThreadPoolTaskExecutor docsAsyncExecutor(DocsProperties properties) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(properties.getAsyncPoolSize());
    executor.setMaxPoolSize(properties.getAsyncPoolSize());
    executor.setQueueCapacity(properties.getAsyncQueueCapacity());
    executor.setThreadNamePrefix("docs-async-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(10);
    executor.initialize();
    log.info(
        "[DocsAutoConfiguration] 已创建默认 docsAsyncExecutor (poolSize={}, queueCapacity={})",
        properties.getAsyncPoolSize(),
        properties.getAsyncQueueCapacity());
    return executor;
  }
}
