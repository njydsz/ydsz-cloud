# ydsz-common-web

YDSZ PC Web 端基座 — 继承 `common-base`，叠加 Spring Security 集成、WebAuthFilter 认证过滤器、Session 管理、异常处理、OpenAPI 配置、健康检查与指标采集。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L6 应用层 |
| **类型** | 公共依赖库（不独立部署） |
| **面向** | PC Web 端微服务（10 个部署单元中的 9 个） |

## 核心能力

### Spring Security 集成

| 类 | 说明 |
|---|---|
| `WebSecurityConfiguration` | Spring Security 配置（SecurityFilterChain + 异常处理接入） |
| `WebAuthFilter` | Web 认证过滤器（Token 解析 → SecurityContext 设置） |
| `WebAuthHandler` / `AuthHandlerFactory` | 认证处理器 / 工厂（按 ServiceType 路由） |
| `WebAuthInfo` | Web 认证信息 |
| `WebAuthenticationEntryPoint` | 未认证入口（401 JSON 响应） |
| `WebAccessDeniedHandler` | 权限不足处理器（403 JSON 响应） |

### 健康检查与指标

| 类 | 说明 |
|---|---|
| `WebHealthIndicator` | Actuator 健康检查（CORS/Trace/Session/Security/UserAgent 状态报告） |
| `WebMetrics` | Micrometer 指标采集（认证/请求/限流/安全头计数+耗时） |

### Session 管理

| 类 | 说明 |
|---|---|
| `WebSessionAutoConfiguration` | Session 自动配置（@ConditionalOnWebApplication SERVLET） |
| `RedisHttpSessionImportSelector` | Redis Session 导入选择器（按需引入 spring-session-data-redis） |

### 过滤器链

| 类 | 说明 |
|---|---|
| `TraceIdResponseFilter` | TraceId 响应过滤器 |
| `SecurityHeaderFilter` | 安全头过滤器 |
| `ContentCachingFilter` | 内容缓存过滤器（基于 WebContentCacheProperties 配置） |

### MVC 配置

| 类 | 说明 |
|---|---|
| `WebMvcConfiguration` | MVC 配置（继承 base，追加 Web 特有配置 + 所有过滤器 @ConditionalOnMissingBean） |
| `WebTimezoneConfiguration` | 时区配置 |
| `WebI18nConfiguration` | 国际化配置 |
| `WebOpenApiConfiguration` | OpenAPI 配置 |
| `WebCorsProperties` | CORS 配置（@Validated） |
| `WebTraceProperties` | Trace 配置（@Validated） |
| `WebContentCacheProperties` | 请求体缓存配置 |
| `UserAgentConfiguration` | UserAgent 解析配置（@ConditionalOnProperty 门控） |

### 拦截器与异常

| 类 | 说明 |
|---|---|
| `RequestLogInterceptor` | 请求日志拦截器（由 WebMvcConfiguration @Bean 注册） |
| `WebExceptionHandler` | Web 异常处理器（由 WebExceptionAutoConfiguration 装配依赖） |
| `WebExceptionAutoConfiguration` | 异常处理器自动配置（注入 ExceptionMetrics/Properties/AlertPublisher） |
| `GlobalResponseAdvice` | 全局响应包装 |

## 与 `common-base` 的关系

`common-web` 继承 `common-base` 的所有能力（CORS / 时区 / I18n / 安全头 / TraceId / 请求日志），额外增加：

1. **Spring Security** — 完整的 SecurityFilterChain 配置（401/403 异常处理接入 + Session 伪造防护）
2. **Web 认证** — `WebAuthFilter` 替代 `BaseAuthFilter`，集成 Spring Security
3. **Session** — Redis 分布式 Session 支持（@ConditionalOnWebApplication 门控）
4. **异常处理** — `WebExceptionHandler` + `WebAuthenticationEntryPoint` + `WebAccessDeniedHandler`（依赖注入完整）
5. **健康检查** — `WebHealthIndicator` 报告 CORS/Trace/Session/Security/UserAgent 状态
6. **指标采集** — `WebMetrics` 采集认证/请求/限流/安全头 Micrometer 指标

## 配置项

```yaml
ydsz:
  web:
    security:
      enabled: true              # Spring Security 开关（默认启用）
    session:
      enabled: true               # Redis Session 开关（默认关闭）
    cors:
      enabled: true              # CORS 跨域开关
      allow-credentials: false   # 是否允许 Cookie
      allowed-origin-patterns:   # 允许的来源模式
        - "https://*.example.com"
      allowed-headers: ["*"]     # 允许的请求头
      allowed-methods: ["*"]     # 允许的请求方法
      exposed-headers: []        # 暴露的响应头
      max-age: 3600              # 预检缓存秒数
      path-pattern: "/**"        # CORS 路径模式
      order: 0                   # 过滤器顺序
    trace:
      enabled: true              # Trace 开关
      response-header-enabled: true  # 响应头输出 TraceId
      request-log-enabled: true # 请求日志开关
      log-level: INFO            # 日志级别（INFO/DEBUG）
      sampling-rate: 1.0         # 采样率 [0, 1]
      log-request-body: false    # 记录请求体
      log-response-body: false   # 记录响应体
    content-cache:
      max-size: 2097152          # 请求体缓存最大字节（默认 2MB）
    user-agent:
      enabled: true              # UserAgent 解析器开关
    health-indicator:
      enabled: true              # 健康检查开关
```

## 自动配置

| 配置类 | 激活条件 |
|---|---|
| `WebMvcConfiguration` | 总是激活 |
| `WebSecurityConfiguration` | Spring Security 可用时激活 |
| `WebSessionAutoConfiguration` | Servlet Web 应用 + spring-session-data-redis 时激活 |
| `WebExceptionAutoConfiguration` | Servlet Web 应用时激活 |
| `UserAgentConfiguration` | yauaa 在 classpath 时激活 |

## 依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-web</artifactId>
</dependency>
```
