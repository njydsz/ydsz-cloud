# ydsz-pmis-common-safe

PMIS 应用安全框架 — XSS 防护、SQL 注入防护、CSRF Token、敏感数据脱敏（7 种类型）、验证码、限流（@RateLimit）、AES-256-GCM 加密、安全事件告警。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L5 业务服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **源文件数** | 69 |

## 核心能力

### XSS 防护

| 类 | 说明 |
|---|---|
| `XssFilter` | XSS 过滤器（请求参数 / Body 清洗） |
| `XssHttpServletRequestWrapper` | XSS 请求包装器 |
| `XssJacksonConfig` / `XssStringDeserializer` | Jackson XSS 反序列化配置 |
| `OwaspXssCleaner` | OWASP AntiSamy XSS 清洗引擎 |
| `HTMLFilter` | HTML 标签过滤器 |
| `EscapeUtils` | HTML / JS / XML 转义工具 |
| `XssValidator` | 参数校验器 |
| `@Xss` | XSS 校验注解 |

### SQL 注入防护

| 类 | 说明 |
|---|---|
| `SqlInjectionFilter` | SQL 注入过滤器（拦截恶意 SQL 关键字） |

### CSRF 防护

| 类 | 说明 |
|---|---|
| `CsrfFilter` | CSRF 过滤器 |
| `CsrfToken` / `CsrfTokenGenerator` / `DefaultCsrfTokenGenerator` | Token 生成 |
| `CsrfTokenRepository` | Token 存储接口 |
| `RedisCsrfTokenRepository` / `InMemoryCsrfTokenRepository` | Redis / 内存实现 |

### 敏感数据脱敏

| 注解 / 类 | 说明 |
|---|---|
| `@Sensitive` | 敏感数据注解（指定脱敏类型） |
| `SensitiveType` | 7 种脱敏类型（PHONE / ID_CARD / EMAIL / BANK_CARD / ADDRESS / NAME / CUSTOM） |
| `SensitiveDataSerializer` | Jackson 序列化脱敏 |
| `SensitiveDataAdvice` | 返回值脱敏切面 |
| `SensitiveDataProcessor` | 脱敏处理器 |
| `SensitiveUtil` | 脱敏工具 |
| `YdszSensitiveSerializer` | 自定义序列化器 |

### 列脱敏

| 类 | 说明 |
|---|---|
| `ColumnDesensitizationRule` | 列脱敏规则 |
| `ColumnDesensitizationContext` | 列脱敏上下文 |
| `ColumnDesensitizationExecutor` | 列脱敏执行器 |

### 限流

| 注解 / 类 | 说明 |
|---|---|
| `@RateLimit` | 限流注解（指定 QPS / 窗口 / Key） |
| `RateLimitFilter` | 限流过滤器 |
| `RateLimitProperties` | 限流配置 |
| `MultiDimensionRateLimiter` | 多维度限流器（IP / USER / API / GLOBAL 组合） |

### 验证码

| 类 | 说明 |
|---|---|
| `CaptchaGenerator` / `ImageCaptchaGenerator` / `ArithmeticCaptchaGenerator` | 验证码生成（图形 / 算术） |
| `CaptchaStore` / `RedisCaptchaStore` / `LocalCaptchaStore` | 验证码存储 |
| `CaptchaValidator` | 验证码校验器 |
| `CaptchaRateLimiter` | 验证码限流（防暴力请求） |
| `CaptchaType` / `CaptchaStoreType` | 验证码类型枚举 |
| `CaptchaProperties` / `CaptchaAutoConfiguration` | 配置与自动装配 |

### 加密

| 类 | 说明 |
|---|---|
| `AesGcmCrypto` | AES-256-GCM 加解密 |
| `NonceCache` | Nonce 缓存（防重放攻击） |

### 安全事件告警

| 类 | 说明 |
|---|---|
| `SecurityEventPublisher` | 安全事件发布器 |
| `SecurityAlertListener` | 安全告警监听器 |
| `SecurityEvent` / `SecurityEventType` | 安全事件模型 |
| `DefaultSecurityAlertLogger` | 默认告警日志器 |
| `SafeAlertProperties` | 告警配置 |

### 安全头

| 类 | 说明 |
|---|---|
| `SecurityHeaderFilter` / `BaseSecurityHeaderFilter` | 安全响应头过滤器（X-Frame-Options / X-Content-Type-Options / CSP） |
| `SecurityHeaderProperties` | 安全头配置 |

## 配置项

```yaml
pmis:
  safe:
    xss:
      enabled: true
      mode: filter                  # filter / converter
      ignore-urls: [/api/public/**]
    csrf:
      enabled: true
      store-type: redis             # redis / memory
    rate-limit:
      enabled: true
      default-qps: 100
    captcha:
      type: image                   # image / arithmetic
      store-type: redis
      expire: 300
    security-header:
      content-security-policy: "default-src 'self'"
```

## 自动配置

| 配置类 | 激活条件 |
|---|---|
| `SafeConfiguration` | 总是激活 |
| `XssAutoConfiguration` | 总是激活 |
| `SensitiveDataConfiguration` | 总是激活 |
| `CaptchaAutoConfiguration` | 总是激活 |

## 依赖

```xml
<dependency>
    <groupId>com.njydsz.pmis</groupId>
    <artifactId>ydsz-pmis-common-safe</artifactId>
</dependency>
```
