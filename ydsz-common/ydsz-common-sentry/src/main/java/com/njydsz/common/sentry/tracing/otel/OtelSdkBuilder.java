package com.njydsz.common.sentry.tracing.otel;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import lombok.extern.slf4j.Slf4j;

/**
 * YDSZ OpenTelemetry SDK 构建器
 *
 * <p>统一封装 OTel SDK 的初始化流程，包括：
 *
 * <ol>
 *   <li>Resource（服务元信息）
 *   <li>Sampler（采样策略）
 *   <li>SpanProcessor（Tail Sampling + Enrichment + Error Event + 用户自定义）
 *   <li>SpanExporter（OTLP / Zipkin / Jaeger）
 *   <li>ContextPropagator（W3C TraceContext + Baggage）
 * </ol>
 *
 * <p>典型用法：
 *
 * <pre>{@code
 * OpenTelemetrySdk sdk = OtelSdkBuilder.create()
 *     .resource(OtelResources.createDefault("ydsz-order"))
 *     .sampler(OtelSamplers.parentBased(0.1))
 *     .addProcessor(new SpanEvaluationProcessor(0.05, samplingRules))
 *     .addProcessor(new YdszSpanEnrichmentProcessor(...))
 *     .exporter(otlpExporter)
 *     .build();
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public final class OtelSdkBuilder {

  private Resource resource;
  private Sampler sampler;
  private final List<SpanProcessor> processors = new ArrayList<>();
  private SpanExporter exporter;
  private OtelExporterFactory.BatchConfig batchConfig = OtelExporterFactory.BatchConfig.defaults();
  private TextMapPropagator[] propagators;

  private OtelSdkBuilder() {}

  /**
   * 创建 SDK 构建器实例
   *
   * @return 新的 OtelSdkBuilder 实例
   */
  public static OtelSdkBuilder create() {
    return new OtelSdkBuilder();
  }

  /**
   * 设置 Resource（服务元信息）
   *
   * @param resource OTel Resource
   * @return 当前构建器
   */
  public OtelSdkBuilder resource(Resource resource) {
    this.resource = resource;
    return this;
  }

  /**
   * 设置采样器
   *
   * @param sampler OTel Sampler
   * @return 当前构建器
   */
  public OtelSdkBuilder sampler(Sampler sampler) {
    this.sampler = sampler;
    return this;
  }

  /**
   * 添加 SpanProcessor
   *
   * @param processor SpanProcessor 实例
   * @return 当前构建器
   */
  public OtelSdkBuilder addProcessor(SpanProcessor processor) {
    if (processor != null) {
      this.processors.add(processor);
    }
    return this;
  }

  /**
   * 设置 SpanExporter
   *
   * @param exporter SpanExporter 实例
   * @return 当前构建器
   */
  public OtelSdkBuilder exporter(SpanExporter exporter) {
    this.exporter = exporter;
    return this;
  }

  /**
   * 设置批处理配置
   *
   * @param batchConfig 批处理配置
   * @return 当前构建器
   */
  public OtelSdkBuilder batchConfig(OtelExporterFactory.BatchConfig batchConfig) {
    if (batchConfig != null) {
      this.batchConfig = batchConfig;
    }
    return this;
  }

  /** 设置 W3C TraceContext + Baggage 传播器（默认） */
  public OtelSdkBuilder withW3CPropagator() {
    this.propagators =
        new TextMapPropagator[] {
          W3CTraceContextPropagator.getInstance(), W3CBaggagePropagator.getInstance()
        };
    return this;
  }

  /** 设置自定义传播器 */
  public OtelSdkBuilder propagators(TextMapPropagator... propagators) {
    this.propagators = propagators;
    return this;
  }

  /**
   * 构建 OTel SDK 并注册到 GlobalOpenTelemetry
   *
   * @return 构建完成的 OpenTelemetrySdk 实例
   */
  public OpenTelemetrySdk build() {
    // 1) TracerProvider
    SdkTracerProviderBuilder tracerProviderBuilder = SdkTracerProvider.builder();

    if (resource != null) {
      tracerProviderBuilder.setResource(resource);
    }
    if (sampler != null) {
      tracerProviderBuilder.setSampler(sampler);
    }

    // 2) SpanProcessor
    for (SpanProcessor processor : processors) {
      tracerProviderBuilder.addSpanProcessor(processor);
    }

    // 3) Exporter
    if (exporter != null) {
      SpanProcessor batchProcessor = OtelExporterFactory.batchProcessor(exporter, batchConfig);
      tracerProviderBuilder.addSpanProcessor(batchProcessor);
    }

    SdkTracerProvider tracerProvider = tracerProviderBuilder.build();

    // 4) Propagators
    TextMapPropagator combinedPropagator;
    if (propagators == null || propagators.length == 0) {
      combinedPropagator =
          TextMapPropagator.composite(
              W3CTraceContextPropagator.getInstance(), W3CBaggagePropagator.getInstance());
    } else if (propagators.length == 1) {
      combinedPropagator = propagators[0];
    } else {
      combinedPropagator = TextMapPropagator.composite(propagators);
    }

    // 5) Build SDK
    OpenTelemetrySdk sdk =
        OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setPropagators(ContextPropagators.create(combinedPropagator))
            .build();

    // 6) 不再注册为 GlobalOpenTelemetry（避免与 opentelemetry-spring-boot-starter 等第三方配置冲突）
    // SDK 由 OtelAutoConfiguration 作为 Spring Bean 管理，通过依赖注入传播
    try {
      OpenTelemetry existing = GlobalOpenTelemetry.get();
      if (existing != null && existing != OpenTelemetry.noop()) {
        log.info(
            "[Sentry] GlobalOpenTelemetry 已被占用（{}），YDSZ SDK 仅作为 Spring Bean 注册，不覆盖全局。",
            existing.getClass().getSimpleName());
      }
    } catch (Exception e) {
      log.debug("[Sentry] GlobalOpenTelemetry 检测异常：{}", e.getMessage());
    }

    log.info(
        "[Sentry] OTel SDK 构建完成：resource={}, sampler={}, processors={}, hasExporter={}",
        resource != null ? resource.getAttributes().get(OtelSemConv.SERVICE_NAME) : "default",
        sampler != null ? sampler.getDescription() : "default",
        processors.size(),
        exporter != null);

    return sdk;
  }

  /**
   * 通过 SPI 自动发现 SpanExporter
   *
   * @param type Exporter 类型标识（如 otlp-grpc、zipkin 等）
   * @return 当前构建器
   * @see OtelExporterFactory.SpanExporterProvider
   */
  public OtelSdkBuilder exporterFromSpi(String type) {
    ServiceLoader<OtelExporterFactory.SpanExporterProvider> loader =
        ServiceLoader.load(OtelExporterFactory.SpanExporterProvider.class);
    for (OtelExporterFactory.SpanExporterProvider provider : loader) {
      if (type.equalsIgnoreCase(provider.type())) {
        log.info("[Sentry] 通过 SPI 找到 SpanExporterProvider: {}", type);
        this.exporter = provider.create(new OtelExporterFactory.ExporterEndpointConfig());
        return this;
      }
    }
    log.warn("[Sentry] 未通过 SPI 找到 SpanExporterProvider: {}", type);
    return this;
  }
}
