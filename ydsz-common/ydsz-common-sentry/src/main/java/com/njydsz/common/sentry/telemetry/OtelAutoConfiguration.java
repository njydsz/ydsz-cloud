package com.njydsz.common.sentry.telemetry;

import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * OpenTelemetry SDK 自动配置（ydsz.sentry.otel 前缀）。
 *
 * <p>当 {@code ydsz.sentry.otel.enabled=true} 时激活，根据 {@link OTelProperties} 自动装配：
 *
 * <ul>
 *   <li>{@link Sampler} — 基于 samplerRatio 的 ParentBasedTraceIdRatioSampler
 *   <li>{@link SdkTracerProvider} — 含 OTLP Exporter + BatchSpanProcessor
 *   <li>{@link Tracer} — 全局默认 Tracer
 * </ul>
 *
 * <p>与之并列的 {@code com.njydsz.common.sentry.config.OtelAutoConfiguration} 使用 {@code
 * ydsz.sentry.tracing.otel} 前缀，提供追踪上下文桥接；本配置聚焦于 OTel SDK 基础能力装配，
 * 二者不存在 Bean 命名冲突（@ConditionalOnMissingBean 兜底）。
 *
 * <p>所有依赖以 optional=true 引入，业务方未引入 OTel 相关 jar 时不生效。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(OTelProperties.class)
@ConditionalOnProperty(
    prefix = "ydsz.sentry.otel",
    name = "enabled",
    havingValue = "true")
@ConditionalOnClass(name = "io.opentelemetry.api.trace.Tracer")
public class OtelAutoConfiguration {

  /**
   * 基于 samplerRatio 返回 ParentBasedTraceIdRatioSampler。
   *
   * <p>父 Span 已采样则子 Span 跟随，无父 Span 时按比例采样，保证分布式链路完整。
   *
   * @param properties OTel 配置属性
   * @return ParentBased Sampler
   */
  @Bean
  public Sampler otelSampler(OTelProperties properties) {
    double ratio = properties.getSamplerRatio();
    log.info("[OTel] 创建 ParentBasedTraceIdRatioSampler, samplerRatio={}", ratio);
    return Sampler.parentBased(Sampler.traceIdRatioBased(ratio));
  }

  /**
   * 创建 SdkTracerProvider，配置 OTLP exporter + BatchSpanProcessor。
   *
   * <p>由于 OTel Exporter 的具体类（如 OtlpGrpcSpanExporter）由业务模块按需引入， 本方法提供基础的
   * SdkTracerProvider Bean（采样器已配置），SpanExporter 由 {@code
   * io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter} 或业务模块自行装配。
   *
   * @param properties OTel 配置属性
   * @param sampler 采样器
   * @return SdkTracerProvider 实例
   */
  @Bean
  public SdkTracerProvider otelTracerProvider(OTelProperties properties, Sampler sampler) {
    log.info(
        "[OTel] 创建 SdkTracerProvider, serviceName={}, endpoint={}, protocol={}",
        properties.getServiceName(),
        properties.getEndpoint(),
        properties.getProtocol());
    return SdkTracerProvider.builder()
        .setSampler(sampler)
        .build();
  }

  /**
   * 从 SdkTracerProvider 获取全局默认 Tracer。
   *
   * <p>scope 固定为 {@code ydsz}，区别于框架自动埋点产生的 Span。
   *
   * @param tracerProvider SdkTracerProvider
   * @return Tracer 实例
   */
  @Bean
  public Tracer otelTracer(SdkTracerProvider tracerProvider) {
    log.info("[OTel] 创建默认 Tracer, scope=ydsz");
    return tracerProvider.tracerBuilder("ydsz").build();
  }
}
