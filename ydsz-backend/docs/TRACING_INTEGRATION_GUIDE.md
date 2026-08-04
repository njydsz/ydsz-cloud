# YDSZ 全链路追踪集成指南（P1-4）

> 目标：让线上问题定位从"查多个服务的日志"变成"一条 Trace 看全貌"。
> 本文档说明三种埋点层级的启用路径与适用场景。

## 一、当前链路能力总览

| 层级 | 能力 | 状态 | 承载方式 |
|------|------|------|---------|
| **传输协议** | W3C Trace Context（`traceparent`）+ `X-Trace-Id` 兼容头 | ✅ 已启用 | 网关 `W3CTraceContextFilter` |
| **自动埋点** | HTTP Client/Server + JDBC + Redis + RocketMQ 字节码级 Span | ✅ 已启用 | Dockerfile 注入 OTel Java Agent（`OTEL_ENABLED=true`） |
| **Trace 后端** | Jaeger/Tempo 存储与查询 | ⚠️ 需部署 | `docker-compose.observability.yaml` |
| **业务埋点** | 方法级 Span（`@Observed` / `@WithSpan`） | 🔶 可选增强 | 见下文第三节 |
| **日志关联** | Logback 输出 traceId 与 traceparent 关联 | ✅ 已启用 | `logstash-logback-encoder` |

## 二、快速启用（5 分钟）

### 2.1 部署 Trace 后端

```bash
cd ydsz-backend/deploy/observability
docker compose -f docker-compose.observability.yaml up -d

# 验证
curl -s http://localhost:4317   # OTLP gRPC 端口就绪
open http://localhost:16686     # Jaeger UI
```

### 2.2 确认微服务上报

微服务容器默认已注入 OTel Agent 并指向 `otel-collector:4317`。
本地开发（无容器）时设置：

```bash
export OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317
```

### 2.3 验证一条完整链路

1. 浏览器登录 → 网关 → userinfo 鉴权 → project 查询 → 数据库
2. Jaeger UI 搜索 `traceparent` 关联的 traceId
3. 应看到：gateway → userinfo → project 三段服务 Span + JDBC/Redis 子 Span

## 三、业务方法级埋点（可选增强）

> 自动埋点已覆盖中间件调用，业务方法级 Span 用于补充**业务语义**（如"立项审批"这个业务动作的耗时）。

### 3.1 方式 A：Spring `@Observed`（推荐，零额外依赖）

Spring Boot 4 已内置 `micrometer-observation`，业务方法标注注解即可：

```java
import io.micrometer.observation.annotation.Observed;

@Service
public class ProjectService {

    @Observed(name = "project.create", contextualName = "create-project")
    public ProjectVO createProject(ProjectCreateDTO dto) {
        // 自动生成 Span + 耗时指标
    }
}
```

启用观测（`application.yml`）：

```yaml
management:
  tracing:
    sampling:
      probability: 1.0   # 生产可调至 0.1（10% 采样）
```

### 3.2 方式 B：手动 Span（需要精确控制标签时）

```java
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;

@Service
public class ContractService {
    private final Tracer tracer;

    public void approveContract(Long contractId) {
        Span span = tracer.nextSpan().name("contract.approve").start();
        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
            span.tag("contract.id", String.valueOf(contractId));
            span.event("approval-passed");
            // 业务逻辑...
        } finally {
            span.end();
        }
    }
}
```

> ⚠️ 方式 B 需要引入桥接依赖 `micrometer-tracing-bridge-otel`（版本由 Spring Boot BOM 管理）。
> 当前 Dockerfile 的 Agent 方案无需该依赖即可工作；业务埋点按需引入。

### 3.3 采样策略建议

| 环境 | 采样率 | 说明 |
|------|--------|------|
| dev / test | 1.0 | 全量采集便于调试 |
| staging | 0.1 | 10% 采样 |
| prod | 0.05~0.1 | 5-10% 采样，高流量时段可临时调高 |
| 出错链路 | 100% | 结合 `sampler.type=ALWAYS` 或尾部采样器（Tail Sampling） |

## 四、Trace 与日志/指标的关联

- **日志**：Logback 已输出 `traceId`（logstash encoder），检索日志时用 traceId 过滤
- **指标**：Micrometer 自动上报 `http_server_requests_seconds` 等，Grafana 用 traceId 下钻到 Jaeger
- **跨服务**：网关生成的 `traceparent` 透传全链路，保证一条业务请求只有一个 traceId

## 五、常见问题排查

| 现象 | 排查路径 |
|------|---------|
| Jaeger 无数据 | ① `docker logs ydsz-otel-collector` 看是否收到 OTLP；② 容器内 `env | grep OTEL` 确认 endpoint；③ 检查 4317 端口连通性 |
| 只有单服务 Span | 检查下游服务是否注入 Agent、`OTEL_SERVICE_NAME` 是否正确 |
| traceId 与日志对不上 | 确认 logback pattern 使用 `%X{traceId}` 且 logstash encoder 开启 `includeMdcKeyName` |
| 采样率不生效 | 确认 `management.tracing.sampling.probability` 配置在 `ydsz-common.yaml` 共享配置中 |
