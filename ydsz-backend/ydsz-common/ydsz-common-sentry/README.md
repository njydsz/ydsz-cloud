# ydsz-common-sentry

统一系统指标监控服务，对标互联网大厂标准，同时支持两套监控方案快速接入。

## 核心特性

- **异步日志上报**：有界队列 + 批量聚合 + 令牌桶限流，业务线程零阻塞（`AsyncLogPublisher`）
- **双方案日志上报**：同时支持 ELK+Logstash（TCP 长连接/UDP）和 Loki+Alloy（HTTP），可配置主备切换
- **统一指标抽象**：封装 Counter/Gauge/Timer/Histogram，Micrometer 不可用时自动降级为内存计数器，Timer/Histogram 内置 SLO 分桶
- **系统资源指标**：自动采集 CPU/内存/磁盘/网络/进程/GC 指标
- **SLA 框架**：`@SlaMetric` 注解 + AOP 自动采集业务关键路径 SLA
- **链路追踪增强**：SkyWalking / OpenTelemetry 自动接入 + TraceId 贯穿日志 + 慢追踪告警
- **告警收敛降噪**：时间窗口聚合 + 去重 + 静默期管理 + 对接 common-notify 钉钉/邮件通知
- **降级保护**：统一 CircuitBreaker 熔断器（AtomicReference+CAS 线程安全）+ 自动降级策略
- **自监控指标**：定时上报各组件可用性指标到 Prometheus，支持告警规则自监控
- **健康检查**：Sentry 组件健康 + 系统资源健康 + 子发布器状态详情

## 架构设计

```
ydsz-common-sentry/
├── domain/           # 领域模型（LogEvent/AlertEvent/SlaDefinition 等）
├── spi/              # SPI 接口（MetricsCollector/LogPublisher/TraceContext/AlertPublisher/SlaCollector）
├── metrics/          # 指标采集（Micrometer + InMemory 降级 + SystemMetrics）
├── logging/          # 日志发布（AsyncLogPublisher + ELK + Loki + DualLogPublisher + LogbackLayout）
├── tracing/          # 链路追踪（SkyWalking + OpenTelemetry + Default + SlowTraceDetector）
├── resilience/       # 降级保护（CircuitBreaker 熔断器，AtomicReference+CAS 线程安全）
├── alerting/         # 告警收敛（AlertConverger + DefaultAlertPublisher + NotifyAlertHandler）
├── sla/              # SLA 框架（@SlaMetric 注解 + AOP 切面 + DefaultSlaCollector）
├── health/           # 健康检查（SentryHealthIndicator + SystemResourceHealthIndicator）
└── config/           # 自动配置（SentryProperties + SentryAutoConfiguration + SentrySelfMonitor）
```

## 快速接入

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-sentry</artifactId>
</dependency>
```

### 2. 配置 application.yml

```yaml
ydsz:
  sentry:
    enabled: true
    app-name: ydsz-project
    profile: ${spring.profiles.active:dev}

    # 指标采集
    metrics:
      primary: micrometer              # micrometer / memory
      enable-system-metrics: true
      system-metrics-interval-seconds: 15

    # 日志方案（方案一: ELK / 方案二: Loki / 双发: dual）
    logging:
      primary: dual
      elk:
        enabled: true
        host: logstash
        port: 5044
        protocol: tcp
      loki:
        enabled: true
        url: http://loki:3100

    # 链路追踪
    tracing:
      primary: skywalking              # skywalking / default
      slow-trace-threshold-millis: 3000

    # 告警
    alerting:
      enabled: true
      silence-period-millis: 300000

    # SLA
    sla:
      enabled: true
```

### 3. 使用 SLA 注解

```java
@SlaMetric(name = "project_creation", description = "项目创建 SLA",
           thresholdMillis = 500, slaTarget = 0.99)
public Long createProject(ProjectCreateDTO dto) {
    // 自动采集执行耗时，超过 500ms 记录 SLA 违反
    return projectService.create(dto);
}
```

### 4. 使用 MetricsCollector

```java
@Autowired
private MetricsCollector metricsCollector;

public void doSearch(String keyword) {
    long start = System.currentTimeMillis();
    try {
        // 业务逻辑
        metricsCollector.incrementCounter("ydsz.search.requests",
                "搜索请求", Map.of("keyword", keyword));
    } finally {
        metricsCollector.recordTimer("ydsz.search.duration",
                "搜索耗时", Map.of("keyword", keyword),
                Duration.ofMillis(System.currentTimeMillis() - start));
    }
}
```

### 5. Logback 配置

```xml
<appender name="SENTRY_JSON" class="ch.qos.logback.core.ConsoleAppender">
    <layout class="com.njydsz.common.sentry.logging.SentryLogbackLayout">
        <appName>ydsz-service</appName>
        <profile>${spring.profiles.active:-dev}</profile>
    </layout>
</appender>
```

## 双方案对比

| 维度 | 方案一: ELK+Logstash | 方案二: Loki+Alloy |
|-----|---------------------|-------------------|
| **存储** | Elasticsearch（全文索引） | Loki（标签索引） |
| **查询** | Kibana KQL/Lucene | LogQL |
| **部署** | 较重（3 组件） | 轻量（2 组件） |
| **全文搜索** | ✅ 强 | ⚠️ 弱（仅标签） |
| **资源消耗** | 高（ES JVM） | 低（Go 实现） |
| **适用场景** | 需要全文搜索 | 仅需标签过滤 |

## 指标清单

### 系统资源指标

| 指标名 | 类型 | 说明 |
|-------|------|------|
| `ydsz.system.cpu.usage` | Gauge | 进程 CPU 使用率 |
| `ydsz.system.cpu.system_usage` | Gauge | 系统 CPU 使用率 |
| `ydsz.system.cpu.load_avg` | Gauge | CPU 平均负载 |
| `ydsz.system.memory.heap.used` | Gauge | 堆内存已用（bytes） |
| `ydsz.system.memory.heap.max` | Gauge | 堆内存最大（bytes） |
| `ydsz.system.disk.free` | Gauge | 磁盘可用空间（bytes） |
| `ydsz.system.process.uptime` | Gauge | 进程运行时长（秒） |
| `ydsz.system.process.thread_count` | Gauge | 线程数 |
| `ydsz.system.process.gc.count` | Gauge | GC 总次数 |
| `ydsz.system.process.gc.time` | Gauge | GC 总耗时（毫秒） |

### SLA 指标

| 指标名 | 类型 | 说明 |
|-------|------|------|
| `ydsz.sla.total.duration` | Timer | SLA 总耗时 |
| `ydsz.sla.total.count` | Counter | SLA 执行总次数 |
| `ydsz.sla.total.failed` | Counter | SLA 失败次数 |
| `ydsz.sla.violation` | Counter | SLA 违反次数 |
| `ydsz.sla.step.duration` | Timer | SLA 步骤耗时 |
| `ydsz.sla.step.timeout` | Counter | SLA 步骤超时次数 |

### 追踪指标

| 指标名 | 类型 | 说明 |
|-------|------|------|
| `ydsz.trace.duration` | Timer | 链路追踪耗时 |
| `ydsz.trace.slow` | Counter | 慢追踪次数 |

## 部署配置

### ELK 方案

```bash
# 启动 ELK Stack
docker-compose -f deploy/monitoring/elk/docker-compose-elk.yml up -d
```

### Loki + Alloy 方案

```bash
# Alloy 替代 Promtail
alloy run deploy/monitoring/loki/alloy-config.alloy
```

## 版本

- **since**: 1.0.0
- **author**: ydsz-team
