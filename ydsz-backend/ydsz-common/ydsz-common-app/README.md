# ydsz-common-app

YDSZ 移动端 App 基座 — 继承 `common-base`，叠加 App 认证、请求体缓存、请求追踪、健康检查、指标采集。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L6 应用层 |
| **类型** | 公共依赖库（不独立部署） |
| **源文件数** | 17 |
| **面向** | 移动端 App 后端服务（未来移动端项目使用） |

## 核心能力

### App 认证

| 类 | 说明 |
|---|---|
| `AppAuthFilter` | App 认证过滤器（Token 解析 → 上下文设置） |
| `AppAuthHandler` | App 认证处理器（由 `AppMvcConfiguration` 注册为 Bean） |
| `AppAuthInfo` | App 认证信息（appId / userId / deviceInfo） |

### 过滤器链

| 类 | 说明 |
|---|---|
| `AppContentCachingFilter` | 内容缓存过滤器（请求体多次读取支持） |
| `AppRequestIdResponseFilter` | 请求 ID 响应过滤器 |
| `AppAuthFilter` | App 认证过滤器 |

> **API 签名验证**和**安全响应头**由 `ydsz-common-safe` 模块统一提供：
> - 签名验证：`ydsz.safe.api-signature.enabled=true` 启用（HMAC-SHA256 + 时间戳 + Nonce 防重放）
> - 安全响应头：`ydsz.safe.security-headers.enabled=true` 启用（默认启用）
>
> 配置参考 `ydsz-common-safe` 模块文档。

### 健康检查与指标

| 类 | 说明 |
|---|---|
| `AppHealthIndicator` | 健康检查（签名配置状态 / 指标状态） |
| `AppMetrics` | Micrometer 指标采集（签名验证计数/耗时、认证计数） |

### MVC 配置

| 类 | 说明 |
|---|---|
| `AppMvcConfiguration` | MVC 配置（继承 base，追加 App 特有配置） |
| `AppTimezoneConfiguration` | 时区配置 |
| `AppI18nConfiguration` | 国际化配置 |
| `AppOpenApiConfiguration` | OpenAPI 配置 |
| `AppCorsProperties` | CORS 配置 |
| `AppTraceProperties` | Trace 配置 |
| `AppContentCacheProperties` | 请求体缓存配置 |

### 拦截器与异常

| 类 | 说明 |
|---|---|
| `AppRequestLogInterceptor` | 请求日志拦截器 |
| `AppExceptionHandler` | App 异常处理器 |
| `AppGlobalResponseAdvice` | 全局响应包装 |

### 工具

| 类 | 说明 |
|---|---|
| `RequestIdGenerator` | 请求 ID 生成器 |

## 与 `common-base` 的关系

`common-app` 继承 `common-base` 的所有能力，额外增加：

1. **App 认证** — `AppAuthFilter` + `AppAuthHandler`
2. **请求体缓存** — 可配置大小的 `AppContentCachingFilter`
3. **健康检查** — `AppHealthIndicator` 报告签名配置和指标状态
4. **指标采集** — `AppMetrics` Micrometer 指标暴露到 Prometheus

> **注意**：`common-web` 与 `common-app` 是两个**平行**的应用层入口。后端微服务统一使用 `common-web`，`common-app` 仅用于未来移动端项目。

## 配置项

```yaml
ydsz:
  app:
    enabled: true                          # 模块开关（默认 true）
    content-cache:
      max-size: 2097152                     # 请求体最大缓存（字节，默认 2MB）
    cors:
      enabled: true
      allowed-origin-patterns:
        - "capacitor://localhost"
        - "http://localhost"
    trace:
      enabled: true
      response-header-enabled: true
  # API 签名验证（由 ydsz-common-safe 模块提供）
  safe:
    api-signature:
      enabled: true                         # 签名验证开关（默认 false）
      app-secret: ${APP_SECRET}             # 签名密钥
      timestamp-tolerance-seconds: 300      # 时间戳容差（秒）
      nonce-expire-seconds: 600             # Nonce 过期时间（秒）
      excludes:                             # 签名验证排除路径
        - /api/app/public/**
        - /api/app/login
```

## 自动配置

| 配置类 | 激活条件 |
|---|---|
| `AppMvcConfiguration` | 总是激活 |
| `AppTimezoneConfiguration` | 总是激活 |
| `AppI18nConfiguration` | 总是激活 |
| `AppOpenApiConfiguration` | 总是激活 |
| `AppHealthIndicator` | `ydsz.app.enabled=true`（默认 true） + classpath 有 HealthIndicator |
| `AppMetrics` | 总是激活（MeterRegistry 可选） |

## 依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-app</artifactId>
</dependency>
```
