# 分布式链路追踪端到端打通方案

> 基于 OpenTelemetry + SkyWalking 双模式，实现完整的 Trace → Metrics → Logging 三支柱可观测性

## 架构概览

```
                    ┌─────────────────────────────────────────┐
                    │           OpenTelemetry Collector         │
                    │  (接收/处理/导出 Trace + Metrics + Logs) │
                    └───────┬────────────┬────────────┬────────┘
                            │            │            │
              ┌─────────────▼──┐  ┌──────▼──────┐  ┌──▼────────────┐
              │   Jaeger/Tempo  │  │  Prometheus │  │  Loki/ELK     │
              │   (链路追踪 UI) │  │  (指标存储) │  │  (日志存储)   │
              └────────────────┘  └─────────────┘  └───────────────┘
```

## 接入方式

### 方式一：Java Agent 自动注入（推荐）

最简单的方式，无需修改代码：

```bash
# 启动参数注入
-javaagent:/path/to/opentelemetry-javaagent.jar \
  -Dotel.service.name=ydsz-gateway \
  -Dotel.exporter.otlp.endpoint=http://otel-collector:4317 \
  -Dotel.traces.exporter=otlp \
  -Dotel.metrics.exporter=otlp \
  -Dotel.logs.exporter=otlp
```

### 方式二：Spring Boot Starter 接入

在 `pom.xml` 中添加依赖：

```xml
<!-- Spring Boot 3.x OTel Starter -->
<dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-spring-boot-starter</artifactId>
</dependency>
```

配置 `application.yml`：

```yaml
management:
  tracing:
    sampling:
      probability: 1.0  # 生产环境建议 0.1-0.5
  otlp:
    tracing:
      endpoint: http://otel-collector:4317
    metrics:
      export:
        url: http://otel-collector:4318/v1/metrics
```

## 业务方法级埋点

### @WithSpan 注解（最简方式）

```java
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;

@Service
public class ProjectService {

    @WithSpan("ProjectService.createProject")
    public ProjectVO createProject(
            @SpanAttribute("project.name") String name,
            @SpanAttribute("project.budget") BigDecimal budget) {
        // 业务逻辑...
    }
}
```

### 手动埋点（更灵活的上下文）

```java
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;

@Service
public class ContractService {

    public ContractVO approveContract(Long contractId) {
        Span span = Span.current();
        span.setAttribute("contract.id", contractId);
        span.setAttribute("contract.action", "APPROVE");

        try {
            // 业务逻辑...
            span.setStatus(StatusCode.OK);
            return result;
        catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            throw e;
        }
    }
}
```

## 跨服务链路串联

### HTTP 调用自动串联

使用 OpenFeign 时，通过拦截器自动传递 TraceContext：

```java
@Configuration
public class TracingFeignConfig {

    @Bean
    public RequestInterceptor traceIdInterceptor() {
        return template -> {
            // W3C Trace Context 自动传播
            String traceId = Span.current().getSpanContext().getTraceId();
            String spanId = Span.current().getSpanContext().getSpanId();
            template.header("traceparent", String.format("00-%s-%s-01", traceId, spanId));
        };
    }
}
```

### 消息队列链路串联

```java
// 生产者：注入 TraceContext 到消息头
Message message = new Message("topic", payload);
message.putUserProperty("trace_id", Span.current().getSpanContext().getTraceId());
message.putUserProperty("span_id", Span.current().getSpanContext().getSpanId());
producer.send(message);

// 消费者：从消息头恢复 TraceContext
String traceId = message.getUserProperty("trace_id");
String spanId = message.getUserProperty("span_id");
// 创建 child span 继续追踪
```

## 关键埋点清单

| 层级 | 埋点位置 | Span 名称 | 关键属性 |
|------|---------|-----------|---------|
| Gateway | AuthGlobalFilter | auth.validate | user_id, tenant_id |
| Gateway | RateLimitFilter | rate.limit.check | dimension, identity |
| Service | Controller | {ClassName}.{Method} | http.method, http.path |
| Service | ServiceImpl | {Service}.{Method} | business.key |
| Data | MyBatis | sql.query | sql.table, sql.duration |
| Cache | CacheLoader | cache.{operation} | cache.key, cache.hit |
| MQ | Producer | mq.send.{topic} | mq.topic, mq.tag |
| MQ | Consumer | mq.receive.{topic} | mq.topic, mq.msg_id |

## Grafana 链路追踪面板配置

### Trace 查询面板

```json
{
  "title": "Trace 查询",
  "type": "traces",
  "datasource": {
    "type": "jaeger",
    "uid": "jaeger-ds"
  },
  "targets": [
    {
      "query": "{service=\"${service}\"} | duration > ${min_duration}"
    }
  ]
}
```

### 服务依赖图

```json
{
  "title": "服务拓扑",
  "type": "nodeGraph",
  "datasource": {
    "type": "jaeger",
    "uid": "jaeger-ds"
  }
}
```
