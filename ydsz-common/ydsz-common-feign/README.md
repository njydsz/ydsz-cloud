# ydzs-common-feign

> OpenFeign 增强（L5 业务服务层）— 数据权限透传 / 编解码 / 重试 / 链路追踪 / 熔断 / 压缩

提供数据权限上下文透传（行 / 列级）、自定义 JSON 编解码（`YdszResponse` 自动解包）、指数退避重试（`@Retryable` 语义化 + 方法级 Aware）、链路追踪（SkyWalking + Trace HTTP Header 透传）、GZIP 请求压缩、Bulkhead 信号量隔离、Resilience4j 熔断器适配、Redis 可选用状态持久化、Feign 指标采集、NotificationClient 通知发送 Feign 客户端等能力。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L5 业务服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 提供 OpenFeign 增强：解码 / 重试 / 追踪 / 熔断 / 数据权限透传 |
| **依赖** | ydsz-common-util、ydsz-common-core、ydsz-common-safe、ydsz-common-domain、ydsz-common-exception、ydsz-common-json；spring-cloud-starter-openfeign、feign-hc5、httpclient5、resilience4j-circuitbreaker、transmittable-thread-local；可选 ydsz-common-redis、spring-boot-actuator、spring-boot-health、restclient |
| **版本** | 2.1.0 |

## 核心能力

### 1. 数据权限透传

| 类 | 说明 |
|---|---|
| `FeignRequestInterceptor`（aspect） | 数据权限上下文拦截器（从 AuthContext 提取行 / 列权限，写入 Feign Request Header） |
| `FeignResponseInterceptor`（interceptor） | Feign 响应拦截器 |

**透传 Header**：

| Header | 说明 |
|---|---|
| `X-Tenant-Id` | 租户 ID |
| `X-Data-Scope-Rule` | 数据范围规则（DEPT_AND_CHILD 等） |
| `X-Data-Scope-Values` | 数据范围 ID 列表（JSON 数组） |
| `X-Col-Permissions` | 列权限位掩码 |
| `X-Trace-Id` | 链路追踪 ID |
| `X-Request-Id` | 请求 ID |

### 2. 自定义 JSON 编解码

| 类 | 说明 |
|---|---|
| `JsonEncoder` | JSON 请求编码器（使用 YdszJson 引擎，替代默认 JacksonEncoder） |
| `JsonDecoder` | JSON 响应解码器 |
| `ResponseUnwrapDecoder` | YdszResponse 自动解包（服务端返回 YdszResponse，客户端直接获取 data） |

**解包行为**：Feign 接口返回声明为业务实体类型时，`ResponseUnwrapDecoder` 自动从 `YdszResponse<T>.data` 提取。

### 3. 指数退避重试

| 类 | 说明 |
|---|---|
| `MethodAwareRetryer` | 方法级感知重试器（根据方法注解 / 返回类型决策重试策略） |
| `FeignProperties` | Feign 配置属性（`ydsz.feign.retry.*`） |

**重试策略配置**：

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.feign.retry.max-attempts` | 3 | 最大重试次数 |
| `ydsz.feign.retry.initial-backoff-ms` | 100 | 初始退避（毫秒） |
| `ydsz.feign.retry.max-backoff-ms` | 2000 | 最大退避（毫秒） |
| `ydsz.feign.retry.multiplier` | 2.0 | 退避乘数 |

### 4. 链路追踪

| 类 | 说明 |
|---|---|
| `FeignTraceHandler` **SPI** | Feign 链路追踪处理器（SkyWalking / Zipkin 自动适配） |
| `SkyWalkingTraceHandler` | SkyWalking 链路追踪实现 |
| `TraceRequestInterceptor` | 链路追踪 RequestInterceptor（自动透传 traceId / spanId） |

### 5. GZIP 压缩

| 类 | 说明 |
|---|---|
| `GzipRequestCompressInterceptor` | GZIP 请求压缩拦截器（Body 超过阈值自动压缩） |

**压缩阈值**：默认 1KB（`ydsz.feign.compress.threshold-bytes=1024`）。

### 6. 信号量隔离

| 类 | 说明 |
|---|---|
| `BulkheadRequestInterceptor`（interceptor） | Bulkhead 信号量隔离拦截器（限制同一方法的并发 Feign 调用数） |

### 7. 熔断器（Resilience4j 适配）

| 类 | 说明 |
|---|---|
| `CircuitBreakerConfig`（circuitbreaker） | 熔断配置 |
| `CircuitBreakerStatePersistence`（circuitbreaker） | 熔断状态持久化（Redis 可选） |
| `CircuitBreakerMetricsExporter`（circuitbreaker） | 熔断指标导出 |
| `CircuitBreakerStrategy`（circuitbreaker） | 熔断策略接口 |
| `SafeCircuitBreakerAdapter`（circuitbreaker） | Resilience4j CircuitBreaker 适配器 |

### 8. 通知 Feign 客户端

| 类 | 说明 |
|---|---|
| `NotificationClient` | 通知服务 Feign 客户端（调用 ydsz-notification 服务发送通知） |
| `NotificationClientFallbackFactory` | 降级工厂 |
| `BroadcastRequestDTO` / `PushRealtimeRequestDTO` / `RealtimePushDTO`（dto） | 通知推送 DTO |

### 9. 名字组装

| 类 | 说明 |
|---|---|
| `NameAssembler*`（assembler） | HTTP名字组装（站内信 / 通知 模板参数名） |
| `NameAssemblerAutoConfiguration`（assembler） | 自动配置 |

### 10. 可观测性

| 类 | 说明 |
|---|---|
| `FeignMetricsConfiguration`（monitor） | Feign 指标自动配置 |
| `FeignMicrometerCollector`（monitor） | Feign Micrometer 收集器（计数器 / 计时器） |
| `FeignResponseMetricsAdapter`（monitor） | 响应指标适配 |

## 接入方式

### 1. POM 引入依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-feign</artifactId>
</dependency>
```

### 2. 启用注解

```java
@SpringBootApplication
@EnableYdszFeign
@EnableFeignClients(basePackages = "com.njydsz")
public class SystemApplication { }
```

### 3. 定义 Feign 客户端

```java
@FeignClient(name = "userinfo-service", contextId = "userClient")
public interface UserClient {

    @GetMapping("/api/v1/users/{id}")
    UserVO getUser(@PathVariable("id") Long id);  // YdszResponse 自动解包

    @PostMapping("/api/v1/users")
    Long createUser(@RequestBody CreateUserRequest request);
}
```

### 4. 配置属性

```yaml
ydsz:
  feign:
    retry:
      max-attempts: 3
      initial-backoff-ms: 100
      max-backoff-ms: 2000
      multiplier: 2.0
    trace:
      enabled: true
      provider: SKYWALKING              # SKYWALKING / ZIPKIN / OTEL
    compress:
      enabled: true
      threshold-bytes: 1024
    bulkhead:
      enabled: false
      max-concurrent: 20
    circuit-breaker:
      failure-rate-threshold: 50
      slow-call-rate-threshold: 80
      slow-call-duration-threshold: 3s
      wait-duration-in-open-state: 30s
    notification:
      url: http://ydsz-notification:9009
```

### 5. 熔断器通知 Feign 客户端

```java
@Autowired
private NotificationClient notificationClient;

// 发送站内通知
notificationClient.sendRealtimePush(new PushRealtimeRequestDTO(...));
```

## SPI 扩展点

| SPI 接口 | 用途 | 注册方式 |
|---|---|---|
| `FeignTraceHandler` **SPI** | Feign 链路追踪（SkyWalking / Zipkin / OTel） | `@Component` |
| `FeignCircuitBreakerStrategy` | Feign 熔断器策略 | `@ConditionalOnMissingBean` |

## 健康检查

| 端点 | 说明 | 触发条件 |
|---|---|---|
| `/actuator/health/feign` | Feign 健康检查 | `ydsz.feign` 模块存在 |

## 自动配置类

| 类 | 触发条件 |
|---|---|
| `FeignConfiguration` | `spring-cloud-starter-openfeign` 在 classpath |
| `FeignMetricsConfiguration` | Micrometer 存在 |
| `CircuitBreakerFeignConfiguration` | Resilience4j 存在 |
| `NameAssemblerAutoConfiguration` | 自动扫描 EnableYdszFeign 注解 |

## 注意事项

1. **数据权限透传**：`AuthContext` 必须已写入 TenantContext / DataScopeContext，否则下游无法正确注入 SQL 条件。
2. **YdszResponse 自动解包**：仅适用于服务端按 YdszResponse 规范返回的场景；非 YdszResponse 返回会触发 `JsonDecoder` fallback。
3. **重试幂等**：Feign 重试仅作用于幂等请求（GET / 带幂等 key 的 POST）；写操作默认不启用方法级重试。
4. **GZIP 压缩阈值**：小请求（< 1KB）不建议开启，压缩后体积变化不大且增加 CPU 开销。

## 变更记录

- **2.1.0**（2026-09-04）：新增 `MethodAwareRetryer` 方法级感知重试（根据方法返回类型决策重试）；新增 Bulkhead 信号量隔离。
- **2.0.0**（2026-09-01）：Feign 数据权限透传（Tenant / DataScope / ColPermission Header 自定义）；YdszResponse 自动解包（ResponseUnwrapDecoder）。
- **1.0.0**（2026-08-02）：初始版本（Feign 增强基座）。
