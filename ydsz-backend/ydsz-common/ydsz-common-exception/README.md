# ydsz-common-exception

YDSZ 统一异常处理框架 — 异常层级体系、错误码管理、RFC 7807 ProblemDetail、国际化 i18n、全局异常处理器、异常构建器、异常指标监控。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L3 基础服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **版本** | 1.4.0 |

## 核心能力

### 异常层级体系

```
RuntimeException
  └─ AbstractYdszException            ← YDSZ 异常抽象基类
       ├─ BusinessException             ← 业务异常（HTTP 4xx，可预期）
       ├─ SysException                  ← 系统异常（HTTP 5xx，不可预期）
       ├─ ValidationException           ← 参数校验异常（HTTP 400）
       ├─ YdszSecurityException         ← 安全异常（HTTP 403）
       ├─ YdszTimeoutException          ← 超时异常（HTTP 504）
       ├─ ExternalException             ← 外部服务异常（HTTP 502）
       ├─ InfrastructureException       ← 基础设施异常（HTTP 500）
       ├─ ConcurrencyException          ← 并发冲突异常（HTTP 409）
       ├─ DuplicateException            ← 重复提交异常（HTTP 409）
       ├─ RateLimitException            ← 限流异常（HTTP 429）
       ├─ CircuitBreakerException       ← 熔断异常（HTTP 503）
       └─ DegradeException              ← 降级异常（HTTP 503）
```

### 核心类

| 类 | 说明 |
|---|---|
| `AbstractYdszException` | 异常抽象基类（错误码 + i18n 消息 + 扩展数据 + 链式调用） |
| `YdszExceptionBuilder<T>` | 异常构建器（CRTP 模式，类型安全的链式构建） |
| `UnifiedExceptionCode` | 统一异常码枚举（A/B/C 三类编码体系） |
| `ExceptionCode` | 异常码接口（各业务模块可扩展自己的错误码枚举） |
| `ExceptionCodeRegistry` | 异常码全局注册表（支持跨模块查找） |
| `ProblemDetail` | RFC 7807 HTTP Problem Details 标准格式 |

### 异常特性

- **链式构建**：`new BusinessException(UnifiedExceptionCode.NOT_FOUND).data("userId", 123).data("tenant", "acme")`
- **懒加载 i18n**：异常被抛出时只存储 key + params，`getMessage()` 调用时才解析国际化消息（DCL 线程安全）
- **扩展数据**：`ConcurrentHashMap<String, Object>` 类型安全的附加数据
- **错误码体系**：`ExceptionCode` 接口 + `UnifiedExceptionCode` 标准实现 + `ExceptionCodeRegistry` 全局注册
- **异常分类**：`ExceptionCategory`（BUSINESS/SYSTEM/EXTERNAL/SECURITY/VALIDATION/INFRASTRUCTURE/TIMEOUT/CONCURRENCY/RATE_LIMIT/DUPLICATE）
- **异常级别**：`ExceptionLevel`（INFO/WARN/ERROR/FATAL）— 监控告警系统可据此决定告警等级

### 错误码编码规范

```
[类型(1位)] + [模块(2位)] + [序号(3位)]
```

| 类型 | 含义 | HTTP 范围 | 示例 |
|---|---|---|---|
| A | 业务级错误 | 4xx | A01052 = 参数错误 |
| B | 系统级错误 | 5xx | B01051 = 系统内部错误 |
| C | 安全级错误 | 401/403 | C01051 = 安全访问被拒绝 |

### 全局异常处理

| 类 | 说明 |
|---|---|
| `MvcExceptionHandler` | Spring MVC 全局异常处理器（`@RestControllerAdvice`） |
| `WebFluxExceptionHandler` | Spring WebFlux 全局异常处理器 |
| `ValidationExceptionHandler` | Jakarta Validation 校验异常处理器 |
| `JdbcExceptionHandler` | Spring JDBC 数据访问异常处理器 |
| `BaseExceptionHandler` | 异常处理器抽象基类（模板方法模式） |

兼容处理的框架异常：
- `MethodArgumentNotValidException` / `ConstraintViolationException` / `BindException`
- `HttpRequestMethodNotSupportedException` / `HttpMessageNotReadableException`
- `MaxUploadSizeExceededException` / `NoHandlerFoundException`
- `MissingServletRequestParameterException` / `MethodArgumentTypeMismatchException`
- `MissingRequestHeaderException`
- `DataAccessException`（Spring JDBC）
- `IllegalArgumentException` / `IllegalStateException` / `NullPointerException`
- 兜底 `Exception` 处理

### RFC 7807 ProblemDetail

支持 RFC 7807 HTTP Problem Details 标准格式输出，通过配置开关切换：

```yaml
ydsz:
  exception:
    response-format: problem-detail  # 默认 base-response
```

```json
{
  "type": "https://ydsz.njydsz.com/errors/business",
  "title": "BusinessException",
  "status": 400,
  "detail": "用户不存在",
  "instance": "/api/v1/users/123",
  "traceId": "a1b2c3d4",
  "errorCode": "A01057",
  "timestamp": "2026-07-15T10:30:00Z",
  "extensions": {
    "userId": 123
  }
}
```

### 异常指标监控

集成 Micrometer，所有异常处理器统一记录异常指标：

| 指标 | 类型 | Tag 维度 |
|---|---|---|
| `exception.count` | Counter | type, level, category, code |
| `exception.handler.duration` | Timer | type |

配置开关：
```yaml
ydsz:
  exception:
    metrics-enabled: true  # 默认启用
```

### 分布式追踪

基于 SLF4J MDC 的 `TraceContext`，自动注入/传递/清理 traceId：

- `TraceContextFilter` — Servlet 过滤器，请求入口自动提取/生成 traceId
- 兼容 W3C Trace Context（`X-Trace-Id`）和 OpenTelemetry/Zipkin（`X-B3-TraceId`）
- 自动清理 MDC（线程池复用安全）

### 国际化 i18n

- **多环境适配**：开发环境实时加载（cacheSeconds=0），生产环境缓存（cacheSeconds=3600）
- **fail-fast 校验**：启动时校验所有已注册 `ExceptionCode` 的 i18n key 是否可解析
- **懒加载解析**：异常抛出时只存储 key + params，`getMessage()` 调用时才解析
- **多语言支持**：zh_CN（默认）、en_US、zh_TW
- **Hibernate Validator 集成**：校验消息国际化

## 使用示例

```java
// 1. 抛出业务异常
throw new BusinessException(UnifiedExceptionCode.NOT_FOUND)
    .data("userId", userId)
    .data("tenant", tenantId);

// 2. 使用 Builder 模式
throw BusinessException.builder()
    .code("CUSTOM_CODE")
    .key("custom.error")
    .httpStatus(422)
    .level(ExceptionLevel.WARN)
    .message("自定义消息")
    .cause(originalException)
    .build();

// 3. 使用工厂方法
throw BusinessException.of(UnifiedExceptionCode.PARAM_ERROR);

// 4. 抛出系统异常
throw new SysException(UnifiedExceptionCode.DATABASE_ERROR, cause)
    .data("dataSource", "master");

// 5. 自定义异常码枚举
public enum UserExceptionCode implements ExceptionCode {
    USER_NOT_FOUND("U10001", "user.not.found", 404),
    USER_ALREADY_EXISTS("U10002", "user.already.exists", 409);

    private final String code;
    private final String key;
    private final int httpStatus;

    UserExceptionCode(String code, String key, int httpStatus) {
        this.code = code;
        this.key = key;
        this.httpStatus = httpStatus;
    }

    @Override
    public String getCode() { return code; }
    @Override
    public String getKey() { return key; }
    @Override
    public int getHttpStatus() { return httpStatus; }
}
```

## 配置项

```yaml
ydsz:
  exception:
    metrics-enabled: true              # 异常指标统计开关
    global-handler-enabled: true       # 全局异常处理器开关
    trace-enabled: true                # TraceId 过滤器开关
    response-format: base-response     # 响应格式：base-response | problem-detail
    include-stack-trace: false         # 是否在响应中包含堆栈信息
  i18n:
    basename: "classpath:i18n/messages"
    encoding: "UTF-8"
    dev-cache-seconds: 0
    prod-cache-seconds: 3600
    fallback-to-system-locale: false
    supported-locales: [zh_CN, en_US, zh_TW]
    lang-param-name: "lang"
    validate-on-startup: true
```

## 自动配置

以下 AutoConfiguration 通过 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 自动注册：

| 配置类 | 条件 | 注册的 Bean |
|---|---|---|
| `I18nConfiguration` | `MessageSource` 在类路径 | `MessageSource`, `Validator`, i18n 解析器注入 |
| `WebI18nConfiguration` | `LocaleResolver` 在类路径 | `LocaleResolver`, `LocaleChangeInterceptor` |
| `ExceptionMetricsAutoConfiguration` | `MeterRegistry` Bean 存在 | `ExceptionMetrics` |
| `TraceFilterAutoConfiguration` | `Filter` 在类路径 | `TraceContextFilter` |
| `MvcExceptionHandlerAutoConfiguration` | Servlet Web 应用 | `MvcExceptionHandler` |
| `ValidationExceptionHandlerAutoConfiguration` | Servlet Web 应用 + validation | `ValidationExceptionHandler` |
| `WebFluxExceptionHandlerAutoConfiguration` | Reactive Web 应用 | `WebFluxExceptionHandler` |
| `JdbcExceptionHandlerAutoConfiguration` | `DataAccessException` 在类路径 | `JdbcExceptionHandler` |

## 依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-exception</artifactId>
</dependency>
```
