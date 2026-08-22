package com.njydsz.common.sentry.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Sentry 配置属性
 *
 * <p>配置前缀：{@code ydsz.sentry}
 *
 * <p>各子配置通过 JSR-303 注解约束合法范围，启动时自动校验。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Validated
@ConfigurationProperties(prefix = "ydsz.sentry")
public class SentryProperties {

  /** 是否启用 Sentry */
  private boolean enabled = true;

  /** 应用名 */
  @NotBlank(message = "应用名不能为空")
  private String appName = "ydsz";

  /** 主机名（auto 自动探测） */
  private String hostname = "auto";

  /** 环境 */
  @NotBlank(message = "环境标识不能为空")
  private String profile = "dev";

  /** 指标配置 */
  @Valid
  @NotNull(message = "指标配置不能为空")
  private MetricsConfig metrics = new MetricsConfig();

  /** 日志配置 */
  @Valid
  @NotNull(message = "日志配置不能为空")
  private LoggingConfig logging = new LoggingConfig();

  /** 追踪配置 */
  @Valid
  @NotNull(message = "追踪配置不能为空")
  private TracingConfig tracing = new TracingConfig();

  /** 告警配置 */
  @Valid
  @NotNull(message = "告警配置不能为空")
  private AlertingConfig alerting = new AlertingConfig();

  /** SLA 配置 */
  @Valid
  @NotNull(message = "SLA 配置不能为空")
  private SlaConfig sla = new SlaConfig();

  /** 指标采集配置（主采集器与系统资源指标）。 */
  @Data
  @Validated
  public static class MetricsConfig {
    /** 主指标采集器：micrometer / memory */
    @NotBlank(message = "主指标采集器不能为空")
    private String primary = "micrometer";

    /** 是否启用系统资源指标采集 */
    private boolean enableSystemMetrics = true;

    /** 系统资源指标采集间隔（秒） */
    @Min(value = 1, message = "系统资源指标采集间隔不能小于 1 秒")
    @Max(value = 300, message = "系统资源指标采集间隔不能大于 300 秒")
    private int systemMetricsIntervalSeconds = 15;

    /** 熔断器配置 */
    @Valid
    @NotNull(message = "熔断器配置不能为空")
    private CircuitBreakerConfig circuitBreaker = new CircuitBreakerConfig();
  }

  /** 指标上报熔断配置（失败率阈值与恢复参数）。 */
  @Data
  @Validated
  public static class CircuitBreakerConfig {
    /** 是否启用熔断器 */
    private boolean enabled = true;

    /** 失败率阈值 */
    @Min(value = 0, message = "失败率阈值不能小于 0")
    @Max(value = 1, message = "失败率阈值不能大于 1")
    private double failureRateThreshold = 0.3;

    /** 滑动窗口大小 */
    @Min(value = 1, message = "滑动窗口大小不能小于 1")
    @Max(value = 1000, message = "滑动窗口大小不能大于 1000")
    private int slidingWindowSize = 100;

    /** 半开恢复时间（秒） */
    @Min(value = 1, message = "半开恢复时间不能小于 1 秒")
    @Max(value = 600, message = "半开恢复时间不能大于 600 秒")
    private int halfOpenAfterSeconds = 30;
  }

  /** 日志发布配置（选择 ELK / Loki / 双发方案）。 */
  @Data
  @Validated
  public static class LoggingConfig {
    /** 主日志方案：elk / loki / dual */
    @NotBlank(message = "主日志方案不能为空")
    private String primary = "loki";

    /** ELK 配置 */
    @Valid
    @NotNull(message = "ELK 配置不能为空")
    private ElkConfig elk = new ElkConfig();

    /** Loki 配置 */
    @Valid
    @NotNull(message = "Loki 配置不能为空")
    private LokiConfig loki = new LokiConfig();

    /** 双发配置 */
    @Valid
    @NotNull(message = "双发配置不能为空")
    private DualConfig dual = new DualConfig();

    /** 异步配置 */
    @Valid
    @NotNull(message = "异步配置不能为空")
    private AsyncConfig async = new AsyncConfig();
  }

  /** ELK（Logstash）日志发布器配置。 */
  @Data
  @Validated
  public static class ElkConfig {
    private boolean enabled = false;

    @NotBlank(message = "ELK 主机名不能为空")
    private String host = "logstash";

    @Min(value = 1, message = "ELK 端口必须在 1-65535 之间")
    @Max(value = 65535, message = "ELK 端口必须在 1-65535 之间")
    private int port = 5044;

    @NotBlank(message = "ELK 协议不能为空")
    private String protocol = "tcp";

    @Min(value = 100, message = "ELK 连接超时不能小于 100ms")
    @Max(value = 30000, message = "ELK 连接超时不能大于 30000ms")
    private int connectTimeoutMillis = 3000;

    @Min(value = 100, message = "ELK 读取超时不能小于 100ms")
    @Max(value = 60000, message = "ELK 读取超时不能大于 60000ms")
    private int readTimeoutMillis = 5000;

    @Min(value = 0, message = "ELK 重试次数不能小于 0")
    @Max(value = 10, message = "ELK 重试次数不能大于 10")
    private int maxRetryAttempts = 3;
  }

  /** Loki 日志发布器配置。 */
  @Data
  @Validated
  public static class LokiConfig {
    private boolean enabled = true;

    @NotBlank(message = "Loki URL 不能为空")
    private String url = "http://loki:3100";

    @Min(value = 1, message = "Loki 连接超时不能小于 1 秒")
    @Max(value = 60, message = "Loki 连接超时不能大于 60 秒")
    private int connectTimeoutSeconds = 5;

    @Min(value = 0, message = "Loki 重试次数不能小于 0")
    @Max(value = 10, message = "Loki 重试次数不能大于 10")
    private int maxRetryAttempts = 3;
  }

  /** 双发（ELK + Loki 同时发布）失败判定配置。 */
  @Data
  public static class DualConfig {
    /** 所有发布器都失败才算失败 */
    private boolean failOnAllError = false;
  }

  /** 异步日志发布配置（队列与批量刷新）。 */
  @Data
  @Validated
  public static class AsyncConfig {
    /** 是否启用异步日志发布 */
    private boolean enabled = true;

    /** 队列容量 */
    @Min(value = 64, message = "异步队列容量不能小于 64")
    @Max(value = 100000, message = "异步队列容量不能大于 100000")
    private int queueCapacity = 8192;

    /** 批量发送大小 */
    @Min(value = 1, message = "批量发送大小不能小于 1")
    @Max(value = 10000, message = "批量发送大小不能大于 10000")
    private int batchSize = 100;

    /** 刷新间隔（毫秒） */
    @Min(value = 100, message = "刷新间隔不能小于 100ms")
    @Max(value = 60000, message = "刷新间隔不能大于 60000ms")
    private long flushIntervalMillis = 1000;

    /** 令牌桶限流（每秒最大发送量，0 表示不限流） */
    @Min(value = 0, message = "限流速率不能小于 0")
    @Max(value = 100000, message = "限流速率不能大于 100000")
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

  /** 链路追踪配置（主追踪系统与慢请求阈值）。 */
  @Data
  @Validated
  public static class TracingConfig {
    /** 主追踪系统：skywalking / opentelemetry / default */
    @NotBlank(message = "主追踪系统不能为空")
    private String primary = "skywalking";

    /** 慢追踪阈值（毫秒） */
    @Min(value = 100, message = "慢追踪阈值不能小于 100ms")
    @Max(value = 60000, message = "慢追踪阈值不能大于 60000ms")
    private long slowTraceThresholdMillis = 3000;

    /** OpenTelemetry 完整配置 */
    @Valid
    @NotNull(message = "OTel 配置不能为空")
    private OtelConfig otel = new OtelConfig();
  }

  /** OpenTelemetry SDK 自动初始化配置（采样、资源属性、尾部采样等）。 */
  @Data
  @Validated
  public static class OtelConfig {
    /** 是否启用 OTel SDK 自动初始化 */
    private boolean enabled = false;

    /** 服务名（默认使用 sentry.appName） */
    private String serviceName;

    /** 服务版本（默认 1.0.0） */
    @NotBlank(message = "服务版本不能为空")
    private String serviceVersion = "1.0.0";

    /** 服务命名空间（业务域） */
    @NotBlank(message = "服务命名空间不能为空")
    private String serviceNamespace = "ydsz";

    /** 服务实例 ID（不填则随机生成雪花 ID） */
    private String serviceInstanceId;

    /** 采样器：always-on / always-off / ratio / parent-based / composite */
    @NotBlank(message = "采样器不能为空")
    private String sampler = "parent-based";

    /** 采样率（0.0 ~ 1.0） */
    @Min(value = 0, message = "采样率不能小于 0")
    @Max(value = 1, message = "采样率不能大于 1")
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
    @Valid
    @NotNull(message = "尾部采样配置不能为空")
    private TailSamplingConfig tailSampling = new TailSamplingConfig();

    /** 错误事件配置 */
    @Valid
    @NotNull(message = "错误事件配置不能为空")
    private ErrorEventConfig errorEvent = new ErrorEventConfig();

    /** 批处理配置 */
    @Valid
    @NotNull(message = "批处理配置不能为空")
    private BatchConfig batch = new BatchConfig();

    /** 资源自定义属性 */
    private Map<String, String> resourceAttributes = new HashMap<>();

    /** OTel 批量导出器配置（队列与调度参数）。 */
    @Data
    @Validated
    public static class BatchConfig {
      /** 队列大小 */
      @Min(value = 64, message = "OTel 队列大小不能小于 64")
      @Max(value = 10000, message = "OTel 队列大小不能大于 10000")
      private int maxQueueSize = 2048;

      /** 批量导出大小 */
      @Min(value = 1, message = "OTel 批量导出大小不能小于 1")
      @Max(value = 10000, message = "OTel 批量导出大小不能大于 10000")
      private int maxExportBatchSize = 512;

      /** 调度延迟（毫秒） */
      @Min(value = 100, message = "OTel 调度延迟不能小于 100ms")
      @Max(value = 60000, message = "OTel 调度延迟不能大于 60000ms")
      private long scheduleDelayMillis = 5000;

      /** 导出超时（毫秒） */
      @Min(value = 1000, message = "OTel 导出超时不能小于 1000ms")
      @Max(value = 300000, message = "OTel 导出超时不能大于 300000ms")
      private long exporterTimeoutMillis = 30000;
    }
  }

  /** 尾部采样配置（延迟到 Span 结束后的决策采样）。 */
  @Data
  @Validated
  public static class TailSamplingConfig {
    /** 是否启用尾部采样 */
    private boolean enabled = true;

    /** 总采样率（未命中规则时的概率采样） */
    @Min(value = 0, message = "尾部采样率不能小于 0")
    @Max(value = 1, message = "尾部采样率不能大于 1")
    private double recordRatio = 0.05;

    /** 是否 100% 采集错误 Span（HTTP 5xx / OTel StatusCode.ERROR） */
    private boolean errorStatus = true;

    /** 慢请求阈值（毫秒，>0 时 100% 采集超过该阈值的 Span） */
    @Min(value = 100, message = "慢请求阈值不能小于 100ms")
    @Max(value = 60000, message = "慢请求阈值不能大于 60000ms")
    private long slowThresholdMillis = 3000;

    /** 错误码前缀（命中前缀的 100% 采集） */
    private List<String> errorCodePrefixes = List.of("A0", "B0", "C0");

    /** 灰度标签列表（命中即 100% 采集） */
    private List<String> grayTags = List.of();

    /** 是否 100% 采集压测流量 */
    private boolean pressureTraffic = true;
  }

  /** 错误事件发布配置（慢 Span 阈值等）。 */
  @Data
  @Validated
  public static class ErrorEventConfig {
    /** 是否启用错误事件发布 */
    private boolean enabled = true;

    /** 慢 Span 阈值（毫秒） */
    @Min(value = 100, message = "慢 Span 阈值不能小于 100ms")
    @Max(value = 60000, message = "慢 Span 阈值不能大于 60000ms")
    private long slowThresholdMillis = 3000;
  }

  /** 告警配置（静默期与接收人）。 */
  @Data
  @Validated
  public static class AlertingConfig {
    private boolean enabled = true;

    /** 静默期（毫秒） */
    @Min(value = 1000, message = "告警静默期不能小于 1000ms")
    @Max(value = 3600000, message = "告警静默期不能大于 3600000ms")
    private long silencePeriodMillis = 300000;

    /** 是否记录告警日志 */
    private boolean logAlerts = true;

    /** 钉钉告警接收者 */
    private String dingtalkReceiver = "";

    /** 邮件告警接收者 */
    private String emailReceiver = "";
  }

  /** SLA 上报配置。 */
  @Data
  public static class SlaConfig {
    private boolean enabled = true;
  }
}
