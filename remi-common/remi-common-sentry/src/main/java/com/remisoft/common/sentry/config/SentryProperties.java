package com.remisoft.common.sentry.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sentry 配置属性
 *
 * <p>配置前缀：{@code remi.sentry}
 *
 * @author remi-team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "remi.sentry")
public class SentryProperties {

    /** 是否启用 Sentry */
    private boolean enabled = true;

    /** 应用名 */
    private String appName = "remi";

    /** 主机名（auto 自动探测） */
    private String hostname = "auto";

    /** 环境 */
    private String profile = "dev";

    /** 指标配置 */
    private MetricsConfig metrics = new MetricsConfig();

    /** 日志配置 */
    private LoggingConfig logging = new LoggingConfig();

    /** 追踪配置 */
    private TracingConfig tracing = new TracingConfig();

    /** 告警配置 */
    private AlertingConfig alerting = new AlertingConfig();

    /** SLA 配置 */
    private SlaConfig sla = new SlaConfig();

    /**
     * 指标采集配置（主采集器与系统资源指标）。
     */
    @Data
    public static class MetricsConfig {
        /** 主指标采集器：micrometer / memory */
        private String primary = "micrometer";

        /** 是否启用系统资源指标采集 */
        private boolean enableSystemMetrics = true;

        /** 系统资源指标采集间隔（秒） */
        private int systemMetricsIntervalSeconds = 15;

        /** 熔断器配置 */
        private CircuitBreakerConfig circuitBreaker = new CircuitBreakerConfig();
    }

    /**
     * 指标上报熔断配置（失败率阈值与恢复参数）。
     */
    @Data
    public static class CircuitBreakerConfig {
        /** 是否启用熔断器 */
        private boolean enabled = true;

        /** 失败率阈值 */
        private double failureRateThreshold = 0.3;

        /** 滑动窗口大小 */
        private int slidingWindowSize = 100;

        /** 半开恢复时间（秒） */
        private int halfOpenAfterSeconds = 30;
    }

    /**
     * 日志发布配置（选择 ELK / Loki / 双发方案）。
     */
    @Data
    public static class LoggingConfig {
        /** 主日志方案：elk / loki / dual */
        private String primary = "loki";

        /** ELK 配置 */
        private ElkConfig elk = new ElkConfig();

        /** Loki 配置 */
        private LokiConfig loki = new LokiConfig();

        /** 双发配置 */
        private DualConfig dual = new DualConfig();

        /** 异步配置 */
        private AsyncConfig async = new AsyncConfig();
    }

    /**
     * ELK（Logstash）日志发布器配置。
     */
    @Data
    public static class ElkConfig {
        private boolean enabled = false;
        private String host = "logstash";
        private int port = 5044;
        private String protocol = "tcp";
        private int connectTimeoutMillis = 3000;
        private int readTimeoutMillis = 5000;
        private int maxRetryAttempts = 3;
        private int circuitBreakerThreshold = 10;
    }

    /**
     * Loki 日志发布器配置。
     */
    @Data
    public static class LokiConfig {
        private boolean enabled = true;
        private String url = "http://loki:3100";
        private int connectTimeoutSeconds = 5;
        private int maxRetryAttempts = 3;
        private int circuitBreakerThreshold = 10;
    }

    /**
     * 双发（ELK + Loki 同时发布）失败判定配置。
     */
    @Data
    public static class DualConfig {
        /** 所有发布器都失败才算失败 */
        private boolean failOnAllError = false;
    }

    /**
     * 异步日志发布配置（队列与批量刷新）。
     */
    @Data
    public static class AsyncConfig {
        /** 是否启用异步日志发布 */
        private boolean enabled = true;

        /** 队列容量 */
        private int queueCapacity = 8192;

        /** 批量发送大小 */
        private int batchSize = 100;

        /** 刷新间隔（毫秒） */
        private long flushIntervalMillis = 1000;

        /** 令牌桶限流（每秒最大发送量，0 表示不限流） */
        private int maxRatePerSecond = 0;

        /**
         * 获取执行器队列容量（兼容命名）
         *
         * @return 队列容量
         */
        public int getExecutorQueueCapacity() {
            return queueCapacity;
        }
    }

    /**
     * 链路追踪配置（主追踪系统与慢请求阈值）。
     */
    @Data
    public static class TracingConfig {
        /** 主追踪系统：skywalking / opentelemetry / default */
        private String primary = "skywalking";

        /** 慢追踪阈值（毫秒） */
        private long slowTraceThresholdMillis = 3000;

        /** OpenTelemetry 完整配置 */
        private OtelConfig otel = new OtelConfig();
    }

    /**
     * OpenTelemetry SDK 自动初始化配置（采样、资源属性、尾部采样等）。
     */
    @Data
    public static class OtelConfig {
        /** 是否启用 OTel SDK 自动初始化 */
        private boolean enabled = false;

        /** 服务名（默认使用 sentry.appName） */
        private String serviceName;

        /** 服务版本（默认 1.0.0） */
        private String serviceVersion = "1.0.0";

        /** 服务命名空间（业务域） */
        private String serviceNamespace = "remi";

        /** 服务实例 ID（不填则随机生成雪花 ID） */
        private String serviceInstanceId;

        /** 采样器：always-on / always-off / ratio / parent-based / composite */
        private String sampler = "parent-based";

        /** 采样率（0.0 ~ 1.0） */
        private double samplerRatio = 0.1;

        /** 服务级采样率覆盖（service name -> ratio） */
        private Map<String, Double> samplerServiceRatios = new HashMap<>();

        /** 灰度标签采样率（gray tag -> ratio） */
        private Map<String, Double> samplerGrayTagRatios = new HashMap<>();

        /** 健康检查路径前缀（不采样） */
        private List<String> healthCheckPaths = List.of("/actuator", "/health", "/metrics");

        /** 是否启用 Span 属性自动注入（MDC/RequestContext/env） */
        private boolean enrichmentEnabled = true;

        /** 自动注入来源列表 */
        private List<String> enrichmentSources = List.of("mdc");

        /** 尾部采样配置 */
        private TailSamplingConfig tailSampling = new TailSamplingConfig();

        /** 错误事件配置 */
        private ErrorEventConfig errorEvent = new ErrorEventConfig();

        /** 批处理配置 */
        private BatchConfig batch = new BatchConfig();

        /** 资源自定义属性 */
        private Map<String, String> resourceAttributes = new HashMap<>();

        /**
         * OTel 批量导出器配置（队列与调度参数）。
         */
        @Data
        public static class BatchConfig {
            /** 队列大小 */
            private int maxQueueSize = 2048;

            /** 批量导出大小 */
            private int maxExportBatchSize = 512;

            /** 调度延迟（毫秒） */
            private long scheduleDelayMillis = 5000;

            /** 导出超时（毫秒） */
            private long exporterTimeoutMillis = 30000;
        }
    }

    /**
     * 尾部采样配置（延迟到 Span 结束后的决策采样）。
     */
    @Data
    public static class TailSamplingConfig {
        /** 是否启用尾部采样 */
        private boolean enabled = true;

        /** 总采样率（未命中规则时的概率采样） */
        private double recordRatio = 0.05;

        /** 是否 100% 采集错误 Span（HTTP 5xx / OTel StatusCode.ERROR） */
        private boolean errorStatus = true;

        /** 慢请求阈值（毫秒，>0 时 100% 采集超过该阈值的 Span） */
        private long slowThresholdMillis = 3000;

        /** 错误码前缀（命中前缀的 100% 采集） */
        private List<String> errorCodePrefixes = List.of("A0", "B0", "C0");

        /** 灰度标签列表（命中即 100% 采集） */
        private List<String> grayTags = List.of();

        /** 是否 100% 采集压测流量 */
        private boolean pressureTraffic = true;
    }

    /**
     * 错误事件发布配置（慢 Span 阈值等）。
     */
    @Data
    public static class ErrorEventConfig {
        /** 是否启用错误事件发布 */
        private boolean enabled = true;

        /** 慢 Span 阈值（毫秒） */
        private long slowThresholdMillis = 3000;
    }

    /**
     * 告警配置（静默期与接收人）。
     */
    @Data
    public static class AlertingConfig {
        private boolean enabled = true;

        /** 静默期（毫秒） */
        private long silencePeriodMillis = 300000;

        /** 是否记录告警日志 */
        private boolean logAlerts = true;

        /** 钉钉告警接收者 */
        private String dingtalkReceiver = "";

        /** 邮件告警接收者 */
        private String emailReceiver = "";
    }

    /**
     * SLA 上报配置。
     */
    @Data
    public static class SlaConfig {
        private boolean enabled = true;
    }
}
