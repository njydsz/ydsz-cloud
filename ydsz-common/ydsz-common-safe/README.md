# ydzs-common-safe

> 安全防护层（L5 业务服务层）— XSS / CSRF / API 签名 / 限流 / 熔断 / SSRF / 脱敏 / 验证码

提供 XSS 三模式（Filter / HttpMessageConverter / Advice）、CSRF Token 防护、API Request 签名校验（Nonce / 防重放）、限流（令牌桶 + 并发限制）、熔断（Resilience4j 适配器）、出站 SSRF 防护、`@SensitiveData` 脱敏、字段加密 MyBatis TypeHandler、图形验证码、密码强度校验、安全事件告警、IP 黑白名单等企业级安全能力。默认 fail-closed 语义（宁可拒绝 / 不泄露 / 不越权）。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L5 业务服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 提供多维安全防护：XSS / CSRF / API 签名 / 限流 / 熔断 / SSRF / 脱敏 |
| **依赖** | ydsz-common-core、ydsz-common-util、ydsz-common-exception、ydsz-common-cache、ydsz-common-json、ydsz-common-redis、ydsz-common-thread；spring-boot、spring-boot-starter-aspectj、owasp-java-html-sanitizer；可选 mybatis、resilience4j-circuitbreaker / resilience4j-consumer、spring-boot-actuator、spring-boot-health、http-converter、jackson-annotations、restclient |
| **版本** | 2.2.0 |

## 核心能力

### 1. XSS 防护（三模式）

| 模式 | 类 | 说明 |
|---|---|---|
| **Filter 模式** | `XssFilter` / `XssWrapper` | HTTP Request 层清理（包装 HttpServletRequest，getParameter 时过滤） |
| **Converter 模式** | `XssJsonMessageConverter` JSON 反序列化层清理（清理 String 字段） |
| **Advice 模式** | `XssRequestBodyAdvice` | Spring MVC Advice 层清理（`@RequestBody` 参数反序列化后清理） |
| **策略工厂** | `XssAutoConfiguration` / `XssPolicyFactory` | 自动配置 + 策略工厂（`DEFAULT_POLICY` / `STRICT` 可调） |

**XSS 防护目标**：清理 HTML 危险标签（`&lt;script&gt;` / `&lt;svg onload=...&gt;`）和危险属性（`onerror` / `onload` / `javascript:` 伪协议）。

### 2. CSRF 防护

| 类 | 说明 |
|---|---|
| `CsrfFilter` | CSRF 过滤器（校验 Token） |
| `CsrfTokenRepository` **SPI** | Token 存储策略接口 |
| `DefaultCsrfTokenRepository`（impl） | 默认内存存储 |
| `RedisCsrfTokenRepository`（impl） | Redis 存储（分布式） |
| `InMemoryCsrfTokenRepository`（impl） | 内存存储（单节点） |
| `CsrfTokenGenerator` **SPI** | Token 生成器（默认 SecureRandom） |

**默认关闭**：CSRF 防护默认关闭（前后端分离 + Token/JWT 架构下通常无需 CSRF），通过 `ydsz.safe.csrf.enabled=true` 启用。

### 3. API Request 签名

| 类 | 说明 |
|---|---|
| `ApiSignature` | API 签名核心（HMAC-SHA256） |
| `ApiSignatureFilter` | 签名校验 Filter（先验签，后消费 nonce） |
| `ApiSignatureConfig` | 签名配置（签名 Header / nonce Header / 签名内容拼接规则） |
| `NonceCache` | Nonce 缓存（Redis SETNX，防重放） |

**签名内容拼接规则**：`{METHOD}\n{URI}\n{canonicalizedQuery}\n{requestBody}\n{timestamp}` → HMAC-SHA256。

### 4. 限流（RateLimiter 体系）

| 类 | 说明 |
|---|---|
| `RateLimitAutoConfiguration` | 限流自动配置（限流 Bean 装配） |
| `RateLimiter` **SPI** | 限流器接口 |
| `ClusterRateLimiter` | 集群限流器（Redis 后端） |
| `RateLimitRuleProvider` **SPI** | 限流规则提供者（动态加载规则） |
| `RateLimitRuleListener` | 限流规则热更新监听器 |
| `RateLimiterProperties` | 限流配置属性（`ydsz.safe.rate-limit.*`） |

### 5. 熔断器

| 类 | 说明 |
|---|---|
| `SafeCircuitBreakerAdapter`（circuitbreaker） | Resilience4j CircuitBreaker 适配器（Ydsz 接口 → Resilience4j 实现） |
| `CircuitBreakerConfig`（circuitbreaker） | 熔断配置 |
| `CircuitBreakerStatePersistence`（circuitbreaker） | 熔断状态持久化（Redis / 本地） |
| `CircuitBreakerMetricsExporter`（circuitbreaker） | 熔断指标导出 |
| `CircuitBreakerStrategy`（circuitbreaker） | 熔断策略接口 |

### 6. 出站 SSRF 防护

| 类 | 说明 |
|---|---|
| `HttpConnectionValidator`（ssrf） | HTTP 出站连接校验器（禁止访问内网 IP / 特定端口） |
| `SsrfHttpRequestInterceptor`（ssrf） | RestTemplate / HttpClient 拦截器（出站前置校验） |

**拦截规则**：禁止访问 RFC1918 私网地址（10.x / 172.16-31.x / 192.168.x）和本地回环（127.x）。

### 7. 字段加密

| 类 | 说明 |
|---|---|
| `FieldEncryptionAutoConfiguration` | 字段加密 MyBatis TypeHandler 自动配置 |
| `FieldEncryptionConfig` | 字段加密配置 |
| `FieldEncryptionTypeHandler` | MyBatis TypeHandler 加解密（读解密，写加密） |
| `FieldEncryptionException` | 字段加密异常 |

### 8. 安全事件与告警

| 类 | 说明 |
|---|---|
| `SecurityAuditLogger`（audit） | 安全审计日志 |
| `SecurityEventAggregator`（alert） | 安全事件聚合 |
| `SecurityEventPublisher**（alert）** | 安全事件发布 |
| `SecurityEventListener` **SPI** | 安全事件监听器（自动收集） |
| `SecurityAlertProperties`（alert） | 安全告警配置 |
| `SecurityEvent*`（alert） | 安全事件聚合 / 发布 / 监听 + SafeAlertProperties |

### 9. IP 黑白名单

| 类 | 说明 |
|---|---|
| `IpAccessFilter`（filter） | IP 访问控制 Filter |
| `IpAccessService`（ip） | IP 访问控制服务（加载黑白名单配置） |
| `ClientIpResolver`（util） | 客户端真实 IP 解析（考虑 X-Forwarded-For / X-Real-IP） |

### 10. 图形验证码

| 类 | 说明 |
|---|---|
| `CaptchaGenerator` **SPI** | 验证码生成器（支持数字 / 字母 / 混合） |
| `CaptchaProperties` | 验证码配置（`ydsz.safe.captcha.*`） |

### 11. 密码强度校验

| 类 | 说明 |
|---|---|
| `PasswordStrengthValidator`（password） | 密码强度校验器（长度 / 大小写 / 数字 / 特殊字符） |

### 12. 脱敏

| 类 | 说明 |
|---|---|
| `SensitiveDataAdvice`（sensitive） | 脱敏 AOP 切面（`@ResponseBody` 返回值处理） |
| `SensitiveDataProcessor**（sensitive）** | 脱敏处理器（按类型统一处理） |
| `SensitiveType`（sensitive） | 脱敏类型枚举（PHONE / ID_CARD / BANK_CARD / EMAIL / ADDRESS / NAME） |
| `SensitiveDataSerializer`（sensitive） | Jackson 序列化器（序列化时自动脱敏） |
| `SensitiveDataUtil`（sensitive） | 脱敏工具 |

### 13. 其他安全注解

| 注解 | 说明 |
|---|---|
| `@EnableYdszSafe` | 启用 ydsz-safe 注解扫描 |
| `@SecondaryAuth` | 二次认证（敏感操作前验证） |
| `@SensitiveLevel` | 敏感级别（自定义脱敏策略） |
| `@SensitiveOperation` | 敏感操作标记 |
| `@Xss` | XSS 清理标记（字段级细粒度控制） |

## 接入方式

### 1. POM 引入依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-safe</artifactId>
</dependency>
```

### 2. 启用注解

```java
@SpringBootApplication
@EnableYdszSafe
public class SystemApplication { }
```

### 3. 配置属性

```yaml
yzsz:
  safe:
    xss:
      enabled: true
      policy: DEFAULT_POLICY              # DEFAULT_POLICY（OWASP + 自定义）/ STRICT（仅白名单标签）
      exclude-paths: /public/**
    csrf:
      enabled: false                       # 默认关闭
      token-header: X-CSRF-TOKEN
      token-parameter: _csrf
      ignore-paths: /public/**
    signature:
      enabled: true
      secret: ${API_SIGNATURE_SECRET}      # HMAC-SHA256 密钥
      nonce-header: X-Nonce
      timestamp-header: X-Timestamp
      timestamp-tolerance-seconds: 300     # 时间戳容忍范围
    rate-limit:
      enabled: true
      default-qps: 100
      cluster: false                       # true = 集群限流（Redis）
    circuit-breaker:
      enabled: true
      failure-rate-threshold: 50
      slow-call-rate-threshold: 80
      slow-call-duration-threshold: 3s
      wait-duration-in-open-state: 30s
    ssrf:
      enabled: true
      block-private-ip: true
      block-loopback: true
      allowed-ports: 80,443,8080,8443
    field-encryption:
      enabled: true
      aes-key: ${FIELD_AES_KEY}
      aes-iv: ${FIELD_AES_IV}
    captcha:
      enabled: true
      length: 4
      type: NUMBER                        # NUMBER / LETTER / MIX
      ttl-seconds: 300
    ip-access:
      enabled: false
      whitelist:
      blacklist:
    password:
      min-length: 8
      require-uppercase: true
      require-lowercase: true
      require-digit: true
      require-special-char: true
    desensitize:
      enabled: true
```

### 4. API Request 签名

```java
// 服务端：Filter 自动校验 X-Nonce + X-Timestamp + X-Signature
// 客户端：拼接规则
// String toSign = method + "\n" + uri + "\n" + canonicalizedQuery + "\n" + body + "\n" + timestamp;
// String signature = HmacUtils.hmacSha256Hex(secret, toSign);
```

## 配置项

### 安全头配置（`ydsz.safe.security-header.*`）

| 内容 | 默认值 | 说明 |
|---|---|---|
| `X-Content-Type-Options` | nosniff | 防 MIME 嗅探 |
| `X-Frame-Options` | DENY | 防 Clickjacking |
| `Strict-Transport-Security` | max-age=31536000; includeSubDomains | HSTS |
| `Referrer-Policy` | strict-origin-when-cross-origin | Referrer 策略 |
| `Content-Security-Policy` | - | CSP（业务自定义） |
| `Permissions-Policy` | - | Permissions Policy |

## SPI 扩展点

| SPI 接口 | 用途 | 注册方式 |
|---|---|---|
| `CaptchaGenerator` **SPI** | 验证码生成器 | `@ConditionalOnMissingBean` |
| `CsrfTokenRepository` **SPI** | CSRF Token 存储 | `@ConditionalOnMissingBean` |
| `CsrfTokenGenerator` **SPI** | CSRF Token 生成器 | `@ConditionalOnMissingBean` |
| `SecurityEventListener` **SPI** | 安全事件告警回调 | `List<SecurityEventListener>` 自动收集 |
| `RateLimitRuleProvider` **SPI** | 限流规则提供者（动态加载） | `@ConditionalOnMissingBean` |
| `RateLimitRuleListener` | 限流规则热更新回调 | `@Component` |
| `RateLimiter` **SPI** | 限流算法（令牌桶 / 并发限制） | `@Component` |
| `ClusterRateLimiter` | 集群限流器 | `@Component` |

## 健康检查

| 端点 | 说明 | 触发条件 |
|---|---|---|
| `/actuator/health/safe` | 安全防护健康检查 | `ydsz-safe` 存在 |

`SafeHealthIndicator` 暴露信息：`csrf` / `xss` / `signature` / `ssrf` 各子模块状态。

## 自动配置类

| 类 | 触发条件 |
|---|---|
| `SafeConfiguration` | `ydsz-safe` 在 classpath |
| `XssAutoConfiguration` | XSS 启用 |
| `RateLimitAutoConfiguration` | 限流启用 |
| `FieldEncryptionAutoConfiguration` | 字段加密启用 |
| `IdempotentAutoConfiguration` | 幂等启用 |

## 注意事项

1. **CSRF 默认关闭**：前后端分离 + JWT Token 场景无需 CSRF；前后端一体化才需开启 CSRF。
2. **API 签名时间戳容忍**：生产环境建议 `timestamp-tolerance-seconds <= 300`（5 分钟），太短导致客户端时间不同步失败。
3. **XSS 三模式不要同时开启`HttpMessageConverter` 和 `Advice` 同时开启会重复清理，建议根据架构选择其一（前后端 JSON 交互推荐 Converter）。
4. **限流降级**：限流器内部设计了 `null == rule` 兜底逻辑（不会因规则未加载拒绝所有请求）。
5. **fail-closed**：出站 SSRF 检测在无法解析目标 IP 时默认拒绝（fail-closed）。

## 变更记录

- **2.2.0**（2026-09-04）：新增 Outbound SSRF 防护（HttpConnectionValidator）；安全事件告警新增`SecurityEventAggregator`聚合降噪；IP 黑白名单新增 `ClientIpResolver`。
- **2.0.0**（2026-09-01）：XSS 三模式重构（Filter / HttpMessageConverter / Advice）；字段加密 TypeHandler 通用化（支持任意 Entity 加解密字段）。
- **1.0.0**（2026-08-02）：初始版本（XSS Filter + CSRF + API 签名 + 限流）。
