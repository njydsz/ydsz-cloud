package com.njydsz.pmis.common.sentry.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * Sentry 配置属性
 *
 * <p>配置前缀：{@code ydsz.sentry}
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@Data
@ConfigurationProperties(prefix = "ydsz.sentry")
public class SentryProperties {

    /** 是否启用 Sentry */
    private boolean enabled = true;

    /** 应用名 */
    private String appName = "pmis";

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

    @Data
    public static class LokiConfig {
        private boolean enabled = true;
        private String url = "http://loki:3100";
        private int connectTimeoutSeconds = 5;
        private int maxRetryAttempts = 3;
        private int circuitBreakerThreshold = 10;
    }

    @Data
    public static class DualConfig {
        /** 所有发布器都失败才算失败 */
        private boolean failOnAllError = false;
    }

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
    }

    @Data
    public static class TracingConfig {
        /** 主追踪系统：skywalking / opentelemetry / default */
        private String primary = "skywalking";

        /** 慢追踪阈值（毫秒） */
        private long slowTraceThresholdMillis = 3000;
    }

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

    @Data
    public static class SlaConfig {
        private boolean enabled = true;
    }
}
