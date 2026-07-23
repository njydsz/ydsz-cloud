# ydsz-common-app

YDSZ 移动端 App 基座 — 继承 `common-base`，叠加 API 签名验证（防篡改 + 防重放）、App 认证、安全头增强、请求上下文管理、健康检查、指标采集。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L6 应用层 |
| **类型** | 公共依赖库（不独立部署） |
| **源文件数** | 20 |
| **面向** | 移动端 App 后端服务（未来移动端项目使用） |

## 核心能力

### API 签名验证（防篡改 + 防重放）

| 类 | 说明 |
|---|---|
| `AppSignatureFilter` | API 签名过滤器（HMAC-SHA256 签名校验 + 请求体哈希防篡改 + 时间戳防重放 + Nonce 防重放） |
| `AppSignatureProperties` | 签名配置属性（多 App 密钥、路径白名单、Nonce TTL） |

签名流程：
1. 客户端将请求体计算 SHA-256 哈希（GET 请求为空字符串）
2. 拼接签名串：`method|uri|timestamp|nonce|bodySha256`
3. 使用 AppSecret 进行 HMAC-SHA256 签名
4. 签名值放入 `X-App-Sign` 请求头
5. 服务端验证签名 + 时间戳有效性（±5 分钟）+ Nonce 唯一性（Redis SETNX）

**防重放机制：**
- 时间戳容差：请求时间戳与服务端时间差超过 `timestamp-tolerance` 则拒绝
- Nonce 唯一性：通过 Redis `SETNX` 原子操作确保同一 Nonce 在 TTL 内不被重复使用

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
| `AppSignatureFilter` | API 签名验证（防篡改 + 防重放） |
| `AppSecurityHeaderFilter` | 安全头过滤器 |
| `AppRequestIdResponseFilter` | 请求 ID 响应过滤器 |
| `AppAuthFilter` | App 认证过滤器 |

### 健康检查与指标

| 类 | 说明 |
|---|---|
| `AppHealthIndicator` | 健康检查（签名配置状态 / Redis 连通性 / 指标状态） |
| `AppMetrics` | Micrometer 指标采集（签名验证计数/耗时、认证计数、请求耗时） |

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

1. **API 签名验证** — HMAC-SHA256 + 请求体哈希 + 时间戳 + Nonce 四重防篡改防重放
2. **App 认证** — `AppAuthFilter` + `AppAuthHandler`
3. **安全头增强** — 移动端特有安全头
4. **健康检查** — `AppHealthIndicator` 报告签名/Redis/指标状态
5. **指标采集** — `AppMetrics` Micrometer 指标暴露到 Prometheus

> **注意**：`common-web` 与 `common-app` 是两个**平行**的应用层入口。后端微服务统一使用 `common-web`，`common-app` 仅用于未来移动端项目。

## 配置项

```yaml
ydsz:
  app:
    enabled: true                          # 模块开关（默认 true）
    signature:
      enabled: true                         # 签名验证开关（默认 false）
      algorithm: HMAC-SHA256               # 签名算法
      timestamp-tolerance: 300000          # 时间戳容差（毫秒，默认 5 分钟）
      nonce-cache-ttl: 300                 # Nonce 缓存 TTL（秒，默认 5 分钟）
      app-id-header: X-App-Id              # App ID 请求头名称
      app-secret: ${DEFAULT_APP_SECRET}    # 默认密钥（单 App 场景）
      app-secrets:                          # 多 App 密钥映射（多 App 场景）
        android-prod: ${ANDROID_APP_SECRET}
        ios-prod: ${IOS_APP_SECRET}
      ignore-urls:                          # 签名验证白名单
        - /api/app/public/**
        - /api/app/login
      order: -2147483623                    # 过滤器顺序（默认在内容缓存之后）
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
```

### 签名请求头

| 请求头 | 说明 | 必填 |
|---|---|---|
| `X-App-Sign` | HMAC-SHA256 签名值（Hex 格式） | 是 |
| `X-App-Timestamp` | 请求时间戳（毫秒） | 是 |
| `X-App-Nonce` | 随机字符串（UUID 推荐） | 是 |
| `X-App-Id` | App 标识（多 App 密钥场景） | 否 |

### 客户端签名示例

```java
// 1. 获取时间戳和 nonce
long timestamp = System.currentTimeMillis();
String nonce = UUID.randomUUID().toString();

// 2. 计算请求体 SHA-256（GET 请求为空字符串）
String bodySha256 = sha256Hex(requestBody);

// 3. 拼接签名串
String signData = method + "|" + uri + "|" + timestamp + "|" + nonce + "|" + bodySha256;

// 4. HMAC-SHA256 签名（Hex 输出）
String signature = hmacSha256Hex(signData, appSecret);

// 5. 设置请求头
request.setHeader("X-App-Sign", signature);
request.setHeader("X-App-Timestamp", String.valueOf(timestamp));
request.setHeader("X-App-Nonce", nonce);
request.setHeader("X-App-Id", appId); // 可选
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
