# ydsz-common-web

PMIS PC Web 端基座 — 继承 `common-base`，叠加 Spring Security 集成、WebAuthFilter 认证过滤器、Session 管理、异常处理、OpenAPI 配置。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L6 应用层 |
| **类型** | 公共依赖库（不独立部署） |
| **源文件数** | 22 |
| **面向** | PC Web 端微服务（10 个部署单元中的 9 个） |

## 核心能力

### Spring Security 集成

| 类 | 说明 |
|---|---|
| `WebSecurityConfiguration` | Spring Security 配置（SecurityFilterChain 定义） |
| `WebAuthFilter` | Web 认证过滤器（Token 解析 → SecurityContext 设置） |
| `WebAuthHandler` / `AuthHandlerFactory` | 认证处理器 / 工厂 |
| `WebAuthInfo` | Web 认证信息 |
| `WebAuthenticationEntryPoint` | 未认证入口（401 JSON 响应） |
| `WebAccessDeniedHandler` | 权限不足处理器（403 JSON 响应） |

### Session 管理

| 类 | 说明 |
|---|---|
| `WebSessionAutoConfiguration` | Session 自动配置 |
| `RedisHttpSessionImportSelector` | Redis Session 导入选择器（按需引入 spring-session-data-redis） |

### 过滤器链

| 类 | 说明 |
|---|---|
| `TraceIdResponseFilter` | TraceId 响应过滤器 |
| `SecurityHeaderFilter` | 安全头过滤器 |
| `ContentCachingFilter` | 内容缓存过滤器 |

### MVC 配置

| 类 | 说明 |
|---|---|
| `WebMvcConfiguration` | MVC 配置（继承 base，追加 Web 特有配置） |
| `WebTimezoneConfiguration` | 时区配置 |
| `WebI18nConfiguration` | 国际化配置 |
| `WebOpenApiConfiguration` | OpenAPI 配置 |
| `WebCorsProperties` | CORS 配置 |
| `WebTraceProperties` | Trace 配置 |
| `UserAgentConfiguration` | UserAgent 解析配置 |

### 拦截器与异常

| 类 | 说明 |
|---|---|
| `RequestLogInterceptor` | 请求日志拦截器 |
| `WebExceptionHandler` | Web 异常处理器 |
| `GlobalResponseAdvice` | 全局响应包装 |

## 与 `common-base` 的关系

`common-web` 继承 `common-base` 的所有能力（CORS / 时区 / I18n / 安全头 / TraceId / 请求日志），额外增加：

1. **Spring Security** — 完整的 SecurityFilterChain 配置
2. **Web 认证** — `WebAuthFilter` 替代 `BaseAuthFilter`，集成 Spring Security
3. **Session** — Redis 分布式 Session 支持
4. **异常处理** — `WebExceptionHandler` + `WebAuthenticationEntryPoint` + `WebAccessDeniedHandler`

## 配置项

```yaml
pmis:
  web:
    security:
      enabled: true
      ignore-urls:
        - /api/public/**
        - /swagger-ui/**
        - /v3/api-docs/**
        - /doc.html
      auth-filter:
        order: 100              # 过滤器顺序
    session:
      redis:
        enabled: true            # Redis Session 开关
        namespace: pmis:session  # Redis Key 前缀
        timeout: 30m             # Session 超时
```

## 自动配置

| 配置类 | 激活条件 |
|---|---|
| `WebMvcConfiguration` | 总是激活 |
| `WebSecurityConfiguration` | Spring Security 可用时激活 |
| `WebSessionAutoConfiguration` | 总是激活 |

## 依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-web</artifactId>
</dependency>
```
