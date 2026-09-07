# ydsz-common-web

> YDSZ PC Web 端基座模块（L6 应用层）

继承 `common-base`，叠加 Spring Security 集成、WebAuthFilter 认证过滤器、Session 管理、API 版本路由、Multipart 文件上传、Webhook 调度、优雅停机、OpenAPI 配置、健康检查与指标采集等 PC Web 端特有能力。本模块与 `common-app` 是两个**平行**的应用层入口，后端微服务**统一使用本模块**。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L6 应用层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 提供 PC Web 服务的 MVC / 认证 / 会话 / API 版本 / Multipart / Webhook / 优雅停机等基座能力 |
| **依赖** | common-base（传递 common-core、common-util、common-exception、common-json、common-auth、common-safe）、common-domain、common-redis、common-excel；可选：spring-security、mybatis-plus、jasypt、redisson、knife4j、springdoc |
| **版本** | 26.09.01-SNAPSHOT |

## 核心能力

### 1. Spring MVC 配置

| 类 | 说明 |
|---|---|
| `WebMvcConfiguration` | MVC 配置（`@ConditionalOnWebApplication SERVLET`，继承 `BaseMvcConfiguration`，所有 Bean `@ConditionalOnMissingBean` 守卫） |
| `WebTimezoneConfiguration` | 时区配置 |
| `WebI18nConfiguration` | 国际化配置 |
| `WebOpenApiConfiguration` | OpenAPI 配置（YDSZ 品牌） |
| `WebCorsProperties` | CORS 配置（`@Validated`，前缀 `ydsz.web.cors`） |
| `WebTraceProperties` | Trace 配置（`@Validated`，前缀 `ydsz.web.trace`） |
| `WebContentCacheProperties` | 请求体缓存配置 |
| `FilterIgnoreProperties` | 过滤器忽略路径配置（`ydsz.web.filter-ignore`） |
| `WebCoreAutoConfiguration` | Web 核心自动配置（注册 `TenantMdcFilter` 等） |
| `UserAgentConfiguration` | UserAgent 解析配置（基于 yauaa 库，`@ConditionalOnProperty` 门控） |

### 2. 全局响应包装

| 类 | 说明 |
|---|---|
| `GlobalResponseAdvice` | 全局响应包装（`@ControllerAdvice`），装载 `BaseGlobalResponseAdvice` 子类 |
| `RequestLogInterceptor` | 请求日志拦截器 + HTTP 请求指标埋点（可选注入 `WebMetrics`） |

### 3. 认证体系

| 类 | 说明 |
|---|---|
| `WebSecurityConfiguration` | Spring Security 配置（`SecurityFilterChain` + 401/403 异常处理接入 + Session 伪造防护） |
| `WebAuthFilter` | Web 认证过滤器（Token 解析 → SecurityContext 设置 + 认证指标埋点） |
| `WebAuthHandler` / `AuthHandlerFactory` | 认证处理器 / 工厂（按 ServiceType 路由） |
| `WebAuthInfo` | Web 认证信息（继承 `BaseAuthInfo`，`getServiceTypeCode()` 返回 "WEB"） |
| `WebAuthenticationEntryPoint` | 未认证入口（401 JSON 响应） |
| `WebAccessDeniedHandler` | 权限不足处理器（403 JSON 响应） |

### 4. 会话管理

| 类 | 说明 |
|---|---|
| `WebSessionAutoConfiguration` | Session 自动配置（`@ConditionalOnWebApplication SERVLET`） |
| `RedisHttpSessionImportSelector` | Redis Session 导入选择器（按需引入 `spring-session-data-redis`） |

### 5. API 版本控制

支持基于 URL 路径的版本路由策略，通过自定义 `RequestMappingHandlerMapping` 在 Spring MVC 注册阶段注入版本匹配条件。

| 类 | 说明 |
|---|---|
| `@ApiVersion` (version 包) | 版本注解（标注在 Controller 类或方法上） |
| `ApiVersionCondition` | 版本路由条件（实现 `RequestCondition`，按策略从请求提取版本并匹配） |
| `ApiVersionRequestMappingHandlerMapping` | 自定义 `RequestMappingHandlerMapping` |
| `ApiVersionAutoConfiguration` | 自动配置（实现 `WebMvcRegistrations`，替换默认 `RequestMappingHandlerMapping`） |
| `ApiVersionProperties` | 配置属性（`ydsz.api.version.*`） |
| `VersionStrategy` | 版本提取策略枚举（当前仅 `URL`） |

### 6. Multipart 文件上传

覆盖 Spring Boot 默认 `MultipartAutoConfiguration`，将企业级默认值（1MB / 10MB）提升为 50MB / 100MB。

| 类 | 说明 |
|---|---|
| `WebMultipartAutoConfiguration` | 自动配置（`@AutoConfigureBefore(MultipartAutoConfiguration.class)`） |
| `WebMultipartProperties` | 配置属性（`ydsz.web.multipart.*`） |

### 7. Webhook 调度

统一 Webhook 投递能力，避免各业务模块重复实现 HTTP 投递、签名、重试逻辑。

| 类 | 说明 |
|---|---|
| `WebhookDispatcher` | 投递器接口（`register` / `unregister` / `dispatch`） |
| `DefaultWebhookDispatcher` | 默认实现（内存 `ConcurrentHashMap` + `RestTemplate` + HMAC-SHA256 签名 + 3 次指数退避重试） |
| `WebhookSubscription` | 订阅模型 |
| `WebhookProperties` | 配置属性（`ydsz.webhook.*`） |

### 8. 优雅停机

| 类 | 说明 |
|---|---|
| `WebGracefulShutdownAutoConfiguration` | 自动配置（注册 `ShutdownEventListener`） |
| `ShutdownEventListener` | 事件监听器（处理 `WebServerInitializedEvent` / `ContextClosedEvent` / `ApplicationFailedEvent`） |

### 9. 内部签名

| 类 | 说明 |
|---|---|
| `InternalSignatureAutoConfiguration` | 内部服务间签名自动配置 |
| `InternalSignatureProperties` | 内部签名配置属性 |
| `InternalSignatureFilter` | 内部签名过滤器 |

### 10. 过滤器链

| 类 | 说明 |
|---|---|
| `ContentCachingFilter` | 请求体缓存过滤器 |
| `TraceIdResponseFilter` | TraceId 响应过滤器 |
| `SecurityHeaderFilter` | 安全头过滤器（`@ConditionalOnBean` 守卫） |
| `WebAuthFilter` | Web 认证过滤器 |
| `TenantMdcFilter` | 租户 MDC 上下文过滤器 |

### 11. 异常处理

| 类 | 说明 |
|---|---|
| `ExcelMvcExceptionHandler` | Excel 异常桥接（补充 `common-exception` 的 `MvcExceptionHandler`） |

## 接入方式

### 1. POM 引入依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-web</artifactId>
</dependency>
```

> 传递引入 `common-base`、`common-auth`、`common-safe`、`common-redis`、`common-json`、`common-exception`、`common-domain`。

### 2. 启用方式

无需额外注解，自动配置通过 `@AutoConfiguration` + Spring Boot 自动装配机制激活。引入依赖后，PC Web 端微服务默认启用 MVC、认证、Trace、CORS、Multipart、优雅停机日志等能力。

### 3. 配置示例

```yaml
ydsz:
  web:
    security:
      enabled: true                  # Spring Security 开关（默认启用）
    session:
      enabled: false                  # Redis Session 开关（默认关闭）
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
    shutdown:
      log-enabled: true
  api:
    version:
      strategy: URL
      default-version: "1"
      current-version: v1
server:
  shutdown: graceful                  # 优雅停机（应用层启用）
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
```

## 配置项

### Security / Session / CORS / Trace

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.web.security.enabled` | `true` | Spring Security 开关 |
| `ydsz.web.session.enabled` | `false` | Redis Session 开关 |
| `ydsz.web.cors.enabled` | `true` | CORS 开关 |
| `ydsz.web.cors.allow-credentials` | `false` | 是否允许 Cookie |
| `ydsz.web.cors.allowed-origin-patterns` | — | 允许的来源模式 |
| `ydsz.web.cors.allowed-headers` | `["*"]` | 允许的请求头 |
| `ydsz.web.cors.allowed-methods` | `["GET","POST","PUT","DELETE","OPTIONS"]` | 允许的方法 |
| `ydsz.web.cors.max-age` | `3600` | 预检缓存秒数 |
| `ydsz.web.cors.path-pattern` | `/**` | CORS 路径模式 |
| `ydsz.web.trace.enabled` | `true` | Trace 开关 |
| `ydsz.web.trace.response-header-enabled` | `true` | 响应头输出 TraceId |
| `ydsz.web.trace.request-log-enabled` | `true` | 请求日志开关 |
| `ydsz.web.trace.log-level` | `INFO` | 日志级别 |
| `ydsz.web.trace.sampling-rate` | `1.0` | 采样率 [0, 1] |
| `ydsz.web.trace.log-request-body` | `false` | 记录请求体 |
| `ydsz.web.trace.log-response-body` | `false` | 记录响应体 |

### 内容缓存 / UserAgent / 健康检查 / 过滤器忽略

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.web.content-cache.max-size` | `2097152`（2MB） | 请求体缓存最大字节 |
| `ydsz.web.user-agent.enabled` | `true` | UserAgent 解析器开关 |
| `ydsz.web.health-indicator.enabled` | `true` | 健康检查开关 |
| `ydsz.web.filter-ignore.common-ignore-urls` | `[]` | 过滤器忽略路径列表 |
| `ydsz.web.filter-ignore.replace-builtin` | `false` | 是否整体替换内置默认值 |

### API 版本控制

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.web.api-version.enabled` | `false` | API 版本路由自动配置开关（默认禁用） |
| `ydsz.api.version.enabled` | `false` | 请求时是否执行版本匹配 |
| `ydsz.api.version.strategy` | `URL` | 版本提取策略（仅 URL） |
| `ydsz.api.version.default-version` | `"1"` | 请求未携带版本时的兜底版本 |
| `ydsz.api.version.header-name` | `X-API-Version` | HEADER 策略请求头名称 |
| `ydsz.api.version.current-version` | `v1` | 当前 API 版本标识 |

### Multipart 文件上传

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.web.multipart.enabled` | `true` | 统一 multipart 配置开关 |
| `ydsz.web.multipart.max-file-size` | `50MB` | 单文件最大大小 |
| `ydsz.web.multipart.max-request-size` | `100MB` | 整个请求最大大小 |
| `ydsz.web.multipart.file-size-threshold` | `0` | 写入磁盘的阈值 |
| `ydsz.web.multipart.resolve-lazily` | `false` | 是否延迟解析 |
| `ydsz.web.multipart.location` | `""` | 临时文件目录 |

### Webhook / 优雅停机 / 内部签名

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.webhook.enabled` | `true` | Webhook 调度器开关 |
| `ydsz.webhook.connect-timeout-ms` | `5000` | HTTP 连接超时 |
| `ydsz.webhook.read-timeout-ms` | `10000` | HTTP 读超时 |
| `ydsz.webhook.max-connections` | `50` | HTTP 连接池最大连接数 |
| `ydsz.webhook.max-connections-per-route` | `20` | 每个路由最大连接数 |
| `ydsz.web.shutdown.log-enabled` | `true` | 优雅停机日志开关 |

## SPI 扩展点

| SPI 接口 / 基类 | 默认实现 | 覆盖方式 |
|---|---|---|
| `WebhookDispatcher` | `DefaultWebhookDispatcher`（内存 + RestTemplate + HMAC-SHA256 + 3 次重试） | 业务方提供 `WebhookDispatcher` Bean |
| `AbstractAuthHandler` | `WebAuthHandler` | 业务方提供 `AbstractAuthHandler` 子类 Bean，由 `AuthHandlerFactory` 路由 |
| `GlobalResponseAdvice` | 全局响应包装 | 提供同类型 Bean |
| `ContentCachingFilter` | 请求体缓存 | 提供同名 `FilterRegistrationBean` |
| `WebAuthFilter` | Web 认证 | 提供同名 `FilterRegistrationBean` |
| `SecurityHeaderFilter` | 安全头 | 提供同名 `FilterRegistrationBean` |
| `TraceIdResponseFilter` | TraceId 响应 | 提供同名 `FilterRegistrationBean` |
| `RequestLogInterceptor` | 请求日志 + 指标 | 提供同类型 Bean |
| `WebMetrics` | Micrometer 指标采集 | 提供同类型 Bean |
| `WebHealthIndicator` | 健康检查 | 提供同类型 Bean |

## 健康检查

| 端点 | 说明 | 触发条件 |
|---|---|---|
| `/actuator/health` | Web 模块健康指标（`WebHealthIndicator`）：CORS / Trace / Session / Security / UserAgent 状态 | `spring-boot-health` + `ydsz.web.health-indicator.enabled=true` |

响应示例：

```json
{
  "status": "UP",
  "details": {
    "corsEnabled": true,
    "traceEnabled": true,
    "sessionStrategy": "none",
    "securityEnabled": true,
    "userAgentAnalyzerEnabled": true
  }
}
```

**Micrometer 指标：**

| 指标名 | 说明 | 标签 |
|---|---|---|
| `web.auth.total` | 认证请求总数 | `result` |
| `web.auth.duration` | 认证耗时分布 | — |
| `web.request.total` | HTTP 请求总数 | `method` / `status` |
| `web.request.duration` | HTTP 请求耗时分布 | `method` |
| `web.ratelimit.rejected` | 限流拒绝计数 | — |
| `web.security.header.injected` | 安全响应头注入计数 | — |

## 自动配置类

| 配置类 | 激活条件 |
|---|---|
| `WebMvcConfiguration` | Servlet Web 应用 + `@ConditionalOnPlatform(WEB)` |
| `WebSecurityConfiguration` | Spring Security 可用时激活 |
| `WebSessionAutoConfiguration` | Servlet Web 应用 + `spring-session-data-redis` 时激活 |
| `UserAgentConfiguration` | `yauaa` 在 classpath 时激活 |
| `ApiVersionAutoConfiguration` | Servlet Web + `ydsz.web.api-version.enabled=true`（默认禁用） |
| `WebMultipartAutoConfiguration` | Servlet Web + `MultipartConfigElement` in classpath + `ydsz.web.multipart.enabled=true` |
| `WebGracefulShutdownAutoConfiguration` | Servlet Web + `ydsz.web.shutdown.log-enabled=true` |
| `WebCoreAutoConfiguration` | `@ConditionalOnWebApplication` + `ydsz.core.enabled=true` |
| `InternalSignatureAutoConfiguration` | 内部签名配置激活时 |

注册入口：`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

```
com.njydsz.common.web.config.WebMvcConfiguration
com.njydsz.common.web.config.UserAgentConfiguration
com.njydsz.common.web.config.WebSecurityConfiguration
com.njydsz.common.web.config.WebOpenApiConfiguration
com.njydsz.common.web.config.WebTimezoneConfiguration
com.njydsz.common.web.config.WebI18nConfiguration
com.njydsz.common.web.config.WebSessionAutoConfiguration
com.njydsz.common.web.config.WebMultipartAutoConfiguration
com.njydsz.common.web.config.WebGracefulShutdownAutoConfiguration
com.njydsz.common.web.version.ApiVersionAutoConfiguration
com.njydsz.common.web.config.WebCoreAutoConfiguration
com.njydsz.common.web.config.InternalSignatureAutoConfiguration
```

## 注意事项

1. **API 版本配置前缀差异**：自动配置开关绑定 `ydsz.web.api-version.enabled`，路由属性绑定 `ydsz.api.version.*`，默认禁用，需显式开启。
2. **Multipart 覆盖**：启用后 `max-file-size` 默认提升至 50MB；设置 `ydsz.web.multipart.enabled=false` 回退 Spring Boot 默认。
3. **Webhook 内存态**：`DefaultWebhookDispatcher` 的订阅信息存于内存，应用重启后丢失；生产环境建议自定义实现持久化订阅。
4. **优雅停机需应用层启用**：本模块仅提供停机日志可观测性；真正的「拒绝新请求 + 等待在飞请求」需配置 `server.shutdown=graceful` + `spring.lifecycle.timeout-per-shutdown-phase`。
5. **异常处理归属**：异常统一由 `common-exception` 的 `MvcExceptionHandler` 负责，本模块仅保留 `ExcelMvcExceptionHandler` 桥接。
6. **Spring Security 集成**：`WebSecurityConfiguration` 通过 `@ConditionalOnClass` 按需激活；若业务方不需要安全能力，可排除 `spring-boot-starter-security` 依赖。

## 变更记录

- **26.09.01**（2026-08-17）：补全 `FilterIgnoreProperties` / `WebhookProperties` / `InternalSignatureAutoConfiguration` / `AbstractModuleHealthIndicator` / `WebCoreAutoConfiguration` 文档；修正 Webhook 默认超时时间
- **26.09.01**（2026-08-02）：补全 API 版本控制、Multipart 文件上传、Webhook 调度、优雅停机章节；新增接入方式、使用示例、注意事项章节；扩充配置项与自动配置表
