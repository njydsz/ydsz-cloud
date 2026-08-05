# remi-common-sentry

> 错误监控与可观测性模块（L5 业务服务层）— 指标 / 日志 / 链路追踪 / SLA / 告警统一抽象

统一可观测性抽象，封装指标采集（Micrometer / 内存降级）、日志发布（ELK / Loki / 双发 + 异步有界队列 + 令牌桶限流）、链路追踪（SkyWalking / OpenTelemetry / MDC 降级）、SLA 框架（`@SlaMetric` 注解 + AOP）、告警收敛（时间窗口 + 去重 + 静默期 + IM 通知）、熔断降级保护，是所有业务模块可观测性的统一基座。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L5 业务服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 提供统一指标采集、双方案日志发布、链路追踪、SLA 框架、告警收敛、熔断降级能力 |
| **依赖** | common-core、common-util、common-exception、common-json；可选依赖 common-notify、spring-context、spring-aop、aspectjweaver、spring-web、spring-boot-autoconfigure、spring-boot-actuator、spring-boot-health、micrometer-core、logback-classic、apm-toolkit-trace、opentelemetry-api/sdk/sdk-trace/semconv |
| **版本** | 1.0.0 |

## 核心能力

### 1. 指标采集

| 类 | 说明 |
|---|---|
| `MetricsCollector` | 指标采集 SPI，定义 `incrementCounter` / `setGauge` / `recordTimer` / `recordHistogram` |
| `MicrometerMetricsCollector` | Micrometer 实现（优先），MeterRegistry 不可用时降级 |
| `InMemoryMetricsCollector` | 内存计数器实现（降级），Micrometer 不可用时自动启用 |
| `SystemMetricsCollector` | 系统资源指标采集（CPU / 内存 / 磁盘 / 网络 / 进程 / GC），定时调度 |

### 2. 日志发布

| 类 | 说明 |
|---|---|
| `LogPublisher` | 日志发布 SPI，定义 `publish(LogEvent)` / `publishBatch(List<LogEvent>)` / `isAvailable()` / `getScheme()` |
| `ElkLogPublisher` | ELK + Logstash 实现，TCP 长连接 / UDP 推送 |
| `LokiLogPublisher` | Loki HTTP 推送实现 |
| `DualLogPublisher` | 双发模式（ELK + Loki 同时推送），`failOnAllError` 控制所有失败才算失败 |
| `AsyncLogPublisher` | 异步发布包装器，有界队列 + 批量发送 + 令牌桶限流 + 优雅关闭；队列满时丢弃最旧日志（背压降级） |
| `LogEventSerializer` | 日志事件序列化器（JSON） |
| `SentryLogbackLayout` | Logback 自定义 Layout，输出结构化 JSON 日志（含 appName / profile / traceId） |

### 3. 链路追踪

| 类 | 说明 |
|---|---|
| `TraceContext` | 追踪上下文 SPI，定义 `getTraceId` / `getSpanId` / `getSegmentId` / `isTracing` / `tag` / `getTracerName` |
| `SkyWalkingTraceContext` | SkyWalking 实现（优先），基于 `apm-toolkit-trace` |
| `OpenTelemetryTraceContext` | OpenTelemetry 实现 |
| `DefaultTraceContext` | 默认 MDC 实现（降级） |
| `SlowTraceDetector` | 慢追踪检测与告警，超过 `slow-trace-threshold-millis` 触发 |

#### OpenTelemetry 增强（`tracing.otel`）

| 类 | 说明 |
|---|---|
| `RemiOpenTelemetry` | OTel SDK 入口 |
| `OtelSdkBuilder` | SDK 构建器 |
| `OtelResources` | Resource 属性构建（service.name / namespace / instance.id / version） |
| `OtelSamplers` | 采样器工厂（always-on / always-off / ratio / parent-based / composite） |
| `OtelExporterFactory` | Exporter 工厂 |
| `OtelSemConv` | 语义约定常量 |
| `RemiSpan` | Span 包装器 |
| `RemiSpanEnrichmentProcessor` | Span 属性自动注入（MDC / RequestContext / env） |
| `ErrorEventSpanProcessor` | 错误事件 Span 处理器 |
| `TailSamplingSpanProcessor` | 尾部采样处理器（错误 100% / 慢请求 100% / 灰度标签 100% / 压测 100% / 其他按 ratio） |

### 4. SLA 框架

| 类 | 说明 |
|---|---|
| `SlaCollector` | SLA 采集 SPI，定义 `register(SlaDefinition)` / `record(name, step, tookMillis, success)` / `recordTotal` |
| `DefaultSlaCollector` | 默认实现，结合 `MetricsCollector` 上报指标 |
| `SlaMetric` | 方法注解（`name` / `description` / `thresholdMillis` / `slaTarget` / `evaluationWindowSeconds`） |
| `SlaMetricAspect` | AOP 切面，拦截 `@SlaMetric` 方法，自动采集执行耗时并判断 SLA 违反 |
| `SlaStep` | SLA 步骤定义 |
| `SlaDefinition` | SLA 定义实体 |
| `SlaMetric`（指标） | SLA 相关 Micrometer 指标 |

### 5. 告警收敛

| 类 | 说明 |
|---|---|
| `AlertPublisher` | 告警发布 SPI，定义 `publish(AlertEvent)` / `publishBatch` |
| `DefaultAlertPublisher` | 默认实现，集成 `AlertConverger` 收敛后发布 |
| `AlertConverger` | 告警收敛器：时间窗口聚合 + 去重 + 静默期管理（`silence-period-millis`） |
| `NotifyAlertHandler` | 告警 → IM 通知桥接，对接 common-notify（钉钉 / 邮件） |
| `AlertEvent` | 告警事件实体 |
| `AlertSeverity` | 告警级别枚举 |

### 6. 熔断降级

| 类 | 说明 |
|---|---|
| `CircuitBreaker` | 统一熔断器，AtomicReference + CAS 线程安全；状态 CLOSED → OPEN → HALF_OPEN → CLOSED |

状态机：

| 状态 | 行为 |
|---|---|
| `CLOSED` | 正常状态，统计滑动窗口失败率，超过阈值切换到 OPEN |
| `OPEN` | 熔断状态，请求直接走 fallback；超过 `halfOpenAfterSeconds` 切换到 HALF_OPEN |
| `HALF_OPEN` | 半开状态，AtomicInteger 保证仅单个探测请求通过；探测成功 → CLOSED，失败 → OPEN |

### 7. 领域模型

| 类 | 说明 |
|---|---|
| `LogEvent` | 日志事件（timestamp / level / logger / message / threadName / traceId / MDC / 异常堆栈） |
| `LogLevel` | 日志级别枚举 |
| `AlertEvent` | 告警事件（severity / source / message / timestamp / tags） |
| `AlertSeverity` | 告警级别枚举 |
| `SlaDefinition` | SLA 定义 |

### 8. 配置与自动装配

| 类 | 说明 |
|---|---|
| `SentryAutoConfiguration` | Spring Boot 自动配置，`remi.sentry.enabled=true`（默认）时装配；`@EnableScheduling` 启用定时任务；内嵌 `MicrometerMetricsConfiguration` 等子配置 |
| `OtelAutoConfiguration` | OpenTelemetry 自动配置 |
| `SentryProperties` | 配置属性（`remi.sentry.*`） |

## 接入方式

### 1. POM 引入依赖

```xml
<dependency>
    <groupId>com.remisoft</groupId>
    <artifactId>remi-common-sentry</artifactId>
</dependency>
```

如需 IM 告警通知，额外引入：

```xml
<dependency>
    <groupId>com.remisoft</groupId>
    <artifactId>remi-common-notify</artifactId>
</dependency>
```

### 2. 配置启用

```yaml
remi:
  sentry:
    enabled: true
    app-name: remi-project
    profile: ${spring.profiles.active:dev}
    metrics:
      primary: micrometer
      enable-system-metrics: true
    logging:
      primary: dual          # elk / loki / dual
      elk:
        enabled: true
        host: logstash
        port: 5044
        protocol: tcp
      loki:
        enabled: true
        url: http://loki:3100
    tracing:
      primary: skywalking    # skywalking / opentelemetry / default
      slow-trace-threshold-millis: 3000
    alerting:
      enabled: true
      silence-period-millis: 300000
    sla:
      enabled: true
```

### 3. 注入 MetricsCollector 使用

```java
import com.remisoft.common.sentry.spi.MetricsCollector;
import java.time.Duration;
import java.util.Map;

@Service
public class SearchService {
    private final MetricsCollector metricsCollector;

    public void doSearch(String keyword) {
        long start = System.currentTimeMillis();
        try {
            // 业务逻辑
            metricsCollector.incrementCounter("remi.search.requests",
                    "搜索请求", Map.of("keyword", keyword));
        } finally {
            metricsCollector.recordTimer("remi.search.duration",
                    "搜索耗时", Map.of("keyword", keyword),
                    Duration.ofMillis(System.currentTimeMillis() - start));
        }
    }
}
```

## 配置项

| 配置 | 默认值 | 说明 |
|---|---|---|
| `remi.sentry.enabled` | true | 是否启用 Sentry 模块 |
| `remi.sentry.app-name` | `remi` | 应用名 |
| `remi.sentry.hostname` | `auto` | 主机名（auto 自动探测） |
| `remi.sentry.profile` | `dev` | 环境标识 |
| `remi.sentry.metrics.primary` | `micrometer` | 主指标采集器（micrometer / memory） |
| `remi.sentry.metrics.enable-system-metrics` | true | 是否启用系统资源指标采集 |
| `remi.sentry.metrics.system-metrics-interval-seconds` | 15 | 系统资源指标采集间隔（秒） |
| `remi.sentry.metrics.circuit-breaker.enabled` | true | 是否启用指标采集熔断器 |
| `remi.sentry.metrics.circuit-breaker.failure-rate-threshold` | 0.3 | 失败率阈值 |
| `remi.sentry.metrics.circuit-breaker.sliding-window-size` | 100 | 滑动窗口大小 |
| `remi.sentry.metrics.circuit-breaker.half-open-after-seconds` | 30 | 半开恢复时间（秒） |
| `remi.sentry.logging.primary` | `loki` | 主日志方案（elk / loki / dual） |
| `remi.sentry.logging.elk.enabled` | false | 是否启用 ELK |
| `remi.sentry.logging.elk.host` | `logstash` | Logstash 主机 |
| `remi.sentry.logging.elk.port` | 5044 | Logstash 端口 |
| `remi.sentry.logging.elk.protocol` | `tcp` | 协议（tcp / udp） |
| `remi.sentry.logging.elk.connect-timeout-millis` | 3000 | 连接超时 |
| `remi.sentry.logging.elk.read-timeout-millis` | 5000 | 读超时 |
| `remi.sentry.logging.elk.max-retry-attempts` | 3 | 最大重试次数 |
| `remi.sentry.logging.elk.circuit-breaker-threshold` | 10 | 熔断阈值 |
| `remi.sentry.logging.loki.enabled` | true | 是否启用 Loki |
| `remi.sentry.logging.loki.url` | `http://loki:3100` | Loki URL |
| `remi.sentry.logging.loki.connect-timeout-seconds` | 5 | 连接超时 |
| `remi.sentry.logging.loki.max-retry-attempts` | 3 | 最大重试次数 |
| `remi.sentry.logging.loki.circuit-breaker-threshold` | 10 | 熔断阈值 |
| `remi.sentry.logging.dual.fail-on-all-error` | false | 所有发布器都失败才算失败 |
| `remi.sentry.logging.async.enabled` | true | 是否启用异步发布 |
| `remi.sentry.logging.async.queue-capacity` | 8192 | 队列容量 |
| `remi.sentry.logging.async.batch-size` | 100 | 批量发送大小 |
| `remi.sentry.logging.async.flush-interval-millis` | 1000 | 刷新间隔（毫秒） |
| `remi.sentry.logging.async.max-rate-per-second` | 0 | 令牌桶限流（0=不限流） |
| `remi.sentry.tracing.primary` | `skywalking` | 主追踪系统（skywalking / opentelemetry / default） |
| `remi.sentry.tracing.slow-trace-threshold-millis` | 3000 | 慢追踪阈值（毫秒） |
| `remi.sentry.tracing.otel.enabled` | false | 是否启用 OTel SDK 自动初始化 |
| `remi.sentry.tracing.otel.service-name` | - | 服务名（默认使用 sentry.appName） |
| `remi.sentry.tracing.otel.service-version` | `1.0.0` | 服务版本 |
| `remi.sentry.tracing.otel.service-namespace` | `remi` | 服务命名空间 |
| `remi.sentry.tracing.otel.service-instance-id` | - | 服务实例 ID（不填则随机生成雪花 ID） |
| `remi.sentry.tracing.otel.sampler` | `parent-based` | 采样器（always-on / always-off / ratio / parent-based / composite） |
| `remi.sentry.tracing.otel.sampler-ratio` | 0.1 | 采样率（0.0 ~ 1.0） |
| `remi.sentry.tracing.otel.sampler-service-ratios` | - | 服务级采样率覆盖（service name -> ratio） |
| `remi.sentry.tracing.otel.sampler-gray-tag-ratios` | - | 灰度标签采样率（gray tag -> ratio） |
| `remi.sentry.tracing.otel.health-check-paths` | `[/actuator, /health, /metrics]` | 健康检查路径前缀（不采样） |
| `remi.sentry.tracing.otel.enrichment-enabled` | true | 是否启用 Span 属性自动注入 |
| `remi.sentry.tracing.otel.enrichment-sources` | `[mdc]` | 自动注入来源列表 |
| `remi.sentry.tracing.otel.tail-sampling.enabled` | true | 是否启用尾部采样 |
| `remi.sentry.tracing.otel.tail-sampling.record-ratio` | 0.05 | 总采样率（未命中规则时） |
| `remi.sentry.tracing.otel.tail-sampling.error-status` | true | 是否 100% 采集错误 Span |
| `remi.sentry.tracing.otel.tail-sampling.slow-threshold-millis` | 3000 | 慢请求阈值（毫秒，>0 时 100% 采集） |
| `remi.sentry.tracing.otel.tail-sampling.error-code-prefixes` | `[A0, B0, C0]` | 错误码前缀（命中前缀的 100% 采集） |
| `remi.sentry.tracing.otel.tail-sampling.gray-tags` | - | 灰度标签列表（命中即 100% 采集） |
| `remi.sentry.tracing.otel.tail-sampling.pressure-traffic` | true | 是否 100% 采集压测流量 |
| `remi.sentry.tracing.otel.error-event.enabled` | true | 是否启用错误事件发布 |
| `remi.sentry.tracing.otel.error-event.slow-threshold-millis` | 3000 | 慢 Span 阈值（毫秒） |
| `remi.sentry.tracing.otel.batch.max-queue-size` | 2048 | 队列大小 |
| `remi.sentry.tracing.otel.batch.max-export-batch-size` | 512 | 批量导出大小 |
| `remi.sentry.tracing.otel.batch.schedule-delay-millis` | 5000 | 调度延迟（毫秒） |
| `remi.sentry.tracing.otel.batch.exporter-timeout-millis` | 30000 | 导出超时（毫秒） |
| `remi.sentry.tracing.otel.resource-attributes` | - | 资源自定义属性 |
| `remi.sentry.alerting.enabled` | true | 是否启用告警 |
| `remi.sentry.alerting.silence-period-millis` | 300000 | 静默期（毫秒） |
| `remi.sentry.alerting.log-alerts` | true | 是否记录告警日志 |
| `remi.sentry.alerting.dingtalk-receiver` | - | 钉钉告警接收者 |
| `remi.sentry.alerting.email-receiver` | - | 邮件告警接收者 |
| `remi.sentry.sla.enabled` | true | 是否启用 SLA 框架 |

## 使用示例

### 1. SLA 注解驱动

```java
import com.remisoft.common.sentry.sla.SlaMetric;

@Service
public class ProjectService {

    @SlaMetric(name = "project_creation", description = "项目创建 SLA",
               thresholdMillis = 500, slaTarget = 0.99)
    public Long createProject(ProjectCreateDTO dto) {
        // 自动采集执行耗时，超过 500ms 记录 SLA 违反
        return projectMapper.insert(dto);
    }
}
```

### 2. 自定义告警发布

```java
import com.remisoft.common.sentry.spi.AlertPublisher;
import com.remisoft.common.sentry.domain.AlertEvent;
import com.remisoft.common.sentry.domain.AlertSeverity;

@Service
public class MonitorService {
    private final AlertPublisher alertPublisher;

    public void alertOnHighError() {
        AlertEvent event = AlertEvent.builder()
                .severity(AlertSeverity.CRITICAL)
                .source("order-service")
                .message("订单错误率超过阈值 5%")
                .build();
        alertPublisher.publish(event);  // 经 AlertConverger 收敛后发布
    }
}
```

### 3. Logback 结构化日志配置

```xml
<appender name="SENTRY_JSON" class="ch.qos.logback.core.ConsoleAppender">
    <layout class="com.remisoft.common.sentry.logging.SentryLogbackLayout">
        <appName>remi-service</appName>
        <profile>${spring.profiles.active:-dev}</profile>
    </layout>
</appender>
```

### 4. OpenTelemetry 尾部采样配置

```yaml
remi:
  sentry:
    tracing:
      primary: opentelemetry
      otel:
        enabled: true
        service-name: remi-order
        sampler: parent-based
        sampler-ratio: 0.1
        tail-sampling:
          enabled: true
          record-ratio: 0.05
          error-status: true        # 错误 100% 采集
          slow-threshold-millis: 3000  # 慢请求 100% 采集
          gray-tags: [gray-canary]    # 灰度标签 100% 采集
          pressure-traffic: true       # 压测流量 100% 采集
```

### 5. 双方案日志发布

```yaml
remi:
  sentry:
    logging:
      primary: dual          # ELK + Loki 双发
      elk:
        enabled: true
        host: logstash
        port: 5044
      loki:
        enabled: true
        url: http://loki:3100
      dual:
        fail-on-all-error: false   # 所有发布器都失败才算失败
      async:
        enabled: true
        queue-capacity: 8192
        batch-size: 100
        max-rate-per-second: 1000  # 令牌桶限流，防止日志风暴
```

## SPI 扩展点

| SPI 接口 | 用途 | 实现方 |
|---|---|---|
| `MetricsCollector` | 统一指标采集（Counter / Gauge / Timer / Histogram） | 框架内置 `MicrometerMetricsCollector`、`InMemoryMetricsCollector`；业务可扩展 |
| `LogPublisher` | 日志发布（单条 / 批量） | 框架内置 `ElkLogPublisher`、`LokiLogPublisher`、`DualLogPublisher`、`AsyncLogPublisher`；业务可扩展 |
| `TraceContext` | 链路追踪上下文（TraceId / SpanId / tag） | 框架内置 `SkyWalkingTraceContext`、`OpenTelemetryTraceContext`、`DefaultTraceContext`；业务可扩展 |
| `AlertPublisher` | 告警发布（经收敛后发布） | 框架内置 `DefaultAlertPublisher`；业务可扩展 |
| `SlaCollector` | SLA 指标采集 | 框架内置 `DefaultSlaCollector`；业务可扩展 |

## 健康检查

| 端点 | 说明 | 触发条件 |
|---|---|---|
| `/actuator/health/sentry` | Sentry 模块整体健康检查 | `spring-boot-health` 在 classpath 且 `remi.sentry.enabled=true` |
| `/actuator/health/system` | 系统资源健康检查 | `SystemMetricsCollector` Bean 存在 |

`SentryHealthIndicator` 暴露信息：

| 字段 | 说明 |
|---|---|
| `metrics.collector` | 指标采集器名称 |
| `metrics.available` | 指标采集器是否可用 |
| `logging.publisher` | 日志发布器名称 |
| `logging.scheme` | 日志发布器协议方案 |
| `logging.available` | 日志发布器是否可用 |
| `logging.subPublishers` | DualLogPublisher 子发布器健康摘要（仅 dual 模式） |
| `logging.queueSize` | AsyncLogPublisher 当前队列大小（仅 async 模式） |
| `logging.droppedCount` | AsyncLogPublisher 累计丢弃日志数 |
| `logging.totalPublished` | AsyncLogPublisher 累计发布日志数 |
| `tracing.tracer` | 链路追踪系统名称 |
| `tracing.tracing` | 当前是否在追踪链路中 |

状态判定规则：

- 任一组件 `isAvailable()=false` → DOWN
- 其他情况 → UP

`SystemResourceHealthIndicator` 暴露信息：

| 字段 | 说明 |
|---|---|
| `memory.max` | 堆内存最大（bytes） |
| `memory.used` | 堆内存已用（bytes） |
| `memory.free` | 堆内存空闲（bytes） |
| `memory.usage` | 内存使用率（百分比） |
| `processors` | 可用处理器数 |
| `systemMetricsCollector` | 采集器状态（active / inactive） |

状态判定规则：内存使用率 > 90% → DOWN，否则 UP。

## 注意事项

1. **Micrometer 可选**：`MicrometerMetricsCollector` 需 classpath 中存在 `MeterRegistry`，不可用时自动降级为 `InMemoryMetricsCollector`，业务无感知但指标不持久化。
2. **异步日志队列满降级**：`AsyncLogPublisher` 队列满时丢弃最旧日志（背压降级），通过 `logging.droppedCount` 监控丢弃量；生产环境应适当增大 `queue-capacity`。
3. **令牌桶限流**：`max-rate-per-second > 0` 时启用令牌桶限流，防止日志风暴打爆下游；设为 0 表示不限流。
4. **告警静默期**：`silence-period-millis` 控制相同告警的最小间隔，避免告警风暴；收敛由 `AlertConverger` 实现（时间窗口 + 去重）。
5. **OTel SDK 默认不启用**：`remi.sentry.tracing.otel.enabled=false`（默认），仅当显式启用时才初始化 OTel SDK；未启用时仅注册 `OpenTelemetryTraceContext` 但不导出 Span。
6. **尾部采样需 OTel SDK**：`tail-sampling.enabled=true` 依赖 `OtelSdkBuilder` 初始化的 `TailSamplingSpanProcessor`，未启用 OTel SDK 时尾部采样配置无效。
7. **NotifyAlertHandler 可选**：`NotifyAlertHandler` 需 classpath 中存在 `common-notify` 的 `NotifyService`，未引入时告警仅记录日志不发送 IM 通知。
8. **SlaMetricAspect 需 AOP**：`@SlaMetric` 注解需 classpath 中存在 AspectJ Weaver，未引入时注解不生效。
9. **CircuitBreaker CAS 安全**：HALF_OPEN 状态下使用 AtomicInteger 保证仅单个探测请求通过，避免并发探测导致状态混乱。

## 变更记录

- **v1.0.0**（2026-08-02）：对标 common-jdbc 标准格式重构 README，补全全部 9 个章节
