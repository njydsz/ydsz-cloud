# ydsz-common-safe

> YDSZ 统一安全防护基座（L5 业务服务层）

提供 XSS 防护、CSRF 双模式防护、敏感数据脱敏、限流（令牌桶 + 熔断器）、AES-256-GCM 加密、API 签名验证、IP 黑白名单、安全事件自动响应、Micrometer 指标、安全审计日志、SSRF 防护、二级认证、幂等性等全栈 Web 安全能力，是 YDSZ 项目所有业务服务的统一安全基座。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L5 业务服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 为所有 Web 服务提供端到端的安全防护能力（过滤器链 + AOP + 注解） |
| **源文件数** | 117 |
| **依赖** | common-core、common-json、common-redis、common-util、common-exception、common-cache；可选依赖 micrometer-core、mybatis-plus-core、spring-boot-actuator、spring-boot-health |
| **版本** | 1.2.1 |

## 核心能力

### 1. XSS 防护

| 类 / 注解 | 说明 |
|---|---|
| `XssFilter` | XSS 过滤器（Filter 模式，请求参数 + Body 清洗） |
| `XssJsonMessageConverter` | JSON 反序列化阶段清洗（Converter 模式，默认模式） |
| `XssRequestBodyAdvice` | JSON Body 请求体拦截清洗 |
| `XssStringDeserializer` | Jackson String 反序列化器 |
| `OwaspXssCleaner` | OWASP HTML Sanitizer 清洗引擎 |
| `XssPolicyFactory` | 可配置清洗策略（STRICT / STANDARD / RELAXED） |
| `HTMLFilter` | HTML 标签过滤器 |
| `EscapeUtils` | HTML / JS / CSS / URL / XML 转义工具 |
| `XssValidator` / `@Xss` | 参数校验器与注解 |
| `SafeJsonModule` | YdszJson 模块注册 |
| `JsonBodyXssCleaner` | JSON Body XSS 清洗器（内部核心） |
| `XssAutoConfiguration` | XSS 自动配置 |
| `SafeXssProperties` | XSS 配置属性（`ydsz.safe.xss.*`） |
| `XssFilterModeCondition` / `XssConverterModeCondition` | XSS 模式条件判断（filter / converter 互斥） |

### 2. CSRF 防护

| 类 | 说明 |
|---|---|
| `CsrfFilter` | CSRF 过滤器（Synchronizer + Double Submit 双模式） |
| `CsrfProperties.CsrfMode` | 防护模式枚举（SYNCHRONIZER / DOUBLE_SUBMIT） |
| `CsrfToken` / `CsrfTokenGenerator` | Token 模型与生成器 |
| `CsrfTokenRepository` | Token 存储抽象 |
| `RedisCsrfTokenRepository` | Redis 分布式存储实现（@Primary） |
| `InMemoryCsrfTokenRepository` | 内存存储实现（单机降级） |
| `DefaultCsrfTokenGenerator` | 默认 Token 生成器 |

### 3. 敏感数据脱敏

| 类 / 注解 | 说明 |
|---|---|
| `@SensitiveData` | 敏感数据注解（18 种类型 + 自定义格式 + 角色白名单） |
| `SensitiveType` | 脱敏类型枚举（PHONE / ID_CARD / BANK_CARD / EMAIL / NAME / ADDRESS 等 18 种） |
| `SensitiveDataAdvice` | 返回值脱敏切面（支持基于角色的动态脱敏） |
| `SensitiveDataProcessor` | 脱敏处理器（带缓存快速跳过无注解类） |
| `SensitiveDataSerializer` | Jackson 序列化器 |
| `SensitiveUtil` / `SensitiveUtils` | 脱敏工具类 |
| `ColumnDesensitizationExecutor` | 列级脱敏执行器 |
| `ColumnDesensitizationRule` | 列脱敏规则 |
| `ColumnDesensitizationContext` | 列脱敏上下文 |
| `@Sensitive` | 敏感数据标注注解（字段级） |
| `SensitiveDataProperties` | 敏感数据配置属性 |
| `SensitiveDataProcessingException` | 敏感数据处理异常 |

### 4. 限流（多维度 + 多算法）

| 类 / 注解 | 说明 |
|---|---|
| `@RateLimit` | 方法级限流注解（多维度 + 多算法 + 突发容量） |
| `RateLimitAspect` | 方法级限流 AOP 切面 |
| `RateLimitManager` | 限流管理器（核心调度） |
| `RateLimiter` | 限流器接口 |
| `RateLimiterFactory` | 限流器工厂 |
| `TokenBucketLimiter` | 令牌桶限流器实现 |
| `ClusterRateLimiter` / `RedisClusterRateLimiter` | 集群限流（Redis） |
| `CircuitBreaker` | 限流熔断器 |
| `AbstractCircuitBreaker` | 熔断器抽象基类 |
| `RateLimitRuleCache` | 规则缓存 |
| `RateLimitMetricsCollector` | Micrometer 指标采集 |
| `RateLimitService` | 限流服务封装 |
| `RateLimitResponseDecorator` | 限流响应装饰器（标准化限流拒绝响应体） |
| `RateLimitDecision` | 限流决策结果 |
| `RateLimitContext` | 限流上下文 |
| `RateLimitAutoConfiguration` | 限流自动配置 |
| `RateLimitProperties` | 限流配置属性 |
| `CircuitBreakerProperties` | 熔断器配置属性 |
| `HotParamRule` | 热点参数规则 |

**支持的限流算法**（`RateLimitAlgorithm` 枚举）：`TOKEN_BUCKET`（令牌桶）。

**支持的限流维度**（`RateLimitDimension` 枚举，11 种）：`API`、`USER`、`IP`、`GLOBAL`、`HOT_PARAM`、`TENANT`、`DEVICE`、`HOT_USER`、`HOT_GOODS`、`CLUSTER`、`ADAPTIVE`。

**支持的限流模式**（`RateLimitMode` 枚举，4 种）：`LOCAL`（本地）、`CLUSTER`（集群 Redis）、`ADAPTIVE`（自适应）、`HYBRID`（混合）。

### 5. 验证码

> **说明**：1.0.0 起验证码能力已精简收敛，仅保留核心生成器（`CaptchaGenerator` + `CaptchaProperties`）。图形/算术/滑块生成器、存储、校验器、限流器等均已移除，验证码存储/校验由业务方结合 Redis 自行实现。

### 6. 加密与签名

| 类 | 说明 |
|---|---|
| `FieldEncryptionService` | AES-256-GCM 字段加解密（含 AAD 认证，MyBatis 集成） |
| `NonceCache` | Nonce 缓存（防重放攻击） |
| `ApiSignatureFilter` | API 签名验证过滤器（timestamp + nonce + HMAC-SHA256） |
| `ApiSignatureProperties` | API 签名配置 |
| `ApiSignatureAutoConfiguration` | API 签名自动配置 |

### 7. 字段级加密（MyBatis 集成）

| 类 / 注解 | 说明 |
|---|---|
| `@EncryptField` | 字段加密注解（标记敏感字段，入库加密 / 出库解密） |
| `FieldEncryptionService` | 字段加解密服务（注解扫描 + 密钥管理） |
| `EncryptTypeHandler` | MyBatis TypeHandler（自动加解密） |
| `DecryptFailureStrategy` | 解密失败策略（THROW / RETURN_MASKED / RETURN_ORIGINAL） |
| `FieldEncryptionAutoConfiguration` | 字段加密自动配置 |
| `EncryptFieldProperties` | 字段加密配置属性 |

### 8. IP 访问控制

| 类 | 说明 |
|---|---|
| `IpAccessFilter` | IP 黑白名单过滤器（最高优先级 HIGHEST_PRECEDENCE） |
| `IpAccessService` | IP 访问控制服务（CIDR 网段 + Redis + 本地缓存） |
| `IpAccessProperties` | IP 访问控制配置 |

### 9. 安全事件自动响应

| 类 | 说明 |
|---|---|
| `SecurityEventAggregator` | 安全事件聚合器（滑动窗口 + 自动 IP 封禁） |
| `SecurityEventPublisher` | 安全事件发布器 |
| `SecurityEventListener` | 安全事件监听器（串联指标 + 审计） |
| `SecurityEvent` / `SecurityEventType` | 安全事件模型与类型枚举 |
| `SecurityAlertListener` | 安全告警监听器接口 |
| `DefaultSecurityAlertLogger` | 默认告警日志实现 |
| `AutoBlockProperties` | 自动封禁配置（阈值 + 窗口） |
| `SafeAlertProperties` | 安全告警配置属性 |

### 10. 密码强度校验

| 类 | 说明 |
|---|---|
| `PasswordStrengthValidator` | 密码强度校验器（长度 + 字符种类 + 弱密码字典 + 序列检测） |

### 11. 安全响应头

| 类 | 说明 |
|---|---|
| `SecurityHeaderFilter` | 安全响应头过滤器（7 种安全头） |
| `BaseSecurityHeaderFilter` | 安全响应头基类过滤器 |
| `SecurityHeaderProperties` | 安全头配置 |
| `SecurityHeaderConfigurer` | 安全头配置器 |

### 12. 可观测性

| 类 | 说明 |
|---|---|
| `SafeMetrics` | Micrometer 指标采集（XSS / SQL / CSRF / 限流 / IP 封禁 Counter + Filter Timer） |
| `SafeMetricsDoc` | 指标文档（指标名/类型/描述） |
| `SecurityAuditLogger` | 安全审计日志（结构化 JSON + traceId 关联） |

### 13. SSRF 防护

| 类 | 说明 |
|---|---|
| `SsrfHttpRequestInterceptor` | HTTP 请求拦截器（RestTemplate / WebClient 调用前校验目标地址，阻断内网 IP / 非法协议） |
| `HttpConnectionValidator` | 连接验证器（目标地址私有 IP 段 / 回环地址 / 链路本地地址检测，抛出 `SsrfBlockedException`） |

### 14. 二级认证

| 类 / 注解 | 说明 |
|---|---|
| `@SecondaryAuth` | 场景化二级认证注解（标记操作需通过二级认证后才能执行，支持场景隔离） |
| `@SensitiveOperation` | 敏感操作标注注解 |
| `@SensitiveLevel` | 敏感级别枚举 |

**典型场景：** `password_change`（修改密码前验证）、`role_assign`（分配角色前验证）、`data_export`（数据导出前验证）、`tenant_config`（租户配置变更前验证）。

### 15. 幂等性

| 类 / 注解 | 说明 |
|---|---|
| `@Idempotent` | 幂等性注解（基于 Redis SETNX 或本地 ConcurrentHashMap 实现分布式/本地幂等校验） |
| `IdempotentInterceptor` | 幂等性拦截器（HandlerInterceptor，拦截标注 `@Idempotent` 的方法） |
| `IdempotentStore` | 幂等存储接口 |
| `InMemoryIdempotentStore` | 内存幂等存储实现 |
| `IdempotentAutoConfiguration` | 幂等性自动配置 |
| `IdempotentException` | 幂等异常 |

### 16. 工具类与开关注解

| 类 / 注解 | 说明 |
|---|---|
| `ClientIpResolver` | 统一客户端 IP 解析（多级代理 + 内网判断） |
| `CachedBodyHttpServletRequestWrapper` | 请求体缓存包装器（多次读取） |
| `SafeFilterChainBuilder` | 安全过滤器链编程式构建器（动态注册过滤器 + 排序） |
| `@EnableYdszSafe` | 启用 ydsz 安全模块自动装配（显式开启所有过滤器和 AOP 切面） |
| `SafeConfiguration` | 安全模块核心自动配置（`@AutoConfiguration`，`@EnableScheduling`） |

## 接入方式

### 1. POM 引入依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-safe</artifactId>
</dependency>
```

### 2. 配置启用

```yaml
ydsz:
  safe:
    enabled: true
    security-headers:
      enabled: true
    xss:
      enabled: true
      mode: converter                # filter / converter（默认 converter）
    csrf:
      enabled: true
      mode: SYNCHRONIZER             # SYNCHRONIZER / DOUBLE_SUBMIT
    sensitive:
      enabled: true
    ratelimit:
      enabled: true
```

### 3. 代码启用

在 Spring Boot 主类上添加 `@EnableYdszSafe` 注解即可启用安全模块自动装配：

```java
import com.njydsz.common.safe.annotation.EnableYdszSafe;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableYdszSafe
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

## 配置项

### 安全总开关

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.safe.enabled` | true | 安全模块总开关（关闭后所有子能力不可用） |

### XSS 防护（`ydsz.safe.xss`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `enabled` | true | 是否启用 XSS 过滤器 |
| `order` | 2 | 过滤器注册顺序 |
| `mode` | converter | XSS 处理模式：`filter` / `converter` |
| `json-enabled` | true | 是否启用 Jackson JSON Body XSS 防护 |
| `custom-patterns` | 空 | 自定义 XSS 检测正则表达式列表 |
| `allowed-tags` | a,img,br,p,div,span,strong,em,ul,ol,li | 白名单 HTML 标签 |
| `allowed-attributes` | href,src,alt,title,class,id,target | 白名单 HTML 属性 |
| `tag-whitelist-enabled` | false | 是否启用 HTML 标签白名单过滤 |
| `strict-level` | MEDIUM | 检测严格级别：`LOW` / `MEDIUM` / `HIGH` |
| `excludes` | /error, /favicon.ico, /actuator/** | 排除路径列表 |

### CSRF 防护（`ydsz.safe.csrf`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `enabled` | true | 是否启用 CSRF 防护 |
| `mode` | SYNCHRONIZER | 防护模式：`SYNCHRONIZER` / `DOUBLE_SUBMIT` |
| `order` | 3 | 过滤器注册顺序 |
| `token-header` | X-CSRF-TOKEN | 令牌请求头名称 |
| `token-parameter` | _csrf | 令牌请求参数名 |
| `expiration-seconds` | 3600 | 令牌过期时间（秒） |
| `session-id-header` | X-Session-Id | 会话 ID 请求头 |
| `check-origin` | true | 是否启用 Origin/Referer 校验 |
| `allowed-origins` | 空 | 允许的 Origin 列表（支持通配符） |
| `cookie-secure` | null | Cookie Secure 标志（null=动态） |
| `same-site` | Lax | Cookie SameSite 属性：`Strict` / `Lax` / `None` |
| `excludes` | 空 | 排除路径列表 |

### 安全响应头（`ydsz.safe.security-headers`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `enabled` | true | 是否启用安全响应头 |
| `order` | 1 | 过滤器注册顺序 |
| `xss-protection` | 1; mode=block | X-XSS-Protection 头 |
| `content-type-options` | nosniff | X-Content-Type-Options 头 |
| `frame-options` | SAMEORIGIN | X-Frame-Options 头 |
| `hsts` | max-age=31536000; includeSubDomains | Strict-Transport-Security 头 |
| `csp` | default-src 'self' | Content-Security-Policy 头 |
| `referrer-policy` | strict-origin-when-cross-origin | Referrer-Policy 头 |
| `permissions-policy` | geolocation=(), microphone=(), camera=() | Permissions-Policy 头 |
| `excludes` | 空 | 排除路径列表 |

### 敏感数据脱敏（`ydsz.safe.sensitive`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `enabled` | true | 是否启用敏感数据脱敏 |
| `max-depth` | 10 | 最大递归深度 |
| `log-level` | DEBUG | 脱敏日志级别 |
| `statistics-enabled` | false | 是否启用脱敏统计 |
| `global-rules` | 空 | 全局脱敏规则列表（field-name / type / replace-char / enabled） |

### 限流（`ydsz.safe.ratelimit`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `enabled` | true | 是否启用限流模块 |
| `default-mode` | LOCAL | 默认限流模式：`LOCAL` / `CLUSTER` |
| `fallback-on-error` | PASS | 异常降级策略：`PASS` / `BLOCK` |
| `metrics-enabled` | true | 是否启用 Micrometer 指标 |
| `cluster-key-prefix` | ydsz:ratelimit: | 集群限流 Redis Key 前缀 |
| `aop-enabled` | true | 是否启用 @RateLimit AOP 切面 |
| `rules` | 空 | 规则列表（resource / threshold / window-millis / dimension / algorithm / mode） |
| `hot-params` | 空 | 热点参数特殊配置 |

### IP 访问控制（`ydsz.safe.ip-access`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `enabled` | false | 是否启用 IP 访问控制 |
| `mode` | BLACKLIST | 访问控制模式：`BLACKLIST` / `WHITELIST` |
| `redis-key-prefix` | safe:ip: | Redis Key 前缀 |
| `default-block-seconds` | 3600 | 默认封禁时长（秒） |
| `local-cache-size` | `10000` | 本地缓存大小 |
| `local-cache-ttl-seconds` | `10` | 本地缓存 TTL（秒） |
| `static-blacklist` | 空 | 静态黑名单（启动时加载，支持 IP 和 CIDR） |
| `static-whitelist` | 空 | 静态白名单 |
| `excludes` | 空 | 排除路径列表 |

### API 签名验证（`ydsz.safe.api-signature`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `enabled` | false | 是否启用 API 签名验证 |
| `app-id` | 空 | 应用 ID |
| `app-secret` | 空 | 应用密钥（Base64 编码） |
| `timestamp-tolerance-seconds` | 300 | 时间戳容差（秒） |
| `nonce-expire-seconds` | 600 | Nonce 过期时间（秒） |
| `header-timestamp` | X-Timestamp | 时间戳请求头 |
| `header-nonce` | X-Nonce | Nonce 请求头 |
| `header-signature` | X-Signature | 签名请求头 |
| `header-app-id` | X-App-Id | 应用 ID 请求头 |
| `excludes` | 空 | 排除路径列表 |

### 自动封禁（`ydsz.safe.auto-block`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `enabled` | true | 是否启用自动封禁 |
| `threshold` | 10 | 触发封禁的事件数量阈值 |
| `window-seconds` | 60 | 滑动窗口大小（秒） |

### 字段加密（`ydsz.safe.field-encryption`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `enabled` | true | 是否启用字段加密 |
| `default-key-version` | 1 | 默认密钥版本 |
| `failure-strategy` | THROW | 解密失败策略：`THROW` / `RETURN_MASKED` / `RETURN_ORIGINAL` |
| `masked-value` | **** | RETURN_MASKED 时返回的脱敏值 |
| `keys` | 空 | 密钥映射（版本号 → Base64 编码的 32 字节密钥） |

### 安全告警（`ydsz.safe.alert`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `enabled` | true | 是否启用安全事件告警 |

## 使用示例

### 1. 方法级限流

```java
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.common.safe.ratelimit.enums.RateLimitAlgorithm;
import com.njydsz.common.safe.ratelimit.enums.RateLimitDimension;
import com.njydsz.common.safe.ratelimit.enums.RateLimitMode;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LoginController {

    /** 用户级限流：每个用户每秒 5 次 */
    @PostMapping("/login")
    @RateLimit(resource = "user.login",
               threshold = 5,
               dimension = RateLimitDimension.USER,
               keyParam = 0)
    public Result<String> login(String username, String password) {
        return Result.success("ok");
    }

    /** 集群限流：令牌桶算法 */
    @PostMapping("/seckill")
    @RateLimit(resource = "seckill",
               threshold = 1000,
               mode = RateLimitMode.CLUSTER,
               algorithm = RateLimitAlgorithm.TOKEN_BUCKET)
    public Result<String> seckill() {
        return Result.success("ok");
    }
}
```

### 2. 敏感数据脱敏（注解式）

```java
import com.njydsz.common.safe.sensitive.SensitiveData;
import com.njydsz.common.safe.sensitive.SensitiveType;
import lombok.Data;

@Data
@SensitiveData
public class UserVO {
    private Long id;

    @SensitiveData(type = SensitiveType.PHONE)
    private String phone;          // 138****1234

    @SensitiveData(type = SensitiveType.ID_CARD)
    private String idCard;         // 110***********1234

    @SensitiveData(type = SensitiveType.EMAIL)
    private String email;          // a***@example.com

    @SensitiveData(type = SensitiveType.NAME)
    private String realName;       // 张**
}
```

### 3. 字段级加密（MyBatis 集成）

```java
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.safe.encrypt.EncryptField;
import com.njydsz.common.safe.encrypt.EncryptTypeHandler;
import lombok.Data;

@Data
@TableName(autoResultMap = true)
public class User {
    private Long id;

    @TableField(typeHandler = EncryptTypeHandler.class)
    @EncryptField
    private String idCard;          // 身份证号加密存储

    @TableField(typeHandler = EncryptTypeHandler.class)
    @EncryptField(keyVersion = 2)
    private String phone;           // 手机号加密存储（使用 v2 密钥）
}
```

### 4. CSRF Token 双模式

```yaml
ydsz:
  safe:
    csrf:
      enabled: true
      mode: DOUBLE_SUBMIT          # SPA / 微服务架构推荐
      cookie-secure: true          # 生产环境强制 HTTPS
      same-site: Strict
      allowed-origins:
        - https://*.ydsz.example.com
```

### 5. IP 黑白名单（自动封禁联动）

```yaml
ydsz:
  safe:
    ip-access:
      enabled: true
      mode: BLACKLIST
      static-blacklist:
        - 192.168.1.100
        - 10.0.0.0/8               # 支持 CIDR 网段
      static-whitelist:
        - 127.0.0.1
    auto-block:
      enabled: true
      threshold: 10                # 同一 IP 60s 内 10 次安全事件自动封禁
      window-seconds: 60
```

### 6. API 签名验证

```yaml
ydsz:
  safe:
    api-signature:
      enabled: true
      app-id: "ydsz-web"
      app-secret: "Base64EncodedSecretKey"
      timestamp-tolerance-seconds: 300
      nonce-expire-seconds: 600
      excludes:
        - /api/public/**
        - /actuator/**
```

### 7. 二级认证

```java
import com.njydsz.common.safe.annotation.SecondaryAuth;
import com.njydsz.common.safe.annotation.SensitiveLevel;

@SecondaryAuth(scene = "password_change", level = SensitiveLevel.HIGH)
@PutMapping("/api/v1/user/password")
public YdszResponse<Void> changePassword(@RequestBody ChangePasswordDTO dto) {
    // 需通过二级认证（密码确认）后才能执行
}
```

### 8. 幂等性

```java
import com.njydsz.common.safe.idempotent.Idempotent;
import java.util.concurrent.TimeUnit;

@PostMapping("/orders")
@Idempotent(key = "#request.orderNo", expire = 300, timeUnit = TimeUnit.SECONDS)
public YdszResponse<OrderVO> createOrder(@RequestBody CreateOrderRequest request) {
    // 同一 orderNo 在 300 秒内只执行一次
}
```

## SPI 扩展点

| SPI 接口 | 用途 | 实现方 |
|---|---|---|
| `RateLimitRuleProvider` | 限流规则来源抽象（支持配置中心 / 数据库 / API 动态规则） | `ConfigRuleProvider`（默认，从 `RateLimitProperties` 加载静态规则） |
| `RateLimitRuleListener` | 限流规则变更监听器（规则增删改时回调） | `RateLimitMetricsCollector`（指标采集） |
| `CaptchaGenerator` | 验证码生成器 | 内置默认实现 |
| `CsrfTokenRepository` | CSRF Token 存储接口 | `RedisCsrfTokenRepository`（@Primary）、`InMemoryCsrfTokenRepository` |
| `CsrfTokenGenerator` | CSRF Token 生成接口 | `DefaultCsrfTokenGenerator` |
| `ClusterRateLimiter` | 集群限流器接口 | `RedisClusterRateLimiter`（基于 StringRedisTemplate） |
| `RateLimiter` | 限流算法接口 | `TokenBucketLimiter`（令牌桶） |
| `SecurityAlertListener` | 安全告警监听器接口 | `DefaultSecurityAlertLogger` |
| `IdempotentStore` | 幂等存储接口 | `InMemoryIdempotentStore` |

## 健康检查

| 端点 | 说明 | 触发条件 |
|---|---|---|
| `/actuator/health/safe` | 安全模块健康检查：Redis 连通性、各安全能力注册状态（XSS / CSRF / 限流 / IP 访问 / API 签名 / 脱敏 / 验证码 / 加密 / 指标 / 审计日志） | `ydsz.safe.enabled=true`（默认 true）+ classpath 存在 `HealthIndicator` |

健康检查返回示例：

```json
{
  "status": "UP",
  "details": {
    "module": "safe",
    "redis": "connected",
    "redisResponseTimeMs": 3,
    "warning": "Redis unavailable - rate limiting/CSRF degraded to local mode",
    "capabilities": {
      "xss": "OWASP Sanitizer + configurable policies",
      "csrf": "Synchronizer / Double Submit dual mode",
      "rateLimit": "Token Bucket + 自研熔断器 (AtomicReference CAS 状态机)"
    }
  }
}
```

## 注意事项

1. **过滤器执行顺序**：`IpAccessFilter (HIGHEST_PRECEDENCE)` → `SecurityHeaderFilter (order=1)` → `XssFilter (order=2)` → `CsrfFilter (order=3)` → `RateLimitFilter (HIGHEST_PRECEDENCE+1)` → `ApiSignatureFilter (HIGHEST_PRECEDENCE+4)`。恶意 IP 在进入其他过滤器之前即被拦截。
2. **XSS 双模式互斥**：`mode=filter`（全局参数清洗）与 `mode=converter`（JSON 反序列化清洗）二选一，默认 `converter`，避免双重清洗。富文本场景建议 `filter` 模式 + 白名单标签。
3. **CSRF 模式选择**：单体应用推荐 `SYNCHRONIZER`（服务端 Redis 存储 Token）；SPA / 微服务架构推荐 `DOUBLE_SUBMIT`（无状态，Cookie + Header 双重提交，无需 Redis）。
4. **限流降级**：Redis 不可用时，集群限流自动降级为本地限流（`fallback-on-error=PASS` 默认放行，可改为 `BLOCK` 拒绝）。`@RateLimit` AOP 通过 `ydsz.safe.ratelimit.aop-enabled` 控制。
5. **字段加密密钥轮换**：`@EncryptField(keyVersion=N)` 配合 `ydsz.safe.field-encryption.keys` 多版本密钥映射，支持平滑轮换。解密失败时按 `failure-strategy` 处理（默认 `THROW` fail-safe）。
6. **自动封禁联动**：`SecurityEventAggregator` 监听安全事件，同一 IP 在 `window-seconds` 内触发超过 `threshold` 次事件时自动调用 `IpAccessService` 封禁 IP（需启用 `ydsz.safe.ip-access.enabled=true`）。
7. **Redis 降级**：Redis 不可用时，限流 / CSRF Token 降级为本地内存模式，健康检查会输出 warning 但状态仍为 UP。
8. **宿主需开启 @EnableScheduling**：`NonceCache` 防重放清理任务依赖 `@EnableScheduling`（本模块 `SafeConfiguration` 已标注 `@EnableScheduling`）。
9. **MyBatis 字段加密**：使用 `@EncryptField` 时实体类必须 `@TableName(autoResultMap = true)`，字段必须 `@TableField(typeHandler = EncryptTypeHandler.class)`。
10. **可选依赖**：`micrometer-core`、`mybatis-plus-core`、`spring-boot-actuator`、`spring-boot-health` 均为 optional，未引入时对应能力自动降级或不可用。
11. **二级认证场景隔离**：`@SecondaryAuth` 支持不同业务场景独立验证（如 `password_change` / `role_assign`），与 `@SensitiveOperation`（全局单一验证）形成互补。
12. **幂等性实现**：`@Idempotent` 基于 Redis SETNX 或本地 ConcurrentHashMap 实现，返回 429 Too Many Requests 表示重复请求。

## 变更记录

- **1.0.0**（2026-08-18）：新增二级认证（`@SecondaryAuth` / `@SensitiveOperation` / `@SensitiveLevel`）、幂等性（`@Idempotent` / `IdempotentInterceptor` / `IdempotentStore` / `IdempotentAutoConfiguration`）；新增限流决策结果（`RateLimitDecision`）、限流上下文（`RateLimitContext`）、限流自动配置（`RateLimitAutoConfiguration`）；新增熔断器抽象基类（`AbstractCircuitBreaker`）、熔断器配置（`CircuitBreakerProperties`）、热点参数规则（`HotParamRule`）；新增字段加密配置（`EncryptFieldProperties`）、API 签名自动配置（`ApiSignatureAutoConfiguration`）；新增敏感数据配置（`SensitiveDataProperties`）、敏感数据处理异常（`SensitiveDataProcessingException`）；新增安全告警配置（`SafeAlertProperties`）；新增限流结果枚举（`RateLimitResult`）；新增列脱敏规则/上下文（`ColumnDesensitizationRule` / `ColumnDesensitizationContext`）；新增 `ConfigRuleProvider` 限流规则提供者。
- **1.0.0**（2026-08-17）：补全 SSRF 防护（`SsrfHttpRequestInterceptor` / `HttpConnectionValidator`）、`SafeFilterChainBuilder`（过滤器链构建器）、`RateLimitResponseDecorator`（限流响应装饰器）文档
- **1.0.0**（2026-08-16）：删除低价值模块（BotDetection、Captcha）；SecurityEventRingBuffer 标记 @Deprecated；限流算法收敛（废弃 COUNTER/SLIDING_WINDOW/LEAKY_BUCKET/CONCURRENCY，统一使用 TOKEN_BUCKET）；熔断器替换为 Resilience4j；移除 SQL 注入正则过滤器；配置前缀收敛（`ydsz.ratelimit` → `ydsz.safe.ratelimit`）；引入 Sentinel 限流扩展；建立度量标准（SLO/指标/热更新）；补全 FieldEncryptionService 测试。
- **1.0.0**（2026-08-16）：限流算法收敛（废弃 COUNTER/SLIDING_WINDOW/LEAKY_BUCKET/CONCURRENCY，统一使用 TOKEN_BUCKET）；熔断器替换为 Resilience4j；移除 SQL 注入正则过滤器；配置前缀收敛（`ydsz.ratelimit` → `ydsz.safe.ratelimit`）；补全 FieldEncryptionService 测试。
- **1.0.0**（2026-08-02）：对标 common-jdbc 标准格式重构 README，补全全部 9 个章节，覆盖 14 项核心能力、10 个 Properties 配置类、9 个 SPI 接口、1 个 HealthIndicator。
