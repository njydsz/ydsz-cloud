# ydzs-common-feign

> OpenFeign 增强（L5 业务服务层）— 请求头透传 / 编解码 / 重试 / 链路追踪 / 熔断 / 隔离 / 压缩

提供统一请求头透传（13 个核心业务头）、自定义 JSON 编解码（`YdszResponse` 自动解包）、指数退避重试（方法级 HTTP Method Aware）、链路追踪（W3C TraceContext + SkyWalking + OTel Header 透传）、GZIP 请求压缩、Bulkhead 信号量隔离、Resilience4j 熔断器适配、Redis 可选用状态持久化、Feign 指标采集等能力。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L5 业务服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 提供 OpenFeign 增强：编解码 / 重试 / 追踪 / 熔断 / 隔离 / 请求头透传 |
| **依赖** | ydsz-common-util(L1) / ydzs-common-core(L2) / ydzs-common-domain(L3) / ydzs-common-exception(L3) / ydzz-common-json(L1)；spring-cloud-starter-openfeign / feign-hc5 / httpclient5 / resilience4j-circuitbreaker / transmittable-thread-local；可选 ydzs-common-redis(L4) / spring-boot-actuator / micrometer-core |

## 核心能力

### 1. 请求头统一透传

| 类 | 说明 |
|---|---|
| `FeignRequestInterceptor` | 从当前 HTTP Request 提取 13 个核心业务头，写入 Feign 子调用 RequestTemplate |

**透传 Header 列表**（ydsz.feign.propagation.headers 可自定义增减）：

| Header | 说明 |
|---|---|
| `traceparent` | W3C 链路追踪头（TraceContext 传播） |
| `X-Tenant-Id` | 租户 ID（多租户隔离） |
| `X-Access-Token` | 访问令牌（身份鉴权透传） |
| `X-Request-Id` | 请求唯一 ID（端到端请求链路串联） |
| `X-User-Userid` | 当前用户 ID（行为审计 / 权限判定） |
| `X-User-Username` | 当前用户名（显示名透传） |
| `X-User-Locale` | 用户语言环境（国际化，如zh-CN / en-US） |
| `X-Request-Source` | 请求来源标识（WEB / APP / OPEN_API） |
| `X-Company-Ids` | 公司 ID 集合（组织权限校验） |
| `X-Data-Scope` | 数据权限范围类型（ALL / DEPT / SELF 等） |
| `X-Unique-Id` | 用户登录唯一 ID（会话级标识） |
| `X-Dept-Ids` | 部门 ID 集合（部门权限校验） |
| `X-Service-Type` | 服务类型标识（内部调用 / 外部网关） |

### 2. 自定义 JSON 编解码

| 类 | 说明 |
|---|---|
| `JsonEncoder` | JSON 请求编码器（使用 YdszJson 引擎） |
| `JsonDecoder` | JSON 响应解码器 |
| `ResponseUnwrapDecoder` | YdszResponse 自动解包（服务端返回 YdszResponse，客户端直接获取 data） |

**解包行为**：Feign 接口返回声明为业务实体类型时，`ResponseUnwrapDecoder` 自动从 `YdszResponse<T>.data` 提取，业务状态码非成功时抛出 `FeignBusinessException`。

**性能优化（自 26.09.19）**：目标类型为普通业务类型时采用 JsonNode 中间路径——先解析为树模型提取 data 子树，再通过 `treeToValue` 直转目标类型，避免完整构造 YdszResponse 包装对象。

### 3. 指数退避重试

| 类 | 说明 |
|---|---|
| `MethodAwareRetryer` | HTTP 方法感知重试器（仅配置的方法白名单内的请求进行重试） |

**重试策略配置**：

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.feign.retry.enabled` | true | 是否启用重试 |
| `ydsz.feign.retry.max-attempts` | 3 | 最大重试次数（含首次调用） |
| `ydsz.feign.retry.backoff.delay` | 100 | 初始退避延迟（毫秒） |
| `ydsz.feign.retry.backoff.maxDelay` | 500 | 最大退避延迟（毫秒） |
| `ydsz.feign.retry.retry-on-methods` | GET | 允许重试的 HTTP 方法白名单 |

### 4. 链路追踪

| 类 | 说明 |
|---|---|
| `TraceRequestInterceptor` | 链路追踪 RequestInterceptor（自动透传 traceparent / spanId） |

**配置**：`ydsz.feign.trace.enabled=true`（默认开启）

### 5. GZIP 压缩

| 类 | 说明 |
|---|---|
| `GzipRequestCompressInterceptor` | GZIP 请求压缩拦截器（Body 超过阈值自动压缩） |

**压缩阈值**：默认 1KB（`ydsz.feign.compress.min-size=1024`）。

### 6. 信号量隔离（Bulkhead）

| 类 | 说明 |
|---|---|
| `BulkheadRequestInterceptor` | Bulkhead 信号量隔离拦截器（按服务维度限制最大并发请求数） |

**配置**：

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.feign.bulkhead.enabled` | false | 是否启用信号量隔离 |
| `ydsz.feign.bulkhead.default-max-concurrent` | 50 | 默认最大并发请求数 |
| `ydsz.feign.bulkhead.acquire-timeout-ms` | 100 | 获取许可超时时间（毫秒） |
| `ydsz.feign.bulkhead.service-max-concurrent` | - | 按服务维度配置（message=10 等） |

### 7. 熔断器（Resilience4j 适配）

| 类 | 说明 |
|---|---|
| `FeignResilience4jAutoConfiguration` | Resilience4j 全局 CircuitBreakerRegistry 自动配置 |
| `SafeCircuitBreakerAdapter` | Resilience4j CircuitBreaker 适配器 |
| `CircuitBreakerStatePersistence` | 熔断状态 Redis 持久化（可选） |
| `FeignCircuitBreakerStrategy` | 熔断策略接口 |

**统一配置入口**（自 26.09.19）：

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.feign.circuit-breaker.enabled` | false | 是否启用熔断 |
| `ydsz.feign.circuit-breaker.failure-rate-threshold` | 50.0 | 失败率阈值（百分比） |
| `ydsz.feign.circuit-breaker.slow-call-rate-threshold` | 80.0 | 慢调用率阈值（百分比） |
| `ydsz.feign.circuit-breaker.slow-call-duration-ms` | 3000 | 慢调用时长阈值（毫秒） |
| `ydsz.feign.circuit-breaker.wait-duration-ms` | 10000 | 熔断打开后等待恢复时长（毫秒） |
| `ydsz.feign.circuit-breaker.minimum-number-of-calls` | 10 | 滑动窗口内最小调用次数 |
| `ydsz.feign.circuit-breaker.sliding-window-size` | 20 | 滑动窗口大小 |
| `ydsz.feign.circuit-breaker.permitted-number-of-calls-in-half-open-state` | 10 | HALF_OPEN 状态允许的探测调用数 |
| `ydsz.feign.circuit-breaker.state-ttl-seconds` | 3600 | 熔断状态 Redis 持久化 TTL（秒） |

### 8. DTO（供 message-api 复用）

| 类 | 说明 |
|---|---|
| `BroadcastRequestDTO` | 广播请求 DTO |
| `PushRealtimeRequestDTO` | 实时推送请求 DTO |
| `RealtimePushDTO` | 实时推送结果 DTO |

> 这些 DTO 保留在本模块供 ydzs-message-api 模块通过依赖引用，避免消息领域对象下沉到 common 基础层。

### 9. 名字组装

| 类 | 说明 |
|---|---|
| `NameAssembler`（interface） | ID → 名称富化接口 |
| `NoOpNameAssembler` | 默认空实现（不进行名称替换） |
| `NameAssemblerAutoConfiguration` | 自动配置 |

### 10. 可观测性

| 类 | 说明 |
|---|---|
| `FeignMicrometerCollector` | Feign Micrometer 收集器（Timer / Counter / DistributionSummary） |
| `FeignResponseMetricsAdapter` | 响应指标适配器 |

**已注册指标**：

| 指标名 | 类型 | 说明 |
|---|---|---|
| `feign.request.latency` | Timer | 请求延迟（标签：client, method, status_code） |
| `feign.request.errors` | Counter | 请求错误计数 |
| `feign.request.slow` | Counter | 慢调用计数 |
| `feign.response.body.size` | DistributionSummary | 响应体大小分布 |

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

    @GetMapping("/internal/users/{id}")
    UserVO getUser(@PathVariable("id") Long id);  // YdszResponse 自动解包

    @PostMapping("/internal/users")
    Long createUser(@RequestBody CreateUserRequest request);
}
```

### 4. 配置属性

```yaml
ydsz:
  feign:
    enabled: true
    logger-level: BASIC
    retry:
      enabled: true
      max-attempts: 3
      backoff:
        delay: 100
        maxDelay: 500
      retry-on-methods:
        - GET
    trace:
      enabled: true
    compress:
      enabled: false
      min-size: 1024
    bulkhead:
      enabled: false
      default-max-concurrent: 50
      acquire-timeout-ms: 100
      service-max-concurrent:
        message: 10
    circuit-breaker:
      enabled: false
      failure-rate-threshold: 50.0
      slow-call-rate-threshold: 80.0
      slow-call-duration-ms: 3000
      wait-duration-ms: 10000
      minimum-number-of-calls: 10
      sliding-window-size: 20
      permitted-number-of-calls-in-half-open-state: 10
```

## SPI 扩展点

| SPI 接口 | 用途 | 注册方式 |
|---|---|---|
| `FeignCircuitBreakerStrategy` | Feign 熔断器策略 | `@ConditionalOnMissingBean` |
| `FeignResponseMetrics` | 响应指标采集（recordSuccess / recordFailure / recordSlowCall / recordResponseBodySize） | `@Component` |
| `NameAssembler` | ID → 名称富化逻辑 | `@ConditionalOnMissingBean` |

## 自动配置类

| 类 | 触发条件 |
|---|---|
| `FeignConfiguration` | `spring-cloud-starter-openfeign` 在 classpath |
| `FeignResilience4jAutoConfiguration` | Resilience4j CircuitBreakerConfig 存在 且 `ydsz.feign.circuit-breaker.enabled=true` |
| `FeignCircuitBreakerConfiguration` | Resilience4j CircuitBreaker 存在 |
| `NameAssemblerAutoConfiguration` | `ydsz.feign` 模块启用 |
| `TraceRequestInterceptor`（Bean） | `ydsz.feign.trace.enabled=true` |

## 注意事项

1. **请求头透传**：默认透传 13 个核心业务头，覆盖链路追踪 / 身份鉴权 / 权限校验 / 租户隔离等场景；可通过 `ydsz.feign.propagation.headers` 自定义增减。
2. **YdszResponse 自动解包**：仅适用于服务端按 `YdszResponse<T>` 规范返回的场景；非 YdszResponse 返回会触发 `JsonDecoder` 直接解码。
3. **重试幂等**：Feign 重试仅作用于幂等请求（默认仅 GET）；写操作（POST / PUT / DELETE）不会重试，除非显式配置 `ydsz.feign.retry.retry-on-methods` 包含对应方法。
4. **GZIP 压缩阈值**：小请求（< 1KB）不建议开启，压缩后体积变化不大且增加 CPU 开销。
5. **熔断器配置合并**（26.09.19）：`ydsz.feign.circuit-breaker.*` 为唯一配置入口，原 `ydsz.feign.resilience4j.*` 路径已废弃，保留一版本兼容。

## 变更记录

- **2.2.0**（26.09.19）：合并熔断配置源（CircuitBreaker 为唯一配置入口，resilience4j 路径标记废弃）；ResponseUnwrapDecoder 增加 JsonNode 中间路径优化；BigDecimal 阈值改为 float；README 文档与代码同步修正。
- **2.1.0**（26.09.04）：新增 `MethodAwareRetryer` 方法级感知重试（HTTP 方法白名单过滤）；新增 Bulkhead 信号量隔离。
- **2.0.0**（26.09.01）：13 个核心业务头统一透传；YdszResponse 自动解包（ResponseUnwrapDecoder）；Resilience4j 熔断器适配。
- **1.0.0**（26.08.02）：初始版本（Feign 增强基座）。
