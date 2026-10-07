package com.njydsz.common.sentry.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
 * <p>默认降级链路（primary=opentelemetry）：OpenTelemetry（需 SDK 可用）→ SkyWalking（需探针已挂载）→ {@link
 * DefaultTraceContext}（纯 MDC，仅本进程内 traceId 透传，无跨服务串联能力）。
 *
 * <p>SkyWalking 模式（primary=skywalking）：SkyWalking → OpenTelemetry → {@link DefaultTraceContext}。
 *
 * <p>Default 模式（primary=default）：直接使用纯 MDC 降级方案，不尝试 OTel 或 SkyWalking SDK。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter(MetricsAutoConfiguration.class)
@EnableConfigurationProperties(SentryProperties.class)
public class TracingAutoConfiguration {

  /**
   * 按 {@code tracing.primary} 选择链路上下文实现，并逐级降级。
   *
   * <p>降级策略：
   *
   * <ul>
   *   <li>opentelemetry 模式：OTel → SkyWalking → DefaultTraceContext
   *   <li>skywalking 模式：SkyWalking → OTel → DefaultTraceContext
   *   <li>default 模式：仅 DefaultTraceContext
   *   <li>其他值（兼容旧配置）：同 opentelemetry 模式
   * </ul>
   *
   * @param properties 监控配置
   * @return 链路上下文实现，永不为 {@code null}
   */
  @Bean
  @ConditionalOnMissingBean(TraceContext.class)
  public TraceContext traceContext(SentryProperties properties) {
    String primary = properties.getTracing().getPrimary();

    if ("default".equals(primary)) {
      log.info("[Sentry] tracing.primary=default，使用 DefaultTraceContext（纯 MDC 降级方案）");
      return new DefaultTraceContext();
    }

    // opentelemetry 模式（默认）：优先尝试 OTel
    if ("opentelemetry".equals(primary)) {
      TraceContext otel = tryOpenTelemetry();
      if (otel != null) {
        return otel;
      }
      TraceContext sw = trySkyWalking();
      if (sw != null) {
        return sw;
      }
      return new DefaultTraceContext();
    }

    // skywalking 模式：优先尝试 SkyWalking
    if ("skywalking".equals(primary)) {
      TraceContext sw = trySkyWalking();
      if (sw != null) {
        return sw;
      }
      TraceContext otel = tryOpenTelemetry();
      if (otel != null) {
        return otel;
      }
      return new DefaultTraceContext();
    }

    // 未知值：按 opentelemetry 模式处理
    log.warn("[Sentry] tracing.primary={} 不是预定义值，按 opentelemetry 模式处理", primary);
    TraceContext otel = tryOpenTelemetry();
    if (otel != null) {
      return otel;
    }
    TraceContext sw = trySkyWalking();
    if (sw != null) {
      return sw;
    }
    return new DefaultTraceContext();
  }

  /**
   * 尝试 OpenTelemetry SDK 是否可用
   *
   * @return OTel 可用返回 {@link OpenTelemetryTraceContext}，否则返回 {@code null}
   */
  private TraceContext tryOpenTelemetry() {
    try {
      if (OpenTelemetryTraceContext.isAvailable()) {
        return new OpenTelemetryTraceContext();
      }
    } catch (Exception e) {
      log.info("[Sentry] OpenTelemetry SDK 不可用, 跳过");
    }
    return null;
  }

  /** 尝试 SkyWalking agent 是否已挂载 */
  /**
   * try sky walking。
   * @return 结果
   */
  private TraceContext trySkyWalking() {
    try {
      Class.forName("org.apache.skywalking.apm.toolkit.trace.TraceContext");
      return new SkyWalkingTraceContext();
    } catch (ClassNotFoundException e) {
      log.info("[Sentry] SkyWalking agent 未检测到, 跳过");
    }
    return null;
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
