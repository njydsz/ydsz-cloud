package com.njydsz.common.sentry.config;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.sentry.spi.MetricsCollector;
import com.njydsz.common.sentry.spi.TraceContext;
import com.njydsz.common.sentry.tracing.DefaultTraceContext;
import com.njydsz.common.sentry.tracing.OpenTelemetryTraceContext;
import com.njydsz.common.sentry.tracing.SkyWalkingTraceContext;
import com.njydsz.common.sentry.tracing.SlowTraceDetector;

/**
 * 链路追踪自动配置。
 *
 * <p>按 {@code tracing.primary} 选择链路上下文实现，并逐级降级保证始终有可用实现。
 *
 * <p>降级链路：SkyWalking（需探针已挂载）→ OpenTelemetry（需 SDK 可用）→ {@link DefaultTraceContext}（纯 MDC，仅本进程内
 * traceId 透传，无跨服务串联能力）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter(MetricsAutoConfiguration.class)
@EnableConfigurationProperties(SentryProperties.class)
public class TracingAutoConfiguration {

  /**
   * 按 {@code tracing.primary} 选择链路上下文实现，并逐级降级。
   *
   * @param properties 监控配置
   * @return 链路上下文实现，永不为 {@code null}
   */
  @Bean
  @ConditionalOnMissingBean(TraceContext.class)
  public TraceContext traceContext(SentryProperties properties) {
    String primary = properties.getTracing().getPrimary();
    if ("skywalking".equals(primary)) {
      try {
        Class.forName("org.apache.skywalking.apm.toolkit.trace.TraceContext");
        return new SkyWalkingTraceContext();
      } catch (ClassNotFoundException e) {
        log.info("[Sentry] SkyWalking agent 未检测到, 尝试 OpenTelemetry");
      }
    }
    if ("opentelemetry".equals(primary) || "skywalking".equals(primary)) {
      try {
        if (OpenTelemetryTraceContext.isAvailable()) {
          return new OpenTelemetryTraceContext();
        }
      } catch (Exception e) {
        log.info("[Sentry] OpenTelemetry SDK 不可用, 降级到 DefaultTraceContext");
      }
    }
    return new DefaultTraceContext();
  }

  /**
   * 装配慢链路检测器，对超过阈值的调用打点并附带 traceId 便于反查。
   *
   * @param metricsCollector 慢链路计数写出目标
   * @param traceContext 用于提取当前 traceId
   * @param properties 监控配置
   * @return 慢链路检测器
   */
  @Bean
  @ConditionalOnMissingBean(SlowTraceDetector.class)
  public SlowTraceDetector slowTraceDetector(
      MetricsCollector metricsCollector, TraceContext traceContext, SentryProperties properties) {
    return new SlowTraceDetector(
        metricsCollector, traceContext, properties.getTracing().getSlowTraceThresholdMillis());
  }
}
