# ydsz-pmis-common-feign

PMIS OpenFeign 增强框架 — 统一编解码、ResponseUnwrapDecoder 自动解包、DefaultFallbackFactory 降级工厂、Resilience4j 熔断器集成、链路追踪传播、动态客户端工厂、Gzip 压缩、Micrometer 监控。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L5 业务服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **源文件数** | 38 |

## 核心能力

### 统一编解码

| 类 | 说明 |
|---|---|
| `YdszJsonEncoder` | 统一 JSON 编码器 |
| `YdszJsonDecoder` | 统一 JSON 解码器 |
| `ResponseUnwrapDecoder` | 响应解包解码器（自动提取 `BaseResponse.data`） |

### 熔断降级

| 类 | 说明 |
|---|---|
| `Resilience4jFeignConfiguration` | Resilience4j 熔断配置 |
| `Resilience4jCircuitBreakerAdapter` | 熔断器适配器 |
| `FeignCircuitBreakerStrategy` | 熔断策略 |
| `CircuitBreakerStatePersistence` | 熔断器状态持久化 |
| `FeignCircuitBreakerMetricsExporter` | 熔断器指标导出 |
| `DefaultFallbackFactory` | 默认降级工厂 |
| `NotificationClientFallbackFactory` | 通知客户端降级 |
| `MessageServiceClientFallbackFactory` | 消息服务客户端降级 |

### 链路追踪

| 类 | 说明 |
|---|---|
| `TraceRequestInterceptor` | TraceId 请求拦截器（自动注入 X-Trace-Id） |
| `FeignTraceHandler` | Feign 追踪处理器 |
| `FeignRequestInterceptor` | 通用请求拦截器（Auth Token / 租户 ID / 请求 ID 传播） |

### 动态客户端

| 类 | 说明 |
|---|---|
| `DynamicFeignClientFactory` | 动态 Feign 客户端工厂（运行时创建 Feign 代理） |
| `FeignConfigRefresher` | Feign 配置刷新器（Nacos 配置变更 → 客户端重建） |
| `FeignClientConstants` | 客户端常量 |

### Gzip 压缩

| 类 | 说明 |
|---|---|
| `GzipRequestCompressInterceptor` | 请求 Gzip 压缩拦截器 |

### 监控

| 类 | 说明 |
|---|---|
| `FeignMetricsCollector` / `FeignMicrometerCollector` | Micrometer 指标采集（请求数 / 延迟 / 错误率） |
| `FeignResponseMetricsAdapter` | 响应指标适配器 |
| `FeignMetricsConfiguration` | 指标自动配置 |
| `FeignResponseInterceptor` | 响应拦截器 |

### 日志与异常

| 类 | 说明 |
|---|---|
| `YdszFeignLogger` | Feign 日志器（结构化日志） |
| `YdszFeignErrorDecoder` | 统一错误解码器 |
| `OpenFeignException` / `NotFoundException` / `BadRequestException` | Feign 异常体系 |

### 预定义客户端

| 接口 | 说明 |
|---|---|
| `NotificationClient` | 通知服务客户端 |
| `MessageServiceClient` | 消息服务客户端 |
| `MessageRequest` / `MessageResult` | 消息请求 / 响应模型 |
| `NotificationFeignDTO` / `RealtimePushDTO` | 通知 DTO |

### 健康检查

| 类 | 说明 |
|---|---|
| `FeignHealthIndicator` | Feign 客户端健康检查 |

## 配置项

```yaml
pmis:
  feign:
    compression:
      enabled: true
      min-request-size: 1024         # 最小压缩大小
    circuit-breaker:
      failure-rate-threshold: 50     # 失败率阈值（%）
      slow-call-duration-threshold: 5s
      wait-duration-in-open-state: 10s
      sliding-window-size: 100
    metrics:
      enabled: true
```

## 自动配置

| 配置类 | 激活条件 |
|---|---|
| `FeignConfiguration` | Feign 可用时激活 |
| `FeignMetricsConfiguration` | Micrometer 可用时激活 |
| `Resilience4jFeignConfiguration` | Resilience4j 可用时激活 |

## 依赖

```xml
<dependency>
    <groupId>com.njydsz.pmis</groupId>
    <artifactId>ydsz-pmis-common-feign</artifactId>
</dependency>
```
