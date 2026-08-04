# remi-common-web

REMI PC Web 端基座 — 继承 `common-base`，叠加 Spring Security 集成、WebAuthFilter 认证过滤器、Session 管理、API 版本路由、Multipart 文件上传、响应压缩、Webhook 调度、优雅停机、OpenAPI 配置、健康检查与指标采集。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L6 应用层 |
| **类型** | 公共依赖库（不独立部署） |
| **继承** | common-base |
| **面向** | PC Web 端微服务（10 个部署单元中的 9 个） |
| **作用** | 提供 PC Web 服务的 MVC / 认证 / 会话 / API 版本 / Multipart / 压缩 / Webhook / 优雅停机等基座能力 |

## 核心能力

### 1. Spring MVC 配置

| 类 | 说明 |
|---|---|
| `WebMvcConfiguration` | MVC 配置（`@ConditionalOnWebApplication SERVLET`，继承 `BaseMvcConfiguration`，所有 Bean `@ConditionalOnMissingBean` 守卫，构造器注入 `RequestLogInterceptor`） |
| `WebTimezoneConfiguration` | 时区配置 |
| `WebI18nConfiguration` | 国际化配置 |
| `WebOpenApiConfiguration` | OpenAPI 配置（REMI 品牌） |
| `WebCorsProperties` | CORS 配置（`@Validated`） |
| `WebTraceProperties` | Trace 配置（`@Validated`） |
| `WebContentCacheProperties` | 请求体缓存配置 |
| `UserAgentConfiguration` | UserAgent 解析配置（`@ConditionalOnProperty` 门控） |

### 2. 全局响应包装

| 类 | 说明 |
|---|---|
| `GlobalResponseAdvice` | 全局响应包装（由 `WebMvcConfiguration` `@Bean` + `@ConditionalOnMissingBean` 注册） |
| `RequestLogInterceptor` | 请求日志拦截器 + HTTP 请求指标埋点（由 `WebMvcConfiguration` 注册，可选注入 `WebMetrics`） |

### 3. 认证体系

| 类 | 说明 |
|---|---|
| `WebSecurityConfiguration` | Spring Security 配置（SecurityFilterChain + 401/403 异常处理接入 + Session 伪造防护） |
| `WebAuthFilter` | Web 认证过滤器（Token 解析 → SecurityContext 设置 + 认证指标埋点） |
| `WebAuthHandler` / `AuthHandlerFactory` | 认证处理器 / 工厂（按 ServiceType 路由，Bean 名称常量化） |
| `WebAuthInfo` | Web 认证信息 |
| `WebAuthenticationEntryPoint` | 未认证入口（401 JSON 响应） |
| `WebAccessDeniedHandler` | 权限不足处理器（403 JSON 响应） |

### 4. 会话管理

| 类 | 说明 |
|---|---|
| `WebSessionAutoConfiguration` | Session 自动配置（`@ConditionalOnWebApplication SERVLET`） |
| `RedisHttpSessionImportSelector` | Redis Session 导入选择器（按需引入 `spring-session-data-redis`） |

### 5. API 版本控制

支持基于 URL 路径、请求头、Accept 头三种版本路由策略，通过自定义 `RequestMappingHandlerMapping` 在 Spring MVC 注册阶段注入版本匹配条件。

| 类 | 说明 |
|---|---|
| `ApiVersion` | 版本注解（标注在 Controller 类或方法上，指定支持的 API 版本，如 `"1.0"` / `"2"`） |
| `ApiVersionCondition` | 版本路由条件（实现 `RequestCondition`，按策略从请求提取版本并与注解版本匹配，支持主版本兼容匹配，版本号大者优先） |
| `ApiVersionRequestMappingHandlerMapping` | 自定义 `RequestMappingHandlerMapping`（在 `getCustomMethodCondition` / `getCustomTypeCondition` 中扫描 `@ApiVersion` 并返回 `ApiVersionCondition`） |
| `ApiVersionAutoConfiguration` | 自动配置（实现 `WebMvcRegistrations`，替换默认 `RequestMappingHandlerMapping`） |
| `ApiVersionProperties` | 配置属性（策略 / 默认版本 / 头名称 / 废弃版本 / Sunset 头等） |
| `VersionStrategy` | 版本提取策略枚举（`URL` / `HEADER` / `ACCEPT`） |

**版本提取策略：**

- `URL`：从 URL 路径提取，如 `/v1/api/users` → `"1"`（正则 `/v(\d+(?:\.\d+)?)`）
- `HEADER`：从请求头提取，默认头名 `X-API-Version`，如 `X-API-Version: 1.0` → `"1.0"`
- `ACCEPT`：从 Accept 头提取，如 `application/vnd.remi.v1+json` → `"1"`

**匹配规则：**

- 未携带版本信息时使用 `default-version` 兜底
- 支持主版本兼容匹配：`"1"` 匹配 `"1.0"`，`"1.0"` 匹配 `"1"`
- 多个候选版本时版本号大者优先（`v2` > `v1`）

### 6. Multipart 文件上传

覆盖 Spring Boot 默认的 `MultipartAutoConfiguration`，将企业级偏小的默认值（1MB / 10MB）提升为更合理的 50MB / 100MB，并暴露统一配置入口。

| 类 | 说明 |
|---|---|
| `WebMultipartAutoConfiguration` | 自动配置（`@AutoConfigureBefore(MultipartAutoConfiguration.class)`，注册 `MultipartConfigElement` Bean，`@ConditionalOnMissingBean` 守卫用户自定义） |
| `WebMultipartProperties` | 配置属性（max-file-size / max-request-size / file-size-threshold / resolve-lazily / location） |

**覆盖关系：**

- 本配置在 Spring Boot `MultipartAutoConfiguration` 之前生效
- 通过 `@ConditionalOnMissingBean(MultipartConfigElement.class)` 避免覆盖用户自定义
- 业务方仍可通过自定义 `MultipartConfigElement` Bean 进一步覆盖
- 设置 `remi.web.multipart.enabled=false` 可回退到 Spring Boot 默认

### 7. 响应压缩

基于 GZIP 对符合条件的 HTTP 响应进行压缩，减少网络传输量。

| 类 | 说明 |
|---|---|
| `ResponseCompressionConfiguration` | 自动配置（注册 `ResponseCompressionFilter`，过滤器顺序 `HIGHEST_PRECEDENCE + 100`，确保在大多数过滤器之后执行） |
| `ResponseCompressionProperties` | 配置属性（enabled / min-response-size / mime-types / excluded-user-agents） |
| `ResponseCompressionFilter` | GZIP 压缩过滤器（包装响应输出流，按条件压缩） |
| `WebContentCacheProperties` | 请求体缓存属性（控制 `ContentCachingFilter` 最大缓存字节，默认 2MB，防 OOM） |

**压缩条件（全部满足才压缩）：**

- 响应体大小 ≥ `min-response-size`（默认 2KB，避免小响应压缩后反而变大）
- 响应 `Content-Type` 在 `mime-types` 列表中（默认包含 JSON / XML / HTML / CSS / JS / SVG 等 10 种）
- 客户端 `Accept-Encoding` 包含 `gzip`
- `User-Agent` 不在 `excluded-user-agents` 列表中（默认排除 IE6 / Netscape 4）

### 8. Webhook 调度

提供 Webhook 统一投递能力，避免各业务模块（message / workflow / project 等）重复实现 HTTP 投递、签名、重试逻辑。

| 类 | 说明 |
|---|---|
| `WebhookDispatcher` | 投递器接口（`register` / `unregister` / `dispatch`） |
| `DefaultWebhookDispatcher` | 默认实现（内存 `ConcurrentHashMap` 管理订阅，`RestTemplate` 投递，HMAC-SHA256 签名，3 次指数退避重试，`@ConditionalOnMissingBean` 守卫允许业务方覆盖） |
| `WebhookSubscription` | 订阅模型（id / callbackUrl / eventTypes / secret / enabled / sourceModule，`@Builder` 构建） |

**投递流程：**

1. 筛选 `enabled=true` 且 `eventTypes` 包含目标事件的订阅
2. 将 `payload` 序列化为 JSON 后 POST 到 `callbackUrl`
3. 在 HTTP Header 附带 `X-Webhook-Signature`（`HMAC-SHA256(payload, secret)` 的 Base64 编码）
4. 投递失败按指数退避重试（默认 3 次，退避间隔 `1s * attempt^2`），最终失败记录日志

> **扩展点**：`DefaultWebhookDispatcher` 通过 `@ConditionalOnMissingBean(WebhookDispatcher.class)` 注册，业务方可自定义 `WebhookDispatcher` Bean 覆盖默认实现（如基于 Redis 持久化订阅、异步线程池投递、消息队列削峰等）。

### 9. 优雅停机

提供 Web 端优雅停机的可观测性支持，监听容器生命周期事件并输出日志。

| 类 | 说明 |
|---|---|
| `WebGracefulShutdownAutoConfiguration` | 自动配置（注册 `ShutdownEventListener`） |
| `ShutdownEventListener` | 事件监听器（实现 `ApplicationListener<ApplicationEvent>`，统一处理三类事件） |

**监听事件：**

- `WebServerInitializedEvent`：Web 服务就绪，输出端口与 contextPath
- `ContextClosedEvent`：上下文开始关闭，提示等待在飞请求完成
- `ApplicationFailedEvent`：应用启动失败，输出异常堆栈

> **使用前提**：本配置仅提供停机可观测性日志。真正的「优雅停机」需要应用层显式启用 `server.shutdown=graceful` 并配置 `spring.lifecycle.timeout-per-shutdown-phase`，Spring Boot 的 Web 服务器（Tomcat / Jetty / Undertow）会拒绝新请求并等待在飞请求完成。设置 `remi.web.shutdown.log-enabled=false` 可关闭停机日志。

### 10. 过滤器链

| 类 | 说明 |
|---|---|
| `TraceIdResponseFilter` | TraceId 响应过滤器 |
| `SecurityHeaderFilter` | 安全头过滤器（`@ConditionalOnBean` 守卫） |
| `ContentCachingFilter` | 内容缓存过滤器（基于 `WebContentCacheProperties` 配置） |
| `WebAuthFilter` | Web 认证过滤器 |
| `ResponseCompressionFilter` | GZIP 响应压缩过滤器 |

### 11. 配置族

| 类 | 说明 |
|---|---|
| `WebCorsProperties` | CORS 跨域配置 |
| `WebI18nConfiguration` | 国际化配置 |
| `WebTimezoneConfiguration` | 时区配置 |
| `WebTraceProperties` | Trace 配置 |
| `WebOpenApiConfiguration` | OpenAPI 配置 |
| `WebContentCacheProperties` | 请求体缓存配置 |

### 健康检查与指标

| 类 | 说明 |
|---|---|
| `WebHealthIndicator` | Actuator 健康检查（CORS / Trace / Session / Security / UserAgent 状态报告） |
| `WebMetrics` | Micrometer 指标采集（认证 / 请求计数 + 耗时，接入 `WebAuthFilter` + `RequestLogInterceptor` 调用链） |

### 异常处理

异常处理由 `common-exception` 模块的 `MvcExceptionHandler` 统一负责（15+ 个 `@ExceptionHandler` 方法，i18n + 动态 HTTP 状态码 + ProblemDetail 格式切换），本模块不再注册独立的异常处理器，避免重复设计。

## 接入方式

### 1. POM 引入依赖

```xml
<dependency>
    <groupId>com.remisoft</groupId>
    <artifactId>remi-common-web</artifactId>
</dependency>
```

### 2. 启动类

无需额外注解，自动配置通过 `@AutoConfiguration` + Spring Boot 自动装配机制激活。引入依赖后，PC Web 端微服务默认启用 MVC、认证、Trace、CORS、API 版本、Multipart、压缩、优雅停机日志等能力。

### 3. 配置示例

```yaml
remi:
  web:
    security:
      enabled: true                  # Spring Security 开关（默认启用）
    session:
      enabled: true                   # Redis Session 开关（默认关闭）
    cors:
      enabled: true
      allowed-origin-patterns:
        - "https://*.example.com"
    trace:
      enabled: true
      response-header-enabled: true
    content-cache:
      max-size: 2097152               # 请求体缓存最大字节（默认 2MB）
    multipart:
      enabled: true
      max-file-size: 50MB
      max-request-size: 100MB
    compression:
      enabled: true
      min-response-size: 2048
    shutdown:
      log-enabled: true
    api-version:
      enabled: true                   # API 版本路由自动配置开关
  api:
    version:
      strategy: URL                   # 版本提取策略
      default-version: "1"
      header-name: X-API-Version
      current-version: v1
      sunset-headers: true
      sunset-duration-days: 90
server:
  shutdown: graceful                  # 优雅停机（应用层启用）
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s   # 单阶段超时
```

### 4. 自动配置

引入依赖后，各自动配置类在满足条件时自动激活（均带 `@ConditionalOnMissingBean` 守卫，业务方可覆盖）：

| 配置类 | 激活条件 |
|---|---|
| `WebMvcConfiguration` | Servlet Web 应用 |
| `WebSecurityConfiguration` | Spring Security 可用时激活 |
| `WebSessionAutoConfiguration` | Servlet Web 应用 + `spring-session-data-redis` 时激活 |
| `UserAgentConfiguration` | `yauaa` 在 classpath 时激活 |
| `ApiVersionAutoConfiguration` | Servlet Web 应用 + `remi.web.api-version.enabled=true`（默认启用） |
| `WebMultipartAutoConfiguration` | Servlet Web 应用 + `MultipartConfigElement` 在 classpath + `remi.web.multipart.enabled=true`（默认启用） |
| `ResponseCompressionConfiguration` | Servlet Web 应用 + `remi.web.compression.enabled=true`（默认启用） |
| `WebGracefulShutdownAutoConfiguration` | Servlet Web 应用 + `remi.web.shutdown.log-enabled=true`（默认启用） |

## 配置项

### Security / Session / CORS / Trace

| 配置 | 默认值 | 说明 |
|---|---|---|
| `remi.web.security.enabled` | `true` | Spring Security 开关 |
| `remi.web.session.enabled` | `false` | Redis Session 开关 |
| `remi.web.cors.enabled` | `true` | CORS 跨域开关 |
| `remi.web.cors.allow-credentials` | `false` | 是否允许 Cookie |
| `remi.web.cors.allowed-origin-patterns` | — | 允许的来源模式列表 |
| `remi.web.cors.allowed-headers` | `["*"]` | 允许的请求头 |
| `remi.web.cors.allowed-methods` | `["*"]` | 允许的请求方法 |
| `remi.web.cors.exposed-headers` | `[]` | 暴露的响应头 |
| `remi.web.cors.max-age` | `3600` | 预检缓存秒数 |
| `remi.web.cors.path-pattern` | `/**` | CORS 路径模式 |
| `remi.web.cors.order` | `0` | 过滤器顺序 |
| `remi.web.trace.enabled` | `true` | Trace 开关 |
| `remi.web.trace.response-header-enabled` | `true` | 响应头输出 TraceId |
| `remi.web.trace.request-log-enabled` | `true` | 请求日志开关 |
| `remi.web.trace.log-level` | `INFO` | 日志级别（INFO / DEBUG） |
| `remi.web.trace.sampling-rate` | `1.0` | 采样率 [0, 1] |
| `remi.web.trace.log-request-body` | `false` | 记录请求体 |
| `remi.web.trace.log-response-body` | `false` | 记录响应体 |

### 内容缓存 / UserAgent / 健康检查

| 配置 | 默认值 | 说明 |
|---|---|---|
| `remi.web.content-cache.max-size` | `2097152`（2MB） | 请求体缓存最大字节，超过不缓存（防 OOM） |
| `remi.web.user-agent.enabled` | `true` | UserAgent 解析器开关 |
| `remi.web.health-indicator.enabled` | `true` | 健康检查开关 |

### API 版本控制

| 配置 | 默认值 | 说明 |
|---|---|---|
| `remi.web.api-version.enabled` | `true` | API 版本路由自动配置开关（`@ConditionalOnProperty` 门控） |
| `remi.api.version.enabled` | `true` | 请求时是否执行版本匹配（关闭后所有请求直接放行） |
| `remi.api.version.strategy` | `URL` | 版本提取策略（`URL` / `HEADER` / `ACCEPT`） |
| `remi.api.version.default-version` | `"1"` | 请求未携带版本时的兜底版本 |
| `remi.api.version.header-name` | `X-API-Version` | `HEADER` 策略下的请求头名称 |
| `remi.api.version.current-version` | `v1` | 当前 API 版本标识 |
| `remi.api.version.deprecated-versions` | `[]` | 已废弃版本列表（返回 410 Gone） |
| `remi.api.version.sunset-headers` | `true` | 是否输出 Deprecation / Sunset 头（RFC 8594） |
| `remi.api.version.sunset-duration-days` | `90` | 废弃过渡期天数 |

### Multipart 文件上传

| 配置 | 默认值 | 说明 |
|---|---|---|
| `remi.web.multipart.enabled` | `true` | 统一 multipart 配置开关 |
| `remi.web.multipart.max-file-size` | `50MB` | 单文件最大大小 |
| `remi.web.multipart.max-request-size` | `100MB` | 整个请求最大大小（含所有文件 + 表单字段） |
| `remi.web.multipart.file-size-threshold` | `0` | 写入磁盘的阈值（0 = 全部内存） |
| `remi.web.multipart.resolve-lazily` | `false` | 是否延迟解析 |
| `remi.web.multipart.location` | `""` | 临时文件目录（空 = Servlet 容器默认） |

### 响应压缩

| 配置 | 默认值 | 说明 |
|---|---|---|
| `remi.web.compression.enabled` | `true` | 响应压缩开关 |
| `remi.web.compression.min-response-size` | `2048`（2KB） | 最小响应体大小，小于不压缩 |
| `remi.web.compression.mime-types` | JSON / XML / HTML / CSS / JS / SVG 等 10 种 | 需要压缩的 MIME 类型列表 |
| `remi.web.compression.excluded-user-agents` | `["MSIE 6", "Mozilla/4"]` | 排除压缩的 User-Agent 模式 |

### Webhook / 优雅停机

| 配置 | 默认值 | 说明 |
|---|---|---|
| `remi.web.shutdown.log-enabled` | `true` | 优雅停机日志开关 |

> Webhook 调度器（`DefaultWebhookDispatcher`）基于内存订阅表与 `RestTemplate`，无独立配置属性；订阅通过 `WebhookDispatcher.register(...)` 编程式注册。

## 使用示例

### 1. API 版本控制

标注 `@ApiVersion` 在 Controller 类或方法上，配合 `@RequestMapping` 中含 `{version}` 占位符或独立路径使用：

```java
import com.remisoft.common.web.version.ApiVersion;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @GetMapping
    @ApiVersion("1.0")
    public UserVO getUserV1() {
        // v1.0 实现
        return new UserVO();
    }

    @GetMapping
    @ApiVersion("2.0")
    public UserVO getUserV2() {
        // v2.0 实现
        return new UserVO();
    }
}
```

按 `strategy` 配置，客户端通过以下任一方式指定版本：

- `URL`：`GET /v1/api/users` 或 `GET /v2/api/users`
- `HEADER`：`GET /api/users` + 请求头 `X-API-Version: 1.0`
- `ACCEPT`：`GET /api/users` + `Accept: application/vnd.remi.v1+json`

### 2. Webhook 订阅与投递

业务模块注入 `WebhookDispatcher`，注册订阅并在事件发生时投递：

```java
import com.remisoft.common.webhook.WebhookDispatcher;
import com.remisoft.common.webhook.WebhookSubscription;

import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class MessageWebhookService {

    private final WebhookDispatcher webhookDispatcher;

    public MessageWebhookService(WebhookDispatcher webhookDispatcher) {
        this.webhookDispatcher = webhookDispatcher;
    }

    public void init() {
        WebhookSubscription subscription = WebhookSubscription.builder()
                .id("sub-001")
                .callbackUrl("https://example.com/webhook")
                .eventTypes("MESSAGE_SENT,MESSAGE_FAILED")
                .secret("your-hmac-secret")
                .enabled(true)
                .sourceModule("message")
                .build();
        webhookDispatcher.register(subscription);
    }

    public void onMessageSent(Long messageId) {
        Map<String, Object> payload = Map.of("messageId", messageId, "event", "SENT");
        webhookDispatcher.dispatch("MESSAGE_SENT", payload);
    }
}
```

> 自定义实现：业务方提供 `WebhookDispatcher` Bean 即可覆盖默认实现（如基于 Redis 持久化订阅、异步线程池投递）。

### 3. 自定义认证

实现 `WebAuthHandler` 并注册为 Bean，由 `AuthHandlerFactory` 按 ServiceType 路由。具体接入方式参考 `common-auth` 模块文档。

## SPI 扩展点

本模块通过 Spring `@ConditionalOnMissingBean` 机制提供以下可覆盖的扩展点，业务方可按需替换默认实现。

### 1. Webhook 投递 SPI

| SPI 接口 | 默认实现 | 覆盖方式 |
|---|---|---|
| `WebhookDispatcher` | `DefaultWebhookDispatcher`（内存订阅表 + `RestTemplate` + HMAC-SHA256 签名 + 3 次指数退避重试） | 业务方提供 `WebhookDispatcher` Bean |

**扩展场景**：Redis 持久化订阅、异步线程池投递、消息队列削峰。

### 2. 认证处理器 SPI（模板方法模式）

| SPI 基类 | 默认实现 | 覆盖方式 |
|---|---|---|
| `AbstractAuthHandler`（来自 `common-auth`） | `WebAuthHandler`（`@Component("webAuthHandler")`，仅提供 `WebAuthInfo` 实例创建，解析逻辑由基类统一处理） | 业务方提供 `AbstractAuthHandler` 子类 Bean，由 `AuthHandlerFactory` 按 ServiceType 路由 |

### 3. Spring MVC 拦截器扩展

`WebMvcConfiguration` 继承 `BaseMvcConfiguration` 并重写 `addInterceptors`，注册 Web 端专属拦截器：

- `RequestLogInterceptor`（请求日志 + HTTP 指标埋点，order = `INTERCEPTOR_REQUEST_LOG`）
- `BaseHttpInterceptor`（请求上下文清理，order = `REQUEST_CONTEXT_CLEANUP`）

业务方实现 `WebMvcConfigurer` 或继承 `WebMvcConfiguration` 可追加自定义拦截器。

### 4. 可覆盖的 Bean（`@ConditionalOnMissingBean` 守卫）

| Bean | 作用 | 覆盖方式 |
|---|---|---|
| `GlobalResponseAdvice` | 全局响应包装 | 提供同类型 Bean |
| `ContentCachingFilter` | 请求体缓存过滤器 | 提供 `FilterRegistrationBean` 同名 Bean |
| `WebAuthFilter` | Web 认证过滤器 | 提供 `FilterRegistrationBean` 同名 Bean |
| `SecurityHeaderFilter` | 安全头过滤器 | 提供 `FilterRegistrationBean` 同名 Bean |
| `TraceIdResponseFilter` | TraceId 响应过滤器 | 提供 `FilterRegistrationBean` 同名 Bean |
| `RequestLogInterceptor` | 请求日志 + 指标埋点 | 提供同类型 Bean |
| `WebMetrics` | Micrometer 指标采集 | 提供同类型 Bean |
| `WebHealthIndicator` | 健康检查（见下一章） | 提供同类型 Bean |

### 5. OpenAPI 定制

`WebOpenApiConfiguration` 提供 REMI 品牌的 OpenAPI 配置，业务方可通过 `OpenApiCustomizer` Bean 追加自定义文档元数据。

## 健康检查

`WebHealthIndicator` 实现 Spring Boot `HealthIndicator`，在 `/actuator/health` 端点下暴露 Web 基座核心能力状态。

**激活条件：**
- `org.springframework.boot.health.contributor.HealthIndicator` 类在 classpath（Spring Boot Actuator 可用）
- `remi.web.health-indicator.enabled=true`（默认启用）

**报告项：**

| 检查维度 | 字段 | 来源 |
|---|---|---|
| CORS 跨域 | `corsEnabled` / `corsAllowCredentials` / `corsOriginCount` | `WebCorsProperties` |
| Trace 追踪 | `traceEnabled` / `traceResponseHeaderEnabled` / `traceRequestLogEnabled` / `traceSamplingRate` | `WebTraceProperties` |
| Session 策略 | `sessionStrategy`（`redis` / `none`） | 上下文中是否存在 `SessionRepository` Bean |
| Security | `securityEnabled` | 上下文中是否存在 `SecurityFilterChain` Bean |
| UserAgent 解析器 | `userAgentAnalyzerEnabled` / `userAgentCacheSize` | `UserAgentAnalyzer` Bean 是否存在 |

**响应示例：**

```json
{
  "status": "UP",
  "details": {
    "corsEnabled": true,
    "corsAllowCredentials": false,
    "corsOriginCount": 1,
    "traceEnabled": true,
    "traceResponseHeaderEnabled": true,
    "traceRequestLogEnabled": true,
    "traceSamplingRate": 1.0,
    "sessionStrategy": "none",
    "securityEnabled": true,
    "userAgentAnalyzerEnabled": true,
    "userAgentCacheSize": 10000
  }
}
```

**关闭健康检查：**

```yaml
remi:
  web:
    health-indicator:
      enabled: false
```

**关联指标采集：**

`WebMetrics`（Micrometer）在 `WebAuthFilter` 与 `RequestLogInterceptor` 调用链中埋点，提供以下指标：

| 指标名 | 说明 | 标签 |
|---|---|---|
| `web.auth.total` | 认证请求总数 | `result=success/failure` |
| `web.auth.duration` | 认证耗时分布 | — |
| `web.request.total` | HTTP 请求总数 | `method` / `status` |
| `web.request.duration` | HTTP 请求耗时分布 | `method` |
| `web.ratelimit.rejected` | 限流拒绝计数 | — |
| `web.security.header.injected` | 安全响应头注入计数 | — |

> 指标注册采用惰性创建模式，首次调用时注册到 `MeterRegistry`，后续复用已注册的 Counter/Timer 实例。

## 注意事项

1. **API 版本配置前缀差异**：自动配置开关通过 `remi.web.api-version.enabled` 控制（`@ConditionalOnProperty`），而版本路由属性（策略 / 默认版本 / 废弃版本等）绑定到 `remi.api.version.*` 前缀（`@ConfigurationProperties`）。两者前缀不同，配置时需分别填写。
2. **Multipart 与 Spring Boot 默认**：启用本模块后 `max-file-size` 默认提升至 50MB。如需回退 Spring Boot 默认（1MB / 10MB），设置 `remi.web.multipart.enabled=false`。
3. **响应压缩与 Tomcat 内置压缩**：本模块的 `ResponseCompressionFilter` 与 Servlet 容器内置的 `server.compression.*` 是两套独立机制，建议二选一，避免重复压缩。
4. **Webhook 默认实现为内存态**：`DefaultWebhookDispatcher` 的订阅信息存于内存 `ConcurrentHashMap`，应用重启后丢失。生产环境建议自定义实现持久化订阅。
5. **Webhook 依赖 RestTemplate**：默认实现通过 `ObjectProvider<RestTemplate>` 获取 `RestTemplate`，若上下文未配置将跳过投递并输出警告日志。
6. **优雅停机需应用层启用**：本模块仅提供停机日志可观测性，真正的「拒绝新请求 + 等待在飞请求」需配置 `server.shutdown=graceful` + `spring.lifecycle.timeout-per-shutdown-phase`。
7. **异常处理归属**：本模块不注册独立异常处理器，统一由 `common-exception` 的 `MvcExceptionHandler` 负责，避免重复设计。

## 变更记录

- **v1.0.0**（2026-08-02）：补全 API 版本控制、Multipart 文件上传、响应压缩、Webhook 调度、优雅停机五大块章节；新增接入方式、使用示例、注意事项章节；扩充配置项与自动配置表。
