# ydsz-common-feign

> 企业级 OpenFeign 统一增强模块（L5 业务服务层）

提供统一请求头透传、错误解码映射、YdszJson 编解码、链路追踪注入、监控指标采集、请求重试、GZIP 压缩、信号量隔离（Bulkhead）、熔断器、响应拦截、ID→名称富化（NameAssembler）、通知中心客户端等开箱即用能力，简化接入复杂度，降低维护成本。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L5 业务服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 提供 Feign 客户端统一增强能力（透传/重试/追踪/指标/熔断/隔离/压缩/富化） |
| **源文件数** | 40 |
| **依赖** | common-common、common-json、spring-cloud-openfeign-core；可选依赖 resilience4j-spring-boot3、micrometer-core、spring-boot-health |

## 核心能力

### 1. 核心请求头透传

| 类 | 说明 |
|---|---|
| `FeignRequestInterceptor` | 请求头透传拦截器（委托 `Propagation` 配置） |

默认自动透传 **13 个**核心请求头，保证链路可追溯、租户上下文透传：
- `traceparent`：W3C 标准链路追踪头，自动从当前上下文获取
- `X-Tenant-Id`：租户上下文标识，从当前请求头获取
- `X-Access-Token`：用户访问令牌，从当前请求头获取
- `X-Request-Id`：请求唯一标识，不存在时自动生成
- `X-User-Userid` / `X-User-Username` / `X-User-Locale`：用户信息
- `X-Request-Source`：请求来源
- `X-Company-Ids` / `X-Dept-Ids` / `X-Data-Scope`：组织与数据权限
- `X-Unique-Id`：唯一请求 ID
- `X-Service-Type`：服务类型

### 2. 请求重试

| 类 | 说明 |
|---|---|
| `MethodAwareRetryer` | 方法级感知重试器（支持按方法配置重试策略） |

默认开启重试，仅对 GET 方法生效（可通过 `retry.retryOnMethods` 配置），最大重试 3 次，指数退避策略（初始延迟 100ms，最大延迟 500ms），避免重试风暴。

### 3. 链路追踪

| 类 | 说明 |
|---|---|
| `TraceRequestInterceptor` | 链路追踪注入拦截器 |
| `FeignTraceHandler` | Feign 链路追踪处理器（SPI，默认实现） |
| `SkyWalkingTraceHandler` | SkyWalking 链路追踪处理器 |

默认开启 W3C `traceparent` 协议头透传，兼容 SkyWalking、Zipkin 等主流链路追踪系统。

### 4. 监控指标

| 类 | 说明 |
|---|---|
| `FeignMicrometerCollector` | Micrometer 指标收集器（Timer.Sample 低开销测量） |
| `FeignMetricsConfiguration` | 指标采集自动配置 |
| `FeignResponseMetricsAdapter` | 响应指标适配器 |

**暴露指标**：

| 指标名 | 类型 | 说明 |
|---|---|---|
| `feign.request.latency` | Timer | Feign 请求延迟（tag: client, method, status_code） |
| `feign.request.errors` | Counter | Feign 请求错误（tag: client, method, status_code） |
| `feign.request.slow` | Counter | Feign 慢调用（tag: client, method） |

### 5. 错误解码

| 类 | 说明 |
|---|---|
| `YdszFeignErrorDecoder` | Feign 错误解码器（HTTP 错误状态码 → 业务异常映射） |
| `ResponseUnwrapDecoder` | 响应解包解码器（统一处理 `YdszResponse` 包装） |
| `JsonEncoder` / `JsonDecoder` | YdszJson 编解码器 |
| `OpenFeignException` | Feign 调用统一异常 |
| `NotFoundException` | 404 异常（HTTP 404 → 业务异常） |
| `BadRequestException` | 400 异常（HTTP 400 → 业务异常） |

### 6. 熔断能力

| 类 | 说明 |
|---|---|
| `Resilience4jCircuitBreakerAdapter` | Resilience4j 熔断器适配器 |
| `Resilience4jFeignConfiguration` | Resilience4j Feign 自动配置 |
| `FeignCircuitBreakerStrategy` | Feign 熔断策略 |
| `FeignCircuitBreakerMetricsExporter` | 熔断器指标导出器 |
| `CircuitBreakerStatePersistence` | 熔断状态 Redis 持久化 |

默认关闭，开启后需自行引入 Resilience4j 依赖。支持失败率阈值、慢调用率阈值、滑动窗口、自动恢复等待时长等配置。熔断状态可持久化到 Redis（默认 TTL 3600 秒）。

### 7. 信号量隔离（Bulkhead）

| 类 | 说明 |
|---|---|
| `BulkheadRequestInterceptor` | 信号量隔离拦截器（按服务维度限制最大并发请求数） |

使用信号量隔离模式，当并发请求超过限制时快速失败而非排队等待，防止某个下游服务变慢耗尽连接池资源影响其他服务。支持全局默认配置 + 按服务维度独立配置。

### 8. GZIP 请求压缩

| 类 | 说明 |
|---|---|
| `GzipRequestCompressInterceptor` | GZIP 请求压缩拦截器 |

对 Feign 请求体进行 GZIP 压缩，减少网络传输量。支持配置压缩触发阈值（默认 1024 字节）和排除的 Content-Type 列表。

### 9. 响应拦截器

| 类 | 说明 |
|---|---|
| `FeignResponseInterceptor` | Feign 响应拦截器（ResponseInterceptor） |

统一处理 Feign 客户端响应，提供：
- 熔断器集成（调用前检查 allowRequest，调用后记录 success/failure）
- 响应日志记录（状态码、耗时、方法信息）
- 响应时间指标采集
- 慢调用检测与告警
- 异常响应统一处理
- Bulkhead 许可释放（finally 块释放信号量许可）

### 10. ID→名称富化（NameAssembler）

| 类 | 说明 |
|---|---|
| `NameAssembler` | ID→名称富化组件接口（SPI） |
| `NoOpNameAssembler` | 兜底实现（不做任何富化） |
| `NameAssemblerProperties` | 名称富化配置属性 |
| `NameAssemblerAutoConfiguration` | 名称富化自动配置 |
| `NameType` | 名称类型枚举（USER / DEPT / ROLE 等） |

跨服务解析业务对象 ID 为用户可读名称，用于 VO 场景中的 createdBy/assignee 等字段富化。业务域实现 `NameAssembler` 接口即可覆盖默认实现。

### 11. 通知中心客户端

| 类 | 说明 |
|---|---|
| `NotificationClient` | 通知中心 Feign 客户端（通用通知能力） |
| `NotificationClientFallbackFactory` | 通知客户端降级工厂 |
| `MessageRequest` / `MessageResult` | 通知请求/响应模型 |
| `RealtimePushDTO` / `PushRealtimeRequestDTO` | 实时推送 DTO |
| `BroadcastRequestDTO` | 广播请求 DTO |

提供跨服务消息通知的统一入口，封装多通道路由（邮件/短信/Webhook/站内信/实时推送）。

### 12. 日志增强

| 类 | 说明 |
|---|---|
| `YdszFeignLogger` | Feign 日志增强器（自定义日志格式，提升可观测性） |

支持 NONE / BASIC / HEADERS / FULL 四种日志级别，通过 `ydsz.feign.logger-level` 配置。

### 13. HTTP 客户端配置

| 类 | 说明 |
|---|---|
| `FeignClientConstants` | Feign 客户端常量 |

支持 HttpClient 连接池配置：最大连接数（默认 200）、每个路由的最大连接数（默认 50）、空闲连接保活时间、连接空闲校验间隔、连接最大存活时间。

## 接入方式

### 1. 引入依赖

在业务模块 pom.xml 中添加依赖：

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-feign</artifactId>
</dependency>
```

### 2. 启用 Feign 增强

在启动类上添加 `@EnableYdszFeign` 注解，导入全部公共能力：

```java
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.njydsz.common.feign.annotation.EnableYdszFeign;

@SpringBootApplication
@EnableYdszFeign
public class XxxApplication {
    public static void main(String[] args) {
        SpringApplication.run(XxxApplication.class, args);
    }
}
```

或者在启动类上添加 Spring Cloud 原生注解，指定 Feign 客户端扫描包路径：

```java
@SpringBootApplication
@EnableFeignClients(basePackages = {"com.njydsz.xxx.feign"})
public class XxxApplication {
    public static void main(String[] args) {
        SpringApplication.run(XxxApplication.class, args);
    }
}
```

### 3. 自定义配置（可选）

在 application.yml 中添加配置，不配置则使用默认值：

```yaml
ydsz:
  feign:
    enabled: true                          # 总开关，默认true
    logger-level: BASIC                    # 日志级别，默认BASIC，可选NONE/HEADERS/FULL
    propagation:
      enabled: true                        # 核心请求头透传开关，默认true
    retry:
      enabled: true                        # 重试开关，默认true
      max-attempts: 3                      # 最大重试次数，默认3
      backoff:
        delay: 100                         # 初始延迟（毫秒），默认100
        max-delay: 500                     # 最大延迟（毫秒），默认500
      retry-on-methods: [GET]              # 可重试的HTTP方法白名单
    timeout:
      connect: 5000                        # 连接超时（毫秒），默认5000
      read: 10000                          # 读取超时（毫秒），默认10000
    trace:
      enabled: true                        # W3C链路追踪头透传开关，默认true
    metrics:
      enabled: true                        # 监控指标采集开关，默认true
    circuit-breaker:
      enabled: false                       # 熔断器开关，默认false
      failure-rate-threshold: 50           # 失败率阈值（百分比），默认50
      slow-call-rate-threshold: 80         # 慢调用率阈值（百分比），默认80
      slow-call-duration-ms: 3000          # 慢调用时长阈值（毫秒），默认3000
      wait-duration-ms: 10000              # 熔断打开后自动恢复等待时长（毫秒），默认10000
      minimum-number-of-calls: 10          # 滑动窗口内最小调用次数，默认10
      sliding-window-size: 20              # 滑动窗口大小，默认20
      state-ttl-seconds: 3600              # 熔断状态Redis持久化TTL（秒），默认3600
    bulkhead:
      enabled: false                       # 信号量隔离开关，默认false
      default-max-concurrent: 50           # 默认最大并发请求数，默认50
      acquire-timeout-ms: 100              # 获取许可超时时间（毫秒），默认100
      service-max-concurrent:              # 按服务维度配置最大并发请求数
        myservice: 100
    compress:
      enabled: false                       # GZIP压缩开关，默认false
      min-size: 1024                       # 压缩触发阈值（字节），默认1024
      excluded-content-types: []           # 排除压缩的Content-Type列表
    client:
      max-connections: 200                 # 连接池最大连接数，默认200
      max-per-route: 50                    # 每个路由的最大连接数，默认50
      keep-alive: 30000                    # 空闲连接保活时间（毫秒），默认30000
      validate-after-inactivity: 5000      # 连接空闲多久后校验（毫秒），默认5000
      connection-time-to-live: 60000       # 连接最大存活时间（毫秒），默认60000
    response-interceptor:
      enabled: true                        # 响应拦截器开关，默认true
      log-enabled: false                   # 响应日志开关，默认false
      metrics-enabled: true                # 响应时间指标采集开关，默认true
      slow-call-threshold-millis: 3000     # 慢调用阈值（毫秒），默认3000
    error:
      include-body: false                  # 错误信息中是否包含响应体，默认false
      max-body-bytes: 4096                 # 响应体最大字节数，默认4096
```

## 配置项总结

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.feign.enabled` | `true` | 模块总开关 |
| `ydsz.feign.logger-level` | `BASIC` | Feign 日志级别 |
| `ydsz.feign.propagation.enabled` | `true` | 请求头透传开关 |
| `ydsz.feign.retry.enabled` | `true` | 重试开关 |
| `ydsz.feign.retry.max-attempts` | `3` | 最大重试次数 |
| `ydsz.feign.retry.backoff.delay` | `100` | 初始退避延迟（毫秒） |
| `ydsz.feign.retry.backoff.max-delay` | `500` | 最大退避延迟（毫秒） |
| `ydsz.feign.retry.retry-on-methods` | `[GET]` | 可重试 HTTP 方法白名单 |
| `ydsz.feign.timeout.connect` | `5000` | 连接超时（毫秒） |
| `ydsz.feign.timeout.read` | `10000` | 读取超时（毫秒） |
| `ydsz.feign.trace.enabled` | `true` | W3C 链路追踪头透传开关 |
| `ydsz.feign.metrics.enabled` | `true` | 监控指标采集开关 |
| `ydsz.feign.circuit-breaker.enabled` | `false` | 熔断器开关 |
| `ydsz.feign.bulkhead.enabled` | `false` | 信号量隔离开关 |
| `ydsz.feign.compress.enabled` | `false` | GZIP 压缩开关 |
| `ydsz.feign.client.max-connections` | `200` | HttpClient 连接池最大连接数 |
| `ydsz.feign.response-interceptor.enabled` | `true` | 响应拦截器开关 |

## 常见问题

### Q1：如何自定义透传的请求头？

在配置中修改 `ydsz.feign.propagation.headers` 即可，例如：

```yaml
ydsz:
  feign:
    propagation:
      headers:
        - traceparent
        - X-Tenant-Id
        - X-Access-Token
        - X-Request-Id
        - X-Custom-Header # 自定义头
```

### Q2：如何关闭某个 Feign 客户端的重试？

在对应 FeignClient 的 configuration 中自定义 Retryer 即可，例如：

```java
@FeignClient(name = "xxx", configuration = NoRetryConfig.class)
public interface XxxClient {
    // 接口方法
}

public class NoRetryConfig {
    @Bean
    public Retryer feignRetryer() {
        return Retryer.NEVER_RETRY;
    }
}
```

### Q3：如何开启熔断能力？

1. 在配置中开启熔断开关：`ydsz.feign.circuit-breaker.enabled=true`
2. 在业务模块中引入 Resilience4j 依赖：

```xml
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
</dependency>
```

3. 在 application.yml 中添加熔断配置（使用 `ydsz.feign.circuit-breaker.*` 前缀）。

### Q4：如何开启信号量隔离？

```yaml
ydsz:
  feign:
    bulkhead:
      enabled: true
      default-max-concurrent: 50        # 全局默认最大并发
      acquire-timeout-ms: 100           # 获取许可超时时间
      service-max-concurrent:           # 按服务维度配置
        slow-service: 20                # 慢服务限制更严格
```

### Q5：如何开启 GZIP 压缩？

```yaml
ydsz:
  feign:
    compress:
      enabled: true
      min-size: 1024                   # 大于 1024 字节的请求体才压缩
      excluded-content-types:
        - application/octet-stream     # 已压缩的内容不重复压缩
```

### Q6：如何自定义 ID→名称富化？

实现 `NameAssembler` 接口并注册为 Spring Bean：

```java
@Component
public class UserNameAssembler implements NameAssembler {
    @Override
    public <T> void enrich(Collection<T> list, Function<T, String> idGetter,
                          BiConsumer<T, String> nameSetter, NameType type) {
        // 批量查询 ID→名称映射，调用 nameSetter 写入
    }
}
```

### Q7：如何关闭整个 Feign 增强模块？

在配置中添加：`ydsz.feign.enabled=false` 即可，关闭后所有增强能力失效，Feign 客户端使用 Spring Cloud 原生能力。

## SPI 扩展点

| SPI 接口 | 用途 | 默认实现 |
|---|---|---|
| `FeignTraceHandler` | 链路追踪处理器 SPI | `FeignTraceHandler`（默认），`SkyWalkingTraceHandler` |
| `NameAssembler` | ID→名称富化 SPI | `NoOpNameAssembler`（兜底） |

所有默认实现均通过 `@ConditionalOnMissingBean` 注册，业务侧自定义 Bean 自动覆盖默认实现。

## 自动配置

| 配置类 | 激活条件 |
|---|---|
| `FeignConfiguration` | `@EnableYdszFeign` 或自动装配激活 |
| `NameAssemblerAutoConfiguration` | `@EnableYdszFeign` 激活 |
| `Resilience4jFeignConfiguration` | `circuit-breaker.enabled=true` + Resilience4j 在 classpath |
| `FeignMetricsConfiguration` | `metrics.enabled=true` + MeterRegistry 在 classpath |

## 注意事项

1. **注解启用**：v1.6.0 起新增 `@EnableYdszFeign` 注解，显式开启所有 Feign 增强能力，推荐替代手动配置 `FeignConfiguration` 导入方式。
2. **请求头透传**：默认透传 13 个核心请求头，覆盖链路追踪、身份鉴权、权限校验、租户隔离等全部业务场景。
3. **重试策略**：仅 GET 方法默认重试，可通过 `retry.retryOnMethods` 自定义。指数退避策略（初始 100ms，最大 500ms）。
4. **熔断器依赖**：开启 `circuit-breaker.enabled=true` 后需引入 `resilience4j-spring-boot3` 依赖，否则启动报错。
5. **信号量隔离**：Bulkhead 使用信号量模式，获取许可超时默认 100ms 快速失败，避免连接池耗尽。
6. **GZIP 压缩**：仅对大于 `min-size` 阈值的请求体生效，避免小请求压缩后反而变大。
7. **响应拦截器**：统一处理熔断集成、响应日志、指标采集、慢调用检测、Bulkhead 许可释放，建议保持开启。
8. **NameAssembler 兜底**：未自定义实现时使用 `NoOpNameAssembler`，不做任何富化（ID 原样返回）。

## 变更记录

- **v1.6.0**（2026-08-18）：新增 `@EnableYdszFeign` 启用注解；新增信号量隔离（`BulkheadRequestInterceptor`）、GZIP 压缩（`GzipRequestCompressInterceptor`）、响应拦截器（`FeignResponseInterceptor`）；新增 ID→名称富化 SPI（`NameAssembler` / `NameAssemblerAutoConfiguration` / `NameType` / `NoOpNameAssembler`）；新增通知中心客户端（`NotificationClient` + 降级工厂 + DTO）；新增 HttpClient 连接池配置（maxConnections / maxPerRoute / keepAlive 等）；新增错误解码增强（`ResponseUnwrapDecoder` / `JsonEncoder` / `JsonDecoder`）；新增链路追踪 SPI（`FeignTraceHandler` / `SkyWalkingTraceHandler`）；新增 Feign 日志增强（`YdszFeignLogger`）；新增方法级重试器（`MethodAwareRetryer`）；新增 Feign 调用常量（`FeignClientConstants`）；新增熔断器指标导出器（`FeignCircuitBreakerMetricsExporter`）和熔断状态持久化（`CircuitBreakerStatePersistence`）；新增异常类型（`OpenFeignException` / `NotFoundException` / `BadRequestException`）。
- **v1.0.0**（2026-08-02）：初始版本。提供统一请求头透传、错误解码映射、YdszJson 编解码、链路追踪注入、监控指标采集、请求重试、熔断器等核心能力。
