# ydsz-common-app

PMIS 移动端 App 基座 — 继承 `common-base`，叠加 API 签名验证（防重放攻击）、App 认证、安全头增强、请求上下文管理。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L6 应用层 |
| **类型** | 公共依赖库（不独立部署） |
| **源文件数** | 20 |
| **面向** | 移动端 App 后端服务（未来移动端项目使用） |

## 核心能力

### API 签名验证（防重放）

| 类 | 说明 |
|---|---|
| `AppSignatureFilter` | API 签名过滤器（HMAC-SHA256 签名校验 + 时间戳防重放 + Nonce 防重放） |
| `AppSignatureProperties` | 签名配置属性 |

签名流程：
1. 客户端将请求参数 + 时间戳 + Nonce 排序拼接
2. 使用 AppSecret 进行 HMAC-SHA256 签名
3. 签名值放入 `X-Signature` 请求头
4. 服务端验证签名 + 时间戳有效性（±5 分钟）+ Nonce 唯一性

### App 认证

| 类 | 说明 |
|---|---|
| `AppAuthFilter` | App 认证过滤器（Token 解析 → 上下文设置） |
| `AppAuthHandler` / `AuthHandlerFactory` | App 认证处理器 / 工厂 |
| `AppAuthInfo` | App 认证信息（appId / userId / deviceInfo） |

### 过滤器链

| 类 | 说明 |
|---|---|
| `AppSignatureFilter` | API 签名验证（最高优先级） |
| `AppSecurityHeaderFilter` | 安全头过滤器 |
| `AppRequestIdResponseFilter` | 请求 ID 响应过滤器 |
| `AppContentCachingFilter` | 内容缓存过滤器 |
| `AppRequestContextCleanupFilter` | 上下文清理过滤器 |
| `AppAuthFilter` | App 认证过滤器 |

### MVC 配置

| 类 | 说明 |
|---|---|
| `AppMvcConfiguration` | MVC 配置（继承 base，追加 App 特有配置） |
| `AppTimezoneConfiguration` | 时区配置 |
| `AppI18nConfiguration` | 国际化配置 |
| `AppOpenApiConfiguration` | OpenAPI 配置 |
| `AppCorsProperties` | CORS 配置 |
| `AppTraceProperties` | Trace 配置 |

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

1. **API 签名验证** — HMAC-SHA256 + 时间戳 + Nonce 三重防重放
2. **App 认证** — `AppAuthFilter` + `AppAuthHandler`
3. **安全头增强** — 移动端特有安全头（如 `X-App-Version`）

> **注意**：`common-web` 与 `common-app` 是两个**平行**的应用层入口。后端微服务统一使用 `common-web`，`common-app` 仅用于未来移动端项目。

## 配置项

```yaml
pmis:
  app:
    signature:
      enabled: true                 # 签名验证开关
      algorithm: HMAC-SHA256        # 签名算法
      timestamp-tolerance: 300      # 时间戳容差（秒）
      nonce-cache-ttl: 600          # Nonce 缓存 TTL（秒）
      app-secrets:                  # App 密钥映射
        android-prod: ${ANDROID_APP_SECRET}
        ios-prod: ${IOS_APP_SECRET}
    auth:
      enabled: true
      token-header: X-Auth-Token
      ignore-urls:
        - /api/app/public/**
        - /api/app/login
    cors:
      allowed-origins: ["capacitor://localhost", "http://localhost"]
```

## 自动配置

| 配置类 | 激活条件 |
|---|---|
| `AppMvcConfiguration` | 总是激活 |

## 依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-app</artifactId>
</dependency>
```
