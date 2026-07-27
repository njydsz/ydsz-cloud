# ydsz-common-safe

YDSZ 统一安全框架 — XSS 防护、SQL 注入防护、CSRF Token（Synchronizer + Double Submit）、敏感数据脱敏（18 种类型）、验证码（图形/算术/滑块）、限流（@RateLimit AOP + Filter + 多维度）、AES-256-GCM 加密、API 签名验证、IP 黑白名单、密码强度校验、安全事件自动响应、Micrometer 指标、安全审计日志。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L5 业务服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **源文件数** | 90+ |

## 核心能力

### XSS 防护

| 类 | 说明 |
|---|---|
| `XssFilter` | XSS 过滤器（请求参数 / Body 清洗） |
| `OwaspXssCleaner` | OWASP HTML Sanitizer 清洗引擎 |
| `XssPolicyFactory` | 可配置清洗策略（STRICT / STANDARD / RELAXED） |
| `HTMLFilter` | HTML 标签过滤器 |
| `EscapeUtils` | HTML / JS / CSS / URL / XML 转义工具 |
| `XssValidator` / `@Xss` | 参数校验器与注解 |

### SQL 注入防护

| 类 | 说明 |
|---|---|
| `SqlInjectionFilter` | SQL 注入过滤器（支持运行时热更新规则） |
| `SqlInjectionProperties` | SQL 注入检测配置（外部化规则 + 白名单） |

### CSRF 防护

| 类 | 说明 |
|---|---|
| `CsrfFilter` | CSRF 过滤器（支持 Synchronizer + Double Submit 两种模式） |
| `CsrfProperties.CsrfMode` | 防护模式枚举（SYNCHRONIZER / DOUBLE_SUBMIT） |
| `CsrfToken` / `CsrfTokenGenerator` | Token 生成 |
| `RedisCsrfTokenRepository` / `InMemoryCsrfTokenRepository` | Redis / 内存实现 |

### 敏感数据脱敏

| 注解 / 类 | 说明 |
|---|---|
| `@SensitiveData` | 敏感数据注解（18 种类型 + 自定义格式 + 角色白名单） |
| `@Sensitive` | 简化版敏感字段注解 |
| `SensitiveDataAdvice` | 返回值脱敏切面（支持基于角色的动态脱敏） |
| `SensitiveDataProcessor` | 脱敏处理器（带缓存快速跳过无注解类） |
| `SensitiveUtil` | 脱敏工具 |

### 限流

| 注解 / 类 | 说明 |
|---|---|
| `@RateLimit` | 方法级限流注解（多维度 + 多算法 + 突发容量） |
| `RateLimitAspect` | 方法级限流 AOP 切面（多维度 + 令牌桶/滑动窗口） |
| `RateLimitFilter` | 全局限流过滤器（Redis 降级到本地限流） |
| `LocalRateLimiter` | 本地限流降级方案（Semaphore + 时间窗口） |
| `MultiDimensionRateLimiter` | 多维度限流器（IP / USER / API / GLOBAL 组合） |

### 验证码

| 类 | 说明 |
|---|---|
| `ImageCaptchaGenerator` / `ArithmeticCaptchaGenerator` | 图形 / 算术验证码 |
| `SliderCaptchaGenerator` | 滑块验证码生成器 |
| `CaptchaStore` / `RedisCaptchaStore` / `LocalCaptchaStore` | 验证码存储 |
| `CaptchaValidator` | 验证码校验器（图形 / 算术 / 滑块） |

### 加密与签名

| 类 | 说明 |
|---|---|
| `AesGcmCrypto` | AES-256-GCM 加解密（含 AAD 认证） |
| `NonceCache` | Nonce 缓存（防重放攻击） |
| `ApiSignatureFilter` | API 签名验证过滤器（timestamp + nonce + HMAC-SHA256） |
| `ApiSignatureProperties` | API 签名配置 |

### IP 访问控制

| 类 | 说明 |
|---|---|
| `IpAccessFilter` | IP 黑白名单过滤器（最高优先级） |
| `IpAccessService` | IP 访问控制服务（CIDR 网段 + Redis + 本地缓存） |
| `IpAccessProperties` | IP 访问控制配置 |

### 安全事件自动响应

| 类 | 说明 |
|---|---|
| `SecurityEventAggregator` | 安全事件聚合器（滑动窗口 + 自动 IP 封禁） |
| `SecurityEventPublisher` | 安全事件发布器 |
| `AutoBlockProperties` | 自动封禁配置（阈值 + 窗口） |

### 密码强度校验

| 类 | 说明 |
|---|---|
| `PasswordStrengthValidator` | 密码强度校验器（长度 + 字符种类 + 弱密码字典 + 序列检测） |

### 可观测性

| 类 | 说明 |
|---|---|
| `SafeMetrics` | Micrometer 指标采集（XSS / SQL / CSRF / 限流 / IP 封禁 Counter + Filter Timer） |
| `SecurityAuditLogger` | 安全审计日志（结构化 JSON + traceId 关联） |

### 工具类

| 类 | 说明 |
|---|---|
| `ClientIpResolver` | 统一客户端 IP 解析（多级代理 + 内网判断） |
| `CachedBodyHttpServletRequestWrapper` | 请求体缓存包装器（多次读取） |

### 安全头

| 类 | 说明 |
|---|---|
| `SecurityHeaderFilter` | 安全响应头过滤器（7 种安全头） |
| `SecurityHeaderProperties` | 安全头配置 |

## 配置项

```yaml
ydsz:
  safe:
    xss:
      enabled: true
      mode: filter                  # filter / converter
      ignore-urls: [/api/public/**]
    sql-injection:
      enabled: true
      block-on-detect: true
      custom-pattern: ""            # 自定义检测规则（热更新）
    csrf:
      enabled: true
      mode: SYNCHRONIZER            # SYNCHRONIZER / DOUBLE_SUBMIT
      token-header: X-CSRF-TOKEN
    ratelimit:
      enabled: true
      limit-per-second: 100
      burst-capacity: 200
    ip-access:
      enabled: false
      mode: BLACKLIST               # BLACKLIST / WHITELIST
      default-block-seconds: 3600
    api-signature:
      enabled: false
      app-id: "ydsz-web"
      app-secret: "Base64Secret"
      timestamp-tolerance-seconds: 300
    auto-block:
      enabled: true
      threshold: 10
      window-seconds: 60
    captcha:
      type: image                   # image / arithmetic / slider
      store-type: redis
      expire: 300
    security-header:
      content-security-policy: "default-src 'self'"
```

## 自动配置

| 配置类 | 激活条件 |
|---|---|
| `SafeConfiguration` | 总是激活（含 @EnableScheduling） |
| `CaptchaAutoConfiguration` | 总是激活 |

## 过滤器执行顺序

```
IpAccessFilter (HIGHEST_PRECEDENCE)
  → SecurityHeaderFilter
  → XssFilter
  → SqlInjectionFilter
  → CsrfFilter
  → RateLimitFilter (HIGHEST_PRECEDENCE + 1)
  → ApiSignatureFilter (HIGHEST_PRECEDENCE + 4)
```

## 依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-safe</artifactId>
</dependency>
```

### 技术栈

- JSON 引擎：YdszJson（ydsz-common-json）
- 本地缓存：ydsz-common-cache（替代 Caffeine）
- 指标采集：Micrometer（optional）
- HTML 清洗：OWASP Java HTML Sanitizer
- AOP：Spring AOP（spring-boot-starter-aspectj）
