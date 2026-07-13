package com.njydsz.pmis.common.sentry.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * Sentry 配置属性
 *
 * <p>配置前缀：{@code ydsz.sentry}
 *
 * <pre>
 * ydsz:
 *   sentry:
 *     enabled: true
 *     app-name: pmis-service
 *     hostname: auto
 *     profile: ${spring.profiles.active:dev}
 *     metrics:
 *       primary: micrometer          # micrometer / memory
 *       enable-system-metrics: true
 *       system-metrics-interval: 15s
 *       circuit-breaker:
 *         enabled: true
 *         failure-rate-threshold: 0.3
 *         sliding-window-size: 100
 *         half-open-after-seconds: 30
 *     logging:
 *       primary: elk                 # elk / loki / dual
 *       elk:
 *         enabled: true
 *         host: logstash
 *         port: 5044
 *         protocol: tcp             # tcp / udp
 *         connect-timeout-millis: 3000
 *         read-timeout-millis: 5000
 *         max-retry-attempts: 3
 *         circuit-breaker-threshold: 10
 *       loki:
 *         enabled: true
 *         url: http://loki:3100
 *         connect-timeout-seconds: 5
 *         max-retry-attempts: 3
 *         circuit-breaker-threshold: 10
 *       dual:
 *         fail-on-all-error: false
 *     tracing:
 *       primary: skywalking          # skywalking / default
 *       slow-trace-threshold-millis: 3000
 *     alerting:
 *       enabled: true
 *       silence-period-millis: 300000
 *       log-alerts: true
 *     sla:
 *       enabled: true
 * </pre>
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
    public static class TracingConfig {
        /** 主追踪系统：skywalking / default */
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
    }

    @Data
    public static class SlaConfig {
        private boolean enabled = true;
    }
}
