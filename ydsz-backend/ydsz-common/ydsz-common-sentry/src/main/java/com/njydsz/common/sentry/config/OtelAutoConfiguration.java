package com.njydsz.common.sentry.config;

import java.util.ArrayList;
import java.util.List;

import com.njydsz.common.sentry.tracing.otel.ErrorEventSpanProcessor;
import com.njydsz.common.sentry.tracing.otel.OtelExporterFactory;
import com.njydsz.common.sentry.tracing.otel.OtelResources;
import com.njydsz.common.sentry.tracing.otel.OtelSamplers;
import com.njydsz.common.sentry.tracing.otel.OtelSdkBuilder;
import com.njydsz.common.sentry.tracing.otel.TailSamplingSpanProcessor;
import com.njydsz.common.sentry.tracing.otel.YdszOpenTelemetry;
import com.njydsz.common.sentry.tracing.otel.YdszSpanEnrichmentProcessor;

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

/**
 * OpenTelemetry SDK 自动配置
 *
 * <p>当 ydsz.sentry.tracing.otel.enabled=true 时激活，根据配置自动构建 OTel SDK 并
 * 注册到 GlobalOpenTelemetry。同时按需注册：
 * <ul>
 *   <li>{@link YdszSpanEnrichmentProcessor}：自动注入 YDSZ 业务属性</li>
 *   <li>{@link TailSamplingSpanProcessor}：尾部采样（错误/慢请求/灰度100%采集）</li>
 *   <li>{@link ErrorEventSpanProcessor}：错误/慢 Span 事件发布</li>
 * </ul>
 *
 * <p>需要业务模块手动提供 {@link SpanExporter} Bean（OTLP gRPC / HTTP / Zipkin 等），
 * 配置示例：
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
@ConditionalOnProperty(prefix = "ydsz.sentry.tracing.otel", name = "enabled", havingValue = "true", matchIfMissing = false)
@EnableConfigurationProperties(SentryProperties.class)
@AutoConfigureAfter(SentryAutoConfiguration.class)
public class OtelAutoConfiguration {

    @Bean(initMethod = "build", destroyMethod = "close")
    @ConditionalOnMissingBean
    public OtelSdkInitializer otelSdkInitializer(
            SentryProperties sentryProperties,
            ObjectProvider<SpanExporter> exporterProvider,
            ObjectProvider<List<SpanProcessor>> customProcessorsProvider) {

        log.info("[Sentry] OtelSdkInitializer 初始化：tracing.otel.enabled=true");
        return new OtelSdkInitializer(sentryProperties, exporterProvider, customProcessorsProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    public Tracer ydszDefaultTracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer("ydsz");
    }

    @Bean
    @ConditionalOnMissingBean(name = "ydszOtelOpenTelemetry")
    public OpenTelemetry ydszOtelOpenTelemetry() {
        return YdszOpenTelemetry.openTelemetry();
    }

    /**
     * OTel SDK 初始化器（独立 Bean，便于业务模块引用并主动初始化）
     */
    public static class OtelSdkInitializer {

        private final SentryProperties sentryProperties;
        private final ObjectProvider<SpanExporter> exporterProvider;
        private final ObjectProvider<List<SpanProcessor>> customProcessorsProvider;
        private OpenTelemetrySdk sdk;

        public OtelSdkInitializer(SentryProperties sentryProperties,
                                  ObjectProvider<SpanExporter> exporterProvider,
                                  ObjectProvider<List<SpanProcessor>> customProcessorsProvider) {
            this.sentryProperties = sentryProperties;
            this.exporterProvider = exporterProvider;
            this.customProcessorsProvider = customProcessorsProvider;
        }

        public void build() {
            SentryProperties.TracingConfig tracingConfig = sentryProperties.getTracing();
            SentryProperties.OtelConfig otelConfig = tracingConfig.getOtel();

            // 1) Resource
            Resource resource = OtelResources.create(OtelResources.YdszResourceConfig.builder()
                    .serviceName(otelConfig.getServiceName() != null
                            ? otelConfig.getServiceName() : sentryProperties.getAppName())
                    .serviceVersion(otelConfig.getServiceVersion() != null
                            ? otelConfig.getServiceVersion() : "1.0.0")
                    .serviceNamespace(otelConfig.getServiceNamespace())
                    .environment(sentryProperties.getProfile())
                    .customAttributes(otelConfig.getResourceAttributes())
                    .build());

            // 2) Sampler
            Sampler sampler = buildSampler(otelConfig);

            // 3) SpanProcessors
            OtelSdkBuilder builder = OtelSdkBuilder.create()
                    .resource(resource)
                    .sampler(sampler)
                    .withW3CPropagator();

            // 3.1) YDSZ 自动注入
            if (otelConfig.isEnrichmentEnabled()) {
                builder.addProcessor(new YdszSpanEnrichmentProcessor(
                        YdszSpanEnrichmentProcessor.EnrichmentConfig.builder()
                                .enabled(true)
                                .sources(otelConfig.getEnrichmentSources())
                                .build()));
            }

            // 3.2) Tail Sampling
            if (otelConfig.getTailSampling().isEnabled()) {
                List<TailSamplingSpanProcessor.SamplingRule> rules = buildTailSamplingRules(
                        otelConfig.getTailSampling());
                builder.addProcessor(new TailSamplingSpanProcessor(
                        otelConfig.getTailSampling().getRecordRatio(), rules));
            }

            // 3.3) Error Event
            if (otelConfig.getErrorEvent().isEnabled()) {
                ErrorEventSpanProcessor errorProcessor = new ErrorEventSpanProcessor(
                        new ErrorEventSpanProcessor.ErrorEventConfig());
                errorProcessor.getClass(); // 引用以确保编译
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
                builder.exporter(exporter)
                        .batchConfig(buildBatchConfig(otelConfig));
            } else {
                log.warn("[Sentry] 未提供 SpanExporter Bean，OTel SDK 将仅做内存处理（不导出）");
            }

            // 5) Build
            this.sdk = builder.build();
        }

        public void close() {
            if (sdk != null) {
                sdk.close();
            }
        }

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
                return OtelSamplers.composite(OtelSamplers.CompositeConfig.builder()
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

    private static List<TailSamplingSpanProcessor.SamplingRule> buildTailSamplingRules(
            SentryProperties.TailSamplingConfig config) {
        List<TailSamplingSpanProcessor.SamplingRule> rules = new ArrayList<>();
        if (config.isErrorStatus()) {
            rules.add(TailSamplingSpanProcessor.Rules.errorStatus());
        }
        if (config.getSlowThresholdMillis() > 0) {
            rules.add(TailSamplingSpanProcessor.Rules.slowRequest(config.getSlowThresholdMillis()));
        }
        if (config.getErrorCodePrefixes() != null && !config.getErrorCodePrefixes().isEmpty()) {
            rules.add(TailSamplingSpanProcessor.Rules.errorCode(
                    config.getErrorCodePrefixes().toArray(new String[0])));
        }
        if (config.getGrayTags() != null && !config.getGrayTags().isEmpty()) {
            for (String tag : config.getGrayTags()) {
                rules.add(TailSamplingSpanProcessor.Rules.grayTag(tag));
            }
        }
        if (config.isPressureTraffic()) {
            rules.add(TailSamplingSpanProcessor.Rules.pressureTraffic());
        }
        return rules;
    }

    private static OtelExporterFactory.BatchConfig buildBatchConfig(
            SentryProperties.OtelConfig config) {
        SentryProperties.OtelConfig.BatchConfig batch =
                config.getBatch();
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
