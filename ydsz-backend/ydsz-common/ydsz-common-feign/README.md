# ydsz-common-feign

> YDSZ OpenFeign 企业级增强框架（L5 业务服务层）

提供统一编解码、ResponseUnwrapDecoder 自动解包、Resilience4j 熔断降级、舱壁隔离、链路追踪传播、动态客户端工厂、Gzip 压缩、Micrometer 监控、跨服务名称富化等能力，是 YDSZ 项目所有微服务间 Feign 调用的统一基座。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L5 业务服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 为所有业务服务提供 OpenFeign 调用的统一增强（编解码 / 熔断 / 重试 / 隔离 / 追踪 / 监控） |
| **依赖** | common-util、common-core、common-json；可选依赖 common-redis、spring-cloud-context、spring-cloud-starter-loadbalancer、resilience4j-spring-boot3、micrometer-core、spring-boot-actuator、spring-boot-health |
| **版本** | 1.0.0 |

## 核心能力

### 1. 统一编解码

| 类 | 说明 |
|---|---|
| `JsonEncoder` | 统一 JSON 编码器（基于 YdszJson） |
| `JsonDecoder` | 统一 JSON 解码器 |
| `ResponseUnwrapDecoder` | 响应解包解码器（自动提取 `BaseResponse.data`） |

### 2. 熔断降级

| 类 | 说明 |
|---|---|
| `Resilience4jFeignConfiguration` | Resilience4j 熔断器自动配置 |
| `Resilience4jCircuitBreakerAdapter` | Resilience4j 熔断器适配器 |
| `FeignCircuitBreakerStrategy` | 熔断器策略抽象接口 |
| `CircuitBreakerStatePersistence` | 熔断器状态持久化（Redis，应用重启后恢复熔断状态） |
| `FeignCircuitBreakerMetricsExporter` | 熔断器指标导出（Micrometer） |
| `DefaultFallbackFactory<T>` | 默认降级工厂抽象基类（提供安全降级代理，避免返回 null） |
| `NotificationClientFallbackFactory` | 通知客户端降级实现 |

### 3. 链路追踪与请求头透传

| 类 | 说明 |
|---|---|
| `TraceRequestInterceptor` | TraceId 请求拦截器（自动注入 X-Trace-Id / X-Span-Id / X-Parent-Span-Id） |
| `FeignTraceHandler` | Feign 追踪处理器 |
| `SkyWalkingTraceHandler` | SkyWalking 追踪处理器 |
| `FeignRequestInterceptor` | 通用请求拦截器（Auth Token / 租户 ID / 数据权限 / 用户偏好等 16 个请求头透传） |

### 4. 舱壁隔离（Bulkhead）

| 类 | 说明 |
|---|---|
| `BulkheadRequestInterceptor` | 舱壁隔离请求拦截器（基于信号量按服务维度限制 Feign 调用并发数，防止级联雪崩） |

### 5. 动态客户端与配置刷新

| 类 | 说明 |
|---|---|
| `DynamicFeignClientFactory` | 动态 Feign 客户端工厂（运行时创建 Feign 代理） |
| `FeignConfigRefresher` | Feign 配置刷新器（监听 `EnvironmentChangeEvent` → 客户端重建） |
| `FeignClientConstants` | Feign 客户端常量定义（客户端名称、上下文 key、默认路径常量） |
| `MethodAwareRetryer` | 方法感知重试器（按 HTTP 方法决定是否重试） |

### 6. 跨服务名称富化（NameAssembler）

| 类 / 接口 | 说明 |
|---|---|
| `NameAssembler` | 跨服务名称解析门面接口（ID → 名称富化，支持批量 / 单条 + 本地缓存） |
| `NameType` | 名称类型枚举（USER / DEPT / ROLE / POST / COMPANY） |
| `NoOpNameAssembler` | 默认空实现（直接返回 ID，不调用 Feign） |
| `NameAssemblerAutoConfiguration` | 名称组装器自动配置 |
| `NameAssemblerProperties` | 名称组装器配置属性 |

### 7. Gzip 压缩

| 类 | 说明 |
|---|---|
| `GzipRequestCompressInterceptor` | 请求 Gzip 压缩拦截器（按 Content-Type 排除 + 最小阈值） |

### 8. 监控与响应拦截

| 类 | 说明 |
|---|---|
| `FeignResponseInterceptor` | 响应拦截器（日志记录 + 指标采集 + 慢调用告警 + Bulkhead 许可释放） |
| `FeignResponseMetricsAdapter` | 响应指标适配器（Micrometer） |
| `FeignMicrometerCollector` | Micrometer 指标采集（请求数 / 延迟 / 错误率） |
| `FeignMetricsConfiguration` | 指标自动配置 |

### 9. 日志与异常体系

| 类 | 说明 |
|---|---|
| `YdszFeignLogger` | Feign 日志器（结构化日志 + traceId） |
| `YdszFeignErrorDecoder` | 统一错误解码器（HTTP 状态码 → 业务异常映射） |
| `OpenFeignException` | Feign 通用异常 |
| `NotFoundException` | 404 资源未找到异常 |
| `BadRequestException` | 400 参数错误异常 |

### 10. 预定义客户端与 DTO

| 接口 / 类 | 说明 |
|---|---|
| `NotificationClient` | 通知服务 Feign 客户端（send / sendMessage / pushRealtime / broadcast） |
| `MessageRequest` / `MessageResult` | 多通道消息请求 / 响应模型 |
| `NotificationFeignDTO` | 通知发送 DTO |
| `RealtimePushDTO` | 实时推送 DTO |

### 11. 健康检查

| 类 | 说明 |
|---|---|
| `FeignHealthIndicator` | Feign 客户端健康检查（暴露 `/actuator/health/feign`） |

### 12. 开关注解

| 注解 | 说明 |
|---|---|
| `@EnableYdszFeign` | 启用 ydsz Feign 增强自动装配（封装 `@EnableFeignClients`，默认扫描 `com.njydsz`） |

## 接入方式

### 1. POM 引入依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-feign</artifactId>
</dependency>
```

### 2. 配置启用

```yaml
ydsz:
  feign:
    enabled: true
    logger-level: BASIC                  # NONE / BASIC / HEADERS / FULL
    propagation:
      enabled: true                       # 请求头透传开关
    retry:
      enabled: true
      max-attempts: 3
    timeout:
      connect: 5000
      read: 10000
    circuit-breaker:
      enabled: true
      failure-rate-threshold: 50
    bulkhead:
      enabled: true
      default-max-concurrent-calls: 50
```

### 3. 代码启用

在 Spring Boot 主类上添加 `@EnableYdszFeign` 注解即可启用 Feign 增强自动装配：

```java
import com.njydsz.common.feign.annotation.EnableYdszFeign;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableYdszFeign(basePackages = "com.njydsz.order.client")
public class OrderApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }
}
```

## 配置项

### 总开关与日志（`ydsz.feign`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `enabled` | true | 模块总开关 |
| `logger-level` | BASIC | 日志级别：`NONE` / `BASIC` / `HEADERS` / `FULL` |

### 请求头透传（`ydsz.feign.propagation`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `enabled` | true | 是否启用请求头透传 |
| `headers` | 16 个默认头 | 透传的请求头名称集合（X-Access-Token / X-Tenant-Id / X-User-Language 等） |

### 重试（`ydsz.feign.retry`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `enabled` | true | 是否启用重试机制 |
| `max-attempts` | 3 | 最大重试次数（含首次调用） |
| `retry-on-methods` | GET | 重试的 HTTP 方法集合 |
| `backoff.delay` | 100 | 初始重试延迟（毫秒） |
| `backoff.max-delay` | 500 | 最大重试延迟（毫秒） |
| `backoff.multiplier` | 2.0 | 延迟倍数 |

### 超时（`ydsz.feign.timeout`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `connect` | 5000 | 连接超时（毫秒） |
| `read` | 10000 | 读取超时（毫秒） |

### per-client 超时（`ydsz.feign.client-timeouts`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `<clientName>.connect` | 全局值 | 指定客户端连接超时 |
| `<clientName>.read` | 全局值 | 指定客户端读取超时 |

### 错误处理（`ydsz.feign.error`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `include-body` | true | 异常信息是否包含响应体 |
| `max-body-bytes` | 4096 | 响应体最大字节数 |

### HTTP 客户端连接池（`ydsz.feign.client`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `max-connections` | 200 | 最大连接数 |
| `max-per-route` | 50 | 每路由最大连接数 |
| `keep-alive` | 30000 | 连接保持时间（毫秒） |
| `validate-after-inactivity` | 2000 | 空闲校验时间（毫秒） |
| `connection-time-to-live` | 300000 | 连接最大生命周期（毫秒） |

### Gzip 压缩（`ydsz.feign.compress`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `enabled` | false | 是否启用 Gzip 压缩 |
| `min-size` | 1024 | 最小压缩阈值（字节） |
| `excluded-content-types` | image/*,video/*,application/octet-stream 等 | 排除压缩的 Content-Type |

### Resilience4j 熔断器（`ydsz.feign.circuit-breaker`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `enabled` | false | 是否启用熔断器 |
| `failure-rate-threshold` | 50 | 失败率阈值（%） |
| `slow-call-rate-threshold` | 100 | 慢调用率阈值（%） |
| `slow-call-duration-threshold` | 3000 | 慢调用阈值（毫秒） |
| `permitted-number-of-calls-in-half-open-state` | 10 | 半开状态最大调用数 |
| `sliding-window-size` | 100 | 滑动窗口大小 |
| `sliding-window-type` | COUNT_BASED | 滑动窗口类型：`COUNT_BASED` / `TIME_BASED` |
| `wait-duration-in-open-state` | 60 | OPEN 状态等待时间（秒） |
| `state-ttl-seconds` | 3600 | 熔断状态 Redis 持久化 TTL（秒） |

### 舱壁隔离（`ydsz.feign.bulkhead`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `enabled` | false | 是否启用 Bulkhead 隔离 |
| `default-max-concurrent-calls` | 50 | 默认最大并发请求数 |
| `acquire-timeout-millis` | 100 | 信号量获取超时（毫秒，0=立即失败） |
| `client-config` | 空 | 按服务名定制的最大并发数（key=服务名，value=并发数） |

### 动态配置刷新（`ydsz.feign.refresh`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `enabled` | false | 是否启用动态配置刷新 |
| `exclude` | 空 | 排除刷新的客户端名称列表 |

### 响应拦截器（`ydsz.feign.response-interceptor`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `log-enabled` | true | 是否启用响应日志记录 |
| `metrics-enabled` | true | 是否启用响应指标采集 |
| `slow-call-threshold-millis` | 3000 | 慢调用阈值（毫秒，0=禁用） |

### 跨服务名称富化（`ydsz.feign.name-assembler`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `cache-ttl` | 5m | 本地缓存存活时间 |
| `cache-max-size` | 10000 | 本地缓存最大条目数 |
| `fallback-to-id` | true | Feign 失败时是否用 ID 顶替 name |

## 使用示例

### 1. 声明 Feign 客户端（带熔断降级）

```java
import com.njydsz.common.feign.FeignClientConstants;
import com.njydsz.common.feign.fallback.NotificationClientFallbackFactory;
import com.njydsz.common.core.response.BaseResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = FeignClientConstants.MESSAGE,
             contextId = "notificationClient",
             fallbackFactory = NotificationClientFallbackFactory.class)
public interface NotificationClient {

    @PostMapping(FeignClientConstants.MESSAGE_PATH_NOTIFICATION_SEND)
    BaseResponse<Void> send(@RequestBody NotificationFeignDTO dto);
}
```

### 2. 自定义降级工厂

```java
import com.njydsz.common.feign.fallback.DefaultFallbackFactory;
import org.springframework.stereotype.Component;

@Component
public class UserServiceFallbackFactory extends DefaultFallbackFactory<UserServiceClient> {

    @Override
    protected UserServiceClient createFallback(Throwable cause) {
        if (isServiceUnavailable(cause)) {
            log.warn("UserService 不可用，返回降级数据, cause={}", cause.getMessage());
            return new UserServiceClient() {
                @Override
                public User getUser(Long id) {
                    return User.DEFAULT;
                }
            };
        }
        return null;     // 返回 null 时由父类 createSafeFallback 生成安全降级代理
    }
}
```

### 3. 跨服务名称富化（批量）

```java
import com.njydsz.common.feign.assembler.NameAssembler;
import com.njydsz.common.feign.assembler.NameType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FlowInstanceService {
    private final NameAssembler nameAssembler;

    public void enrichInstanceNames(List<FlowInstanceVO> records) {
        // 一次 Feign 调用解决整页数据，自动兜底（失败时用 ID 顶替 name）
        nameAssembler.enrich(records,
                FlowInstanceVO::getInitiatorId,
                FlowInstanceVO::setInitiatorName,
                NameType.USER);
    }
}
```

### 4. 启用熔断 + 舱壁隔离

```yaml
ydsz:
  feign:
    circuit-breaker:
      enabled: true
      failure-rate-threshold: 50
      slow-call-duration-threshold: 3000
      wait-duration-in-open-state: 60
      state-ttl-seconds: 3600     # Redis 持久化 TTL
    bulkhead:
      enabled: true
      default-max-concurrent-calls: 50
      acquire-timeout-millis: 100
      client-config:
        message:
          max-concurrent-calls: 10
        user:
          max-concurrent-calls: 100
```

### 5. 动态配置刷新（Nacos 联动）

```yaml
ydsz:
  feign:
    refresh:
      enabled: true
      exclude:
        - specialClient         # 该客户端配置变更不重建
```

## SPI 扩展点

| SPI 接口 | 用途 | 实现方 |
|---|---|---|
| `FeignCircuitBreakerStrategy` | 熔断器策略抽象 | `Resilience4jCircuitBreakerAdapter`（基于 Resilience4j） |
| `NameAssembler` | 跨服务名称富化门面 | `NoOpNameAssembler`（默认空实现，业务方按需覆盖） |
| `DefaultFallbackFactory<T>` | 降级工厂抽象基类（继承 `FallbackFactory`） | `NotificationClientFallbackFactory`（业务方继承实现） |
| `FeignTraceHandler` | Feign 追踪处理器 | `SkyWalkingTraceHandler`（SkyWalking 集成） |

## 健康检查

| 端点 | 说明 | 触发条件 |
|---|---|---|
| `/actuator/health/feign` | Feign 模块健康检查：熔断器启用状态、重试策略、连接 / 读取超时、熔断器策略名称 | `ydsz.feign.enabled=true`（默认 true）+ classpath 存在 `HealthIndicator` |

健康检查返回示例：

```json
{
  "status": "UP",
  "details": {
    "module": "feign",
    "circuitBreakerEnabled": true,
    "retryEnabled": true,
    "connectTimeoutMs": 5000,
    "readTimeoutMs": 10000,
    "circuitBreakerStrategy": "Resilience4jCircuitBreakerAdapter"
  }
}
```

## 注意事项

1. **自动配置加载顺序**：`FeignConfiguration` → `FeignMetricsConfiguration` → `Resilience4jFeignConfiguration`（after=FeignConfiguration）→ `NameAssemblerAutoConfiguration`。Resilience4j 熔断器需显式 `ydsz.feign.circuit-breaker.enabled=true` 启用。
2. **请求头透传**：默认透传 16 个请求头（X-Access-Token / X-Tenant-Id / X-User-Language / X-Company-Ids / X-Dept-Ids / X-Data-Scope 等），可通过 `ydsz.feign.propagation.headers` 自定义。
3. **重试仅对 GET 默认生效**：`retry-on-methods` 默认仅 GET，避免对非幂等接口（POST/PUT/DELETE）重试导致重复写入。可通过配置扩展。
4. **熔断状态持久化**：Redis 可用时，熔断器状态会持久化到 Redis（TTL 由 `state-ttl-seconds` 控制），应用重启后恢复熔断状态。Redis 不可用时降级为内存态。
5. **舱壁隔离许可释放**：`BulkheadRequestInterceptor` 获取信号量后写入 ThreadLocal，由 `FeignResponseInterceptor` 在 `finally` 块中释放。启用 Bulkhead 时必须同时启用响应拦截器。
6. **动态配置刷新依赖 spring-cloud-context**：`FeignConfigRefresher` 监听 `EnvironmentChangeEvent`，需引入 `spring-cloud-context`（optional）。Nacos / Apollo 配置变更会自动触发 Feign 客户端重建。
7. **DefaultFallbackFactory 安全降级**：子类 `createFallback` 返回 null 时，父类自动生成动态代理，根据返回类型返回 `BaseResponse.error` / 空集合 / 默认值，**绝不返回 null**。
8. **ResponseUnwrapDecoder 自动解包**：Feign 接口返回类型直接声明为业务对象（如 `User`），解码器自动从 `BaseResponse<User>` 中提取 `data` 字段。错误响应会抛出 `OpenFeignException`。
9. **HttpClient 5 连接池**：替代旧版 `feign-httpclient`，禁用 Cookie / 重定向 / 自动重试（重试由 `Retryer` 统一控制），避免双层重试。空闲连接校验防止"僵尸连接"。
10. **可选依赖降级**：`micrometer-core` / `resilience4j-spring-boot3` / `common-redis` / `spring-cloud-context` 均为 optional / provided，未引入时对应能力自动降级或不可用。

## 变更记录

- **v1.0.0**（2026-08-02）：对标 common-jdbc 标准格式重构 README，补全全部 9 个章节，覆盖 12 项核心能力、9 个配置分组、4 个 SPI 接口、1 个 HealthIndicator。
