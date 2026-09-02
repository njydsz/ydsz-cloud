package com.njydsz.common.sentry.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.njydsz.common.sentry.SentryObservation;
import com.njydsz.common.sentry.SentryService;
import com.njydsz.common.sentry.adapter.SentryMetricsAdapter;

/**
 * Sentry 可观测性模块自动配置（总入口）。
 *
 * <p>通过 {@link Import} 引入各子配置类，按职责拆分为：
 *
 * <ul>
 *   <li>{@link MetricsAutoConfiguration}：指标采集 + 熔断器
 *   <li>{@link LoggingAutoConfiguration}：日志发布（ELK/Loki/双发/异步）
 *   <li>{@link TracingAutoConfiguration}：链路追踪 + 慢请求检测
 *   <li>{@link AlertingAutoConfiguration}：告警收敛 + IM 通知
 *   <li>{@link SlaAutoConfiguration}：SLA 指标采集 + AOP 切面
 *   <li>{@link SelfMonitorAutoConfiguration}：自监控指标上报
 *   <li>{@link HealthIndicatorAutoConfiguration}：Actuator 健康探针
 *   <li>{@link OtelAutoConfiguration}：OpenTelemetry SDK 增强
 * </ul>
 *
 * <p>{@code ydsz.sentry.enabled=true}（默认）时装配全部能力； {@code ydsz.sentry.enabled=false} 时整个可观测性模块不生效。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see SentryProperties
 */
@AutoConfiguration
@EnableConfigurationProperties(SentryProperties.class)
@EnableScheduling
@ConditionalOnProperty(
    prefix = "ydsz.sentry",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@Import({
  MetricsAutoConfiguration.class,
  LoggingAutoConfiguration.class,
  TracingAutoConfiguration.class,
  AlertingAutoConfiguration.class,
  SlaAutoConfiguration.class,
  SelfMonitorAutoConfiguration.class,
  HealthIndicatorAutoConfiguration.class,
  OtelAutoConfiguration.class
})
public class SentryAutoConfiguration {

  private final ObjectProvider<SentryService> sentryServiceProvider;

  /**
   * 构造方法注入 ObjectProvider。
   *
   * @param sentryServiceProvider SentryService 提供者
   */
  public SentryAutoConfiguration(ObjectProvider<SentryService> sentryServiceProvider) {
    this.sentryServiceProvider = sentryServiceProvider;
  }

  /** 注册 SentryService 的 Supplier，替代 ApplicationContextAware 静态查找。 */
  @PostConstruct
  /**
   * register sentry service。
   */
  public void registerSentryServiceSupplier() {
    SentryObservation.setSentryServiceProvider(sentryServiceProvider::getIfAvailable);
    SentryMetricsAdapter.setSentryServiceProvider(sentryServiceProvider::getIfAvailable);
  }
}
