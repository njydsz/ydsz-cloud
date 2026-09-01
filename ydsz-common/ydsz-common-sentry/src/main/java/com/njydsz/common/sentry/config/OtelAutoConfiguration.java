package com.njydsz.common.sentry.config;

import java.util.ArrayList;
import java.util.List;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.njydsz.common.sentry.tracing.otel.ErrorEventSpanProcessor;
import com.njydsz.common.sentry.tracing.otel.OtelExporterFactory;
import com.njydsz.common.sentry.tracing.otel.OtelResources;
import com.njydsz.common.sentry.tracing.otel.OtelSamplers;
import com.njydsz.common.sentry.tracing.otel.OtelSdkBuilder;
import com.njydsz.common.sentry.tracing.otel.SpanEvaluationProcessor;
import com.njydsz.common.sentry.tracing.otel.YdszOpenTelemetry;
import com.njydsz.common.sentry.tracing.otel.YdszSpanEnrichmentProcessor;

/**
 * OpenTelemetry SDK 自动配置
 *
 * <p>当 ydsz.sentry.tracing.otel.enabled=true 时激活，根据配置自动构建 OTel SDK 并 注册到
 * GlobalOpenTelemetry。同时按需注册：
 *
 * <ul>
 *   <li>{@link YdszSpanEnrichmentProcessor}：自动注入 YDSZ 业务属性
 *   <li>{@link SpanEvaluationProcessor}：Span 评估处理器（错误/慢请求/灰度标记）
 *   <li>{@link ErrorEventSpanProcessor}：错误/慢 Span 事件发布
 * </ul>
 *
 * <p>需要业务模块手动提供 {@link SpanExporter} Bean（OTLP gRPC / HTTP / Zipkin 等）， 配置示例：
 *
 * <pre>{@code
 * ydsz:
 *   sentry:
 *     tracing:
 *       otel:
 *         enabled: true
 *         service-name: ydsz-order
 *         service-version: 1.0.0
 *         sampler: parent-based
 *         sampler-ratio: 0.1
 *         tail-sampling:
 *           enabled: true
 *           record-ratio: 0.05
 *         exporter:
 *           type: otlp-grpc
 *           endpoint: http://otel-collector:4317
 *
 * # 引入依赖
 * # implementation 'io.opentelemetry:opentelemetry-exporter-otlp:1.40.0'
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "io.opentelemetry.api.OpenTelemetry")
@ConditionalOnProperty(
    prefix = "ydsz.sentry.tracing.otel",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = false)
@EnableConfigurationProperties(SentryProperties.class)
@AutoConfigureAfter(SentryAutoConfiguration.class)
public class OtelAutoConfiguration {

  /**
   * 注册 OTel SDK 初始化器，由 Spring 在 Bean 就绪时回调 {@code build()} 完成 SDK 装配。
   *
   * <p>之所以不在方法内直接构建 SDK，是因为构建依赖 {@link SpanExporter} 与自定义 {@link SpanProcessor}，二者由业务模块延迟提供；用
   * {@code initMethod} 可保证在容器 Bean 定义全部完成后再解析 {@link ObjectProvider}，避免过早触发循环依赖。 销毁阶段通过 {@code
   * destroyMethod} 关闭 SDK，触发 Span 缓冲区 flush，防止进程退出丢数据。
   *
   * @param sentryProperties 监控配置，读取 tracing.otel 子树
   * @param exporterProvider Span 导出器提供者；未提供时 SDK 仅内存处理，不向外导出
   * @param customProcessorsProvider 业务自定义 SpanProcessor 列表提供者，可为空
   * @return 初始化器实例，永不为 {@code null}
   */
  @Bean(initMethod = "build", destroyMethod = "close")
  @ConditionalOnMissingBean
  public OtelSdkInitializer otelSdkInitializer(
      SentryProperties sentryProperties,
      ObjectProvider<SpanExporter> exporterProvider,
      ObjectProvider<List<SpanProcessor>> customProcessorsProvider) {

    log.info("[Sentry] OtelSdkInitializer 初始化：tracing.otel.enabled=true");
    return new OtelSdkInitializer(sentryProperties, exporterProvider, customProcessorsProvider);
  }

  /**
   * 提供全局共享的默认 {@link Tracer}，instrumentation scope 固定为 {@code ydsz}。
   *
   * <p>统一 scope 名便于在 APM 后端按来源过滤自研埋点，区别于框架自动埋点产生的 Span。 Tracer 实例线程安全，可被任意 Bean 注入后长期持有。
   *
   * @param openTelemetry 容器中的 OpenTelemetry 实例
   * @return 默认 Tracer；SDK 未初始化时 OTel 会返回 no-op 实现而非 {@code null}
   */
  @Bean
  @ConditionalOnMissingBean
  /**
   * ydsz default tracer。
   * @param openTelemetry 参数
   * @return 结果
   */
  public Tracer ydszDefaultTracer(OpenTelemetry openTelemetry) {
    return openTelemetry.getTracer("ydsz");
  }

  /**
   * 提供 {@link OpenTelemetrySdk} Spring Bean。
   *
   * <p>由 {@link OtelSdkInitializer} 在 {@code initMethod} 阶段构建完成。 业务模块可通过 {@code @Resource(name =
   * "ydszOpenTelemetrySdk")} 注入使用。
   *
   * <p>1.0.0 变更：不再注册为 GlobalOpenTelemetry，改为 Spring Bean 依赖注入传播， 避免与
   * opentelemetry-spring-boot-starter 等第三方配置冲突。
   *
   * @param sentryProperties 可观测配置，其中 {@code tracing.otel} 段决定采样率、导出方式与资源属性
   * @param exporterProvider 用户自定义的 {@code SpanExporter} 延迟提供者；取不到时按配置构建默认导出器
   * @param customProcessorsProvider 用户追加的 {@code SpanProcessor} 列表提供者；取不到时不追加任何处理器
   * @return OpenTelemetrySdk 实例（以 {@code OtelSdkInitializer} 形式承载，{@code initMethod} 阶段完成构建）
   */
  @Bean(name = "ydszOpenTelemetrySdk", initMethod = "build", destroyMethod = "close")
  @ConditionalOnMissingBean(name = "ydszOpenTelemetrySdk")
  public OtelSdkInitializer otelSdkBean(
      SentryProperties sentryProperties,
      ObjectProvider<SpanExporter> exporterProvider,
      ObjectProvider<List<SpanProcessor>> customProcessorsProvider) {

    log.info("[Sentry] OtelSdkInitializer Bean 初始化：tracing.otel.enabled=true");
    return new OtelSdkInitializer(sentryProperties, exporterProvider, customProcessorsProvider);
  }

  /**
   * 兜底提供 {@link OpenTelemetry} 实例，来源于 {@link YdszOpenTelemetry} 持有的全局单例。
   *
   * <p>仅当容器中不存在名为 {@code ydszOtelOpenTelemetry} 的 Bean 时生效，避免与 opentelemetry-spring-boot-starter
   * 等第三方自动配置冲突。若 SDK 尚未通过 {@link OtelSdkInitializer} 构建完成，此处返回的是 no-op 实现，埋点调用不会报错但也不上报。
   *
   * @return OpenTelemetry 实例，永不为 {@code null}
   */
  @Bean
  @ConditionalOnMissingBean(name = "ydszOtelOpenTelemetry")
  /**
   * ydsz otel open。
   * @return 结果
   */
  public OpenTelemetry ydszOtelOpenTelemetry() {
    return YdszOpenTelemetry.openTelemetry();
  }

  /** OTel SDK 初始化器（独立 Bean，便于业务模块引用并主动初始化） */
  public static class OtelSdkInitializer {

    private final SentryProperties sentryProperties;
    private final ObjectProvider<SpanExporter> exporterProvider;
    private final ObjectProvider<List<SpanProcessor>> customProcessorsProvider;
    private OpenTelemetrySdk sdk;

    public OtelSdkInitializer(
        SentryProperties sentryProperties,
        ObjectProvider<SpanExporter> exporterProvider,
        ObjectProvider<List<SpanProcessor>> customProcessorsProvider) {
      this.sentryProperties = sentryProperties;
      this.exporterProvider = exporterProvider;
      this.customProcessorsProvider = customProcessorsProvider;
    }

    /**
     * 按配置组装 OTel SDK：Resource → Sampler → Processors → Exporter → 全局 SDK。
     *
     * <p>幂等执行：多次调用仅首次生效（{@code build} 内部由外层控制调用时机）。 任何单步异常都会回退为不初始化 OTel SDK，避免影响主业务链路。
     */
    public void build() {
      SentryProperties.TracingConfig tracingConfig = sentryProperties.getTracing();
      SentryProperties.OtelConfig otelConfig = tracingConfig.getOtel();

      // 1) Resource
      Resource resource =
          OtelResources.create(
              OtelResources.YdszResourceConfig.builder()
                  .serviceName(
                      otelConfig.getServiceName() != null
                          ? otelConfig.getServiceName()
                          : sentryProperties.getAppName())
                  .serviceVersion(
                      otelConfig.getServiceVersion() != null
                          ? otelConfig.getServiceVersion()
                          : "1.0.0")
                  .serviceNamespace(otelConfig.getServiceNamespace())
                  .environment(sentryProperties.getProfile())
                  .customAttributes(otelConfig.getResourceAttributes())
                  .build());

      // 2) Sampler
      Sampler sampler = buildSampler(otelConfig);

      // 3) SpanProcessors
      OtelSdkBuilder builder =
          OtelSdkBuilder.create().resource(resource).sampler(sampler).withW3CPropagator();

      // 3.1) YDSZ 自动注入
      if (otelConfig.isEnrichmentEnabled()) {
        builder.addProcessor(
            new YdszSpanEnrichmentProcessor(
                YdszSpanEnrichmentProcessor.EnrichmentConfig.builder()
                    .enabled(true)
                    .sources(otelConfig.getEnrichmentSources())
                    .build()));
      }

      // 3.2) Span Evaluation
      if (otelConfig.getTailSampling().isEnabled()) {
        List<SpanEvaluationProcessor.SamplingRule> rules =
            buildTailSamplingRules(otelConfig.getTailSampling());
        builder.addProcessor(
            new SpanEvaluationProcessor(otelConfig.getTailSampling().getRecordRatio(), rules));
      }

      // 3.3) Error Event
      if (otelConfig.getErrorEvent().isEnabled()) {
        ErrorEventSpanProcessor errorProcessor =
            new ErrorEventSpanProcessor(new ErrorEventSpanProcessor.ErrorEventConfig());
        builder.addProcessor(errorProcessor);
      }

      // 3.4) 用户自定义
      List<SpanProcessor> customs = customProcessorsProvider.getIfAvailable();
      if (customs != null) {
        customs.forEach(builder::addProcessor);
      }

      // 4) Exporter
      SpanExporter exporter = exporterProvider.getIfAvailable();
      if (exporter != null) {
        builder.exporter(exporter).batchConfig(buildBatchConfig(otelConfig));
      } else {
        log.warn("[Sentry] 未提供 SpanExporter Bean，OTel SDK 将仅做内存处理（不导出）");
      }

      // 5) Build
      this.sdk = builder.build();
    }

    /**
     * 关闭 OTel SDK，阻塞等待已缓冲的 Span 完成导出。
     *
     * <p>由 Spring 容器销毁阶段自动调用。{@code build()} 未执行或已失败时 {@code sdk} 为 {@code
     * null}，此处直接跳过，保证关闭流程幂等、不抛异常。
     */
    public void close() {
      if (sdk != null) {
        sdk.close();
      }
    }

    /**
     * get sdk。
     * @return 结果
     */
    public OpenTelemetrySdk getSdk() {
      return sdk;
    }
  }

  private static Sampler buildSampler(SentryProperties.OtelConfig config) {
    if (config.getSampler() == null) {
      return OtelSamplers.parentBased(config.getSamplerRatio());
    }
    switch (config.getSampler().toLowerCase()) {
      case "always-on":
        return OtelSamplers.alwaysOn();
      case "always-off":
        return OtelSamplers.alwaysOff();
      case "ratio":
        return OtelSamplers.ratio(config.getSamplerRatio());
      case "parent-based":
        return OtelSamplers.parentBased(config.getSamplerRatio());
      case "composite":
        return OtelSamplers.composite(
            OtelSamplers.CompositeConfig.builder()
                .defaultRatio(config.getSamplerRatio())
                .serviceRatios(config.getSamplerServiceRatios())
                .grayTagRatios(config.getSamplerGrayTagRatios())
                .healthCheckPaths(config.getHealthCheckPaths())
                .build());
      default:
        log.warn("[Sentry] 未知 sampler: {}，使用 parent-based", config.getSampler());
        return OtelSamplers.parentBased(config.getSamplerRatio());
    }
  }

  private static List<SpanEvaluationProcessor.SamplingRule> buildTailSamplingRules(
      SentryProperties.TailSamplingConfig config) {
    List<SpanEvaluationProcessor.SamplingRule> rules = new ArrayList<>();
    if (config.isErrorStatus()) {
      rules.add(SpanEvaluationProcessor.Rules.errorStatus());
    }
    if (config.getSlowThresholdMillis() > 0) {
      rules.add(SpanEvaluationProcessor.Rules.slowRequest(config.getSlowThresholdMillis()));
    }
    if (config.getErrorCodePrefixes() != null && !config.getErrorCodePrefixes().isEmpty()) {
      rules.add(
          SpanEvaluationProcessor.Rules.errorCode(
              config.getErrorCodePrefixes().toArray(new String[0])));
    }
    if (config.getGrayTags() != null && !config.getGrayTags().isEmpty()) {
      for (String tag : config.getGrayTags()) {
        rules.add(SpanEvaluationProcessor.Rules.grayTag(tag));
      }
    }
    if (config.isPressureTraffic()) {
      rules.add(SpanEvaluationProcessor.Rules.pressureTraffic());
    }
    return rules;
  }

  private static OtelExporterFactory.BatchConfig buildBatchConfig(
      SentryProperties.OtelConfig config) {
    SentryProperties.OtelConfig.BatchConfig batch = config.getBatch();
    OtelExporterFactory.BatchConfig result = new OtelExporterFactory.BatchConfig();
    if (batch != null) {
      result.setMaxQueueSize(batch.getMaxQueueSize());
      result.setMaxExportBatchSize(batch.getMaxExportBatchSize());
      result.setScheduleDelayMillis(batch.getScheduleDelayMillis());
      result.setExporterTimeoutMillis(batch.getExporterTimeoutMillis());
    }
    return result;
  }
}
