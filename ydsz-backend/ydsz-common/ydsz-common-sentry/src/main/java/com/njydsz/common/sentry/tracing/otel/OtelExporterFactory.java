package com.njydsz.common.sentry.tracing.otel;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessorBuilder;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * OTLP SpanExporter 工厂
 *
 * <p><b>注意</b>：本类不直接 import OTLP Exporter 类，避免在缺少 otel-exporter-* 依赖时无法启动。
 * Exporter 实例由上层模块（已添加 otel-exporter-otlp / otel-exporter-zipkin / otel-exporter-jaeger 依赖）
 * 通过 {@link SpanExporterProvider} SPI 注入；本工厂负责包装成 {@link BatchSpanProcessor} 或
 * {@link SimpleSpanProcessor}。
 *
 * <p>典型集成方式：
 * <pre>{@code
 * @Bean
 * public SpanExporter otlpSpanExporter() {
 *     return OtlpGrpcSpanExporter.builder()
 *             .setEndpoint("http://otel-collector:4317")
 *             .build();
 * }
 *
 * @Bean
 * public SpanProcessor otlpSpanProcessor(SpanExporter exporter) {
 *     return OtelExporterFactory.batchProcessor(exporter, OtelExporterFactory.BatchConfig.defaults());
 * }
 * }</pre>
 *
 * <p>同时支持 Zipkin / Jaeger / Logging / 任意自定义 Exporter。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public final class OtelExporterFactory {

    private OtelExporterFactory() {
        throw new UnsupportedOperationException("OtelExporterFactory is a utility class");
    }

    /**
     * 创建 Batch SpanProcessor（生产推荐）
     *
     * @param exporter  底层 Exporter
     * @param config    批处理配置
     */
    public static SpanProcessor batchProcessor(SpanExporter exporter, BatchConfig config) {
        if (exporter == null) {
            throw new IllegalArgumentException("exporter 不能为 null");
        }
        BatchSpanProcessorBuilder builder = BatchSpanProcessor.builder(exporter);

        if (config.getMaxQueueSize() > 0) {
            builder.setMaxQueueSize(config.getMaxQueueSize());
        }
        if (config.getMaxExportBatchSize() > 0) {
            builder.setMaxExportBatchSize(config.getMaxExportBatchSize());
        }
        if (config.getScheduleDelayMillis() > 0) {
            builder.setScheduleDelay(Duration.ofMillis(config.getScheduleDelayMillis()));
        }
        if (config.getExporterTimeoutMillis() > 0) {
            builder.setExporterTimeout(Duration.ofMillis(config.getExporterTimeoutMillis()));
        }

        SpanProcessor processor = builder.build();
        log.info("[Sentry] BatchSpanProcessor 创建完成：queue={}, batch={}, delay={}ms",
                config.getMaxQueueSize(), config.getMaxExportBatchSize(),
                config.getScheduleDelayMillis());
        return processor;
    }

    /**
     * 创建 Simple SpanProcessor（开发/调试用）
     */
    public static SpanProcessor simpleProcessor(SpanExporter exporter) {
        if (exporter == null) {
            throw new IllegalArgumentException("exporter 不能为 null");
        }
        log.info("[Sentry] SimpleSpanProcessor 创建完成（开发模式）");
        return SimpleSpanProcessor.create(exporter);
    }

    // ============================================================================
    // 配置
    // ============================================================================

    /**
     * 批处理配置
     */
    @Data
    public static class BatchConfig {
        /** 队列大小 */
        private int maxQueueSize = 2048;
        /** 批量导出大小 */
        private int maxExportBatchSize = 512;
        /** 调度延迟（毫秒） */
        private long scheduleDelayMillis = 5000;
        /** 单次导出超时（毫秒） */
        private long exporterTimeoutMillis = 30000;

        public static BatchConfig defaults() {
            return new BatchConfig();
        }

        public static BatchConfig highThroughput() {
            BatchConfig c = new BatchConfig();
            c.setMaxQueueSize(8192);
            c.setMaxExportBatchSize(2048);
            c.setScheduleDelayMillis(2000);
            return c;
        }

        public static BatchConfig lowLatency() {
            BatchConfig c = new BatchConfig();
            c.setMaxQueueSize(512);
            c.setMaxExportBatchSize(64);
            c.setScheduleDelayMillis(500);
            return c;
        }
    }

    // ============================================================================
    // SPI 接口
    // ============================================================================

    /**
     * SpanExporter 提供者 SPI
     * <p>业务模块实现此接口，注入到 {@link OtelAutoConfiguration} 中
     */
    @FunctionalInterface
    public interface SpanExporterProvider {
        /**
         * 返回 Exporter 类型标识（用于配置选择）
         */
        default String type() {
            return "custom";
        }

        /**
         * 创建 Exporter
         */
        SpanExporter create(ExporterEndpointConfig config);
    }

    /**
     * Exporter 端点配置
     */
    @Data
    public static class ExporterEndpointConfig {
        /** Exporter 类型：otlp-grpc / otlp-http / zipkin / jaeger / logging */
        private String type = "otlp-grpc";
        /** 端点 URL */
        private String endpoint = "http://localhost:4317";
        /** 协议头（gRPC = http://, http = http://） */
        private String protocol = "grpc";
        /** 是否启用 */
        private boolean enabled = true;
        /** 请求头（如 Authorization） */
        private Map<String, String> headers = new HashMap<>();
        /** 超时（毫秒） */
        private long timeoutMillis = 10000;
        /** 压缩：gzip / none */
        private String compression = "gzip";
        /** TLS 配置 */
        private TlsConfig tls = new TlsConfig();

        @Data
        public static class TlsConfig {
            private boolean enabled = false;
            private String certFile;
            private String keyFile;
            private String caFile;
        }
    }
}
