package com.remisoft.common.sentry.tracing.otel;

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
 * @author remi-team
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

        /**
         * 通用默认档：队列 2048、批量 512、5s 调度。
         *
         * <p>适用于绝大多数中低流量服务，在导出频次与内存占用之间取平衡。
         *
         * @return 全新的配置实例，可安全修改后使用
         */
        public static BatchConfig defaults() {
            return new BatchConfig();
        }

        /**
         * 高吞吐档：队列 8192、批量 2048、2s 调度。
         *
         * <p>面向高 QPS 服务，通过放大队列降低 Span 因队列满而被丢弃的概率，
         * 代价是常驻内存更高（队列按 Span 对象计数，非字节数）。
         *
         * @return 全新的配置实例，可安全修改后使用
         */
        public static BatchConfig highThroughput() {
            BatchConfig c = new BatchConfig();
            c.setMaxQueueSize(8192);
            c.setMaxExportBatchSize(2048);
            c.setScheduleDelayMillis(2000);
            return c;
        }

        /**
         * 低延迟档：队列 512、批量 64、500ms 调度。
         *
         * <p>面向排障与灰度验证场景，让链路数据尽快可见；高流量下极易触发队列溢出丢 Span，
         * 且导出请求频次高，不建议在生产主链路长期开启。
         *
         * @return 全新的配置实例，可安全修改后使用
         */
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

        /**
         * OTLP/gRPC 传输 TLS 配置。
         */
        @Data
        public static class TlsConfig {
            /** 是否启用 TLS 加密传输 */
            private boolean enabled = false;
            /** 客户端证书文件路径 */
            private String certFile;
            /** 客户端私钥文件路径 */
            private String keyFile;
            /** CA 根证书文件路径 */
            private String caFile;
        }
    }
}
