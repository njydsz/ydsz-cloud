# ydsz-common-exception

YDSZ 统一异常处理框架 — 异常层级体系、错误码管理、RFC 7807 ProblemDetail、国际化 i18n、全局异常处理器、异常构建器、异常指标监控、异常告警、堆栈脱敏、健康检查。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L3 基础服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **版本** | 1.1.0 |

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
| `ExceptionCodeRegistry` | 异常码全局注册表（支持跨模块查找，静态 API） |
| `ResultCodeRegistry` | 错误码文档注册表（按模块分组，供 Actuator 端点展示） |
| `ResultCodeScanner` | 启动时自动扫描 `@YdszResultCode` 注解枚举并注册到两个注册中心 |
| `ProblemDetail` | RFC 7807 HTTP Problem Details 标准格式 |

### 异常特性

- **链式构建**：`new BusinessException(UnifiedExceptionCode.NOT_FOUND).data("userId", 123).data("tenant", "acme")`
- **懒加载 i18n**：异常被抛出时只存储 key + params，`getMessage()` 调用时才解析国际化消息（DCL 线程安全）
- **扩展数据**：`ConcurrentHashMap<String, Object>` 类型安全的附加数据
- **错误码体系**：`ExceptionCode` 接口 + `UnifiedExceptionCode` 标准实现 + `@YdszResultCode` 注解自动扫描注册
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

### 错误码自动注册

使用 `@YdszResultCode` 注解标记业务模块的错误码枚举类，启动时 `ResultCodeScanner` 自动扫描并注册：

```java
@YdszResultCode(module = "user", description = "用户中心错误码")
public enum UserExceptionCode implements ExceptionCode {
    USER_NOT_FOUND("U10001", "user.not.found", 404),
    USER_ALREADY_EXISTS("U10002", "user.already.exists", 409);
    // ...
}
```

扫描器同时注册到两个注册中心：
- `ResultCodeRegistry` — 供 `/actuator/exception-codes` 端点按模块分组展示
- `ExceptionCodeRegistry` — 供 `ExceptionCode.fromCode()` 反查枚举实例

这解决了静态块注册的类加载确定性问题，确保所有错误码在应用就绪后被确定性注册。

### 全局异常处理

| 类 | 说明 |
|---|---|
| `BaseExceptionHandler` | 异常处理器抽象基类（模板方法模式，统一日志/指标/告警/脱敏/响应构建） |
| `MvcExceptionHandler` | Spring MVC 全局异常处理器（`@RestControllerAdvice`） |
| `WebFluxExceptionHandler` | Spring WebFlux 全局异常处理器（继承 `BaseExceptionHandler`） |
| `ValidationExceptionHandler` | Jakarta Validation 校验异常处理器（含请求路径和 traceId 提取） |
| `JdbcExceptionHandler` | Spring JDBC 数据访问异常处理器（优先从 MDC 提取 traceId） |
| `GrpcExceptionTranslator` | gRPC 异常翻译器（YDSZ 异常 → gRPC Status） |

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
    problem-detail-type-base-url: https://api.example.com/errors  # type URI 基础 URL
```

```json
{
  "type": "https://api.example.com/errors/business",
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

集成 Micrometer，所有异常处理器统一记录异常指标（通过 `BaseExceptionHandler.recordMetrics()` 统一入口）：

| 指标 | 类型 | Tag 维度 | 说明 |
|---|---|---|---|
| `exception.count` | Counter | type, level, category | 异常计数（code tag 默认不包含，可通过配置开启） |
| `exception.handler.duration` | Timer | type | 异常处理耗时 |

配置开关：
```yaml
ydsz:
  exception:
    metrics-enabled: true              # 默认启用
    metrics-include-code-tag: false    # 高基数 code tag 默认关闭，防止 Prometheus 指标爆炸
```

### 异常告警

当异常级别为 ERROR 或 FATAL 时，自动发布告警事件给所有注册的 `ExceptionAlertListener`：

- **告警收敛**：同一 errorCode 在去重时间窗口内只告警一次（FATAL 级别忽略收敛）
- **静默期**：支持全局静默期配置，同一 errorCode 在静默期内不重复告警
- **异步执行**：告警监听器可异步执行，避免阻塞请求线程
- **多渠道**：支持钉钉、邮件、短信等多渠道告警（实现 `ExceptionAlertListener` 接口即可）

```yaml
ydsz:
  exception:
    alert-enabled: true
    alert-dedup-window-seconds: 60      # 去重时间窗口
    alert-silence-period-seconds: 300   # 静默期
    async-alert-enabled: true           # 异步告警
    async-alert-pool-size: 2            # 异步线程池大小
```

### 堆栈脱敏

`StackTraceSanitizer` 在生产环境中对异常堆栈进行脱敏处理：
- 移除框架堆栈帧（Spring/Tomcat/JDK 等）
- 隐藏敏感文件路径
- 限制堆栈深度
- **保留原始异常类型**：直接在原始异常对象上替换 StackTraceElement 数组，确保 `instanceof` 检查在脱敏后仍然有效

### 分布式追踪

基于 SLF4J MDC 的 `TraceContext`，自动注入/传递/清理 traceId：

- `TraceContextFilter` — Servlet 过滤器，请求入口自动提取/生成 traceId
- 兼容 W3C Trace Context（`X-Trace-Id`）和 OpenTelemetry/Zipkin（`X-B3-TraceId`）
- 自动清理 MDC（线程池复用安全）
- 所有异常处理器统一优先从 MDC 提取 traceId

### 国际化 i18n

- **多环境适配**：开发环境实时加载（cacheSeconds=0），生产环境缓存（cacheSeconds=3600）
- **fail-fast 校验**：启动时校验所有已注册 `ExceptionCode` 的 i18n key 是否可解析
- **懒加载解析**：异常抛出时只存储 key + params，`getMessage()` 调用时才解析
- **多语言支持**：zh_CN（默认）、en_US、zh_TW
- **Hibernate Validator 集成**：校验消息国际化

### 健康检查

`ExceptionHealthIndicator` 通过 Spring Boot Actuator 暴露异常模块运行状态：

- 全局异常处理器/指标/告警/追踪是否启用
- 响应格式（BaseResponse / ProblemDetail）
- 指标统计器是否可用
- 告警发布器是否可用、异步状态
- 错误码注册中心已注册模块数和错误码总数
- ProblemDetail type URI 基础 URL

访问路径：`/actuator/health`（在 details 中查看 `exception` 组件状态）

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

// 5. 自定义异常码枚举（自动注册）
@YdszResultCode(module = "user", description = "用户中心错误码")
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

// 6. 实现告警监听器
@Bean
public ExceptionAlertListener dingTalkAlertListener() {
    return event -> {
        // 发送钉钉告警
        dingTalkService.send(event.getLevel() + ": " + event.getMessage());
    };
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
    alert-enabled: true                # 异常告警开关
    alert-dedup-window-seconds: 60     # 告警去重时间窗口（秒）
    alert-silence-period-seconds: 300  # 告警静默期（秒）
    async-alert-enabled: true          # 异步告警开关
    async-alert-pool-size: 2           # 异步告警线程池大小
    metrics-include-code-tag: false    # 是否包含高基数 code tag
    problem-detail-type-base-url: about:blank  # ProblemDetail type URI 基础 URL
    doc-endpoint-enabled: true         # 错误码文档端点开关
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
| `ExceptionAlertAutoConfiguration` | `ydsz.exception.alert-enabled=true` | `ExceptionAlertPublisher` |
| `ExceptionHealthAutoConfiguration` | `HealthIndicator` 在类路径 | `ExceptionHealthIndicator` |
| `TraceFilterAutoConfiguration` | `Filter` 在类路径 | `TraceContextFilter` |
| `MvcExceptionHandlerAutoConfiguration` | Servlet Web 应用 | `MvcExceptionHandler` |
| `ValidationExceptionHandlerAutoConfiguration` | Servlet Web 应用 + validation | `ValidationExceptionHandler` |
| `WebFluxExceptionHandlerAutoConfiguration` | Reactive Web 应用 | `WebFluxExceptionHandler` |
| `JdbcExceptionHandlerAutoConfiguration` | `DataAccessException` 在类路径 | `JdbcExceptionHandler` |
| `ExceptionCodeDocEndpointAutoConfiguration` | Actuator + `doc-endpoint-enabled=true` | `ExceptionCodeDocEndpoint`, `ResultCodeScanner` |

## 依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-exception</artifactId>
</dependency>
```
