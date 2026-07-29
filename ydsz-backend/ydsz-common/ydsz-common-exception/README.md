# ydsz-common-exception

YDSZ 统一异常处理框架 — 异常层级体系、错误码管理、RFC 7807 ProblemDetail、国际化 i18n、全局异常处理器、异常构建器、异常指标监控、健康检查。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L3 基础服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **版本** | 1.2.0 |

## 核心能力

### 异常层级体系

```
RuntimeException
  └─ AbstractYdszException            ← YDSZ 异常抽象基类
       ├─ BusinessException             ← 业务异常（HTTP 4xx，可预期）
       └─ SysException                  ← 系统异常（HTTP 5xx，不可预期）
```

> **设计原则**：仅保留 `BusinessException`（业务可预期）和 `SysException`（系统不可预期）两个具体异常类。
> 安全/限流/重复提交/基础设施等场景通过 `ExceptionCategory` 分类标签区分，无需独立异常类。

### 核心类

| 类 | 说明 |
|---|---|
| `AbstractYdszException` | 异常抽象基类（错误码 + i18n 消息 + 扩展数据 + 链式调用） |
| `YdszExceptionBuilder<T>` | 异常构建器（CRTP 模式，类型安全的链式构建） |
| `UnifiedExceptionCode` | 统一异常码枚举（A/B/C 三类编码体系） |
| `ExceptionCode` | 异常码接口（各业务模块可扩展自己的错误码枚举） |
| `ExceptionCodeRegistry` | 异常码全局注册表（支持跨模块查找，静态 API） |
| `ResultCodeRegistry` | 错误码文档注册表（按模块分组，供 Actuator 端点展示） |
| `ResultCodeScanner` | 启动时自动扫描 `@YdszResultCode` 注解枚举并注册到注册中心 |
| `ProblemDetail` | RFC 7807 HTTP Problem Details 标准格式 |

### 异常特性

- **链式构建**：`new BusinessException(UnifiedExceptionCode.NOT_FOUND).data("userId", 123).data("tenant", "acme")`
- **懒加载 i18n**：异常被抛出时只存储 key + params，`getMessage()` 调用时才解析国际化消息（DCL 线程安全）
- **扩展数据**：`ConcurrentHashMap<String, Object>` 类型安全的附加数据
- **错误码体系**：`ExceptionCode` 接口 + `UnifiedExceptionCode` 标准实现 + `@YdszResultCode` 注解自动扫描注册
- **异常分类**：`ExceptionCategory`（BUSINESS/SYSTEM/EXTERNAL/SECURITY/VALIDATION/INFRASTRUCTURE/TIMEOUT/RATE_LIMIT）
- **异常级别**：`ExceptionLevel`（INFO/WARN/ERROR/FATAL）

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

使用 `@YdszResultCode` 注解标记业务模块的错误码枚举类，启动时 `ResultCodeScanner` 自动扫描并注册到 `ResultCodeRegistry`：

```java
@YdszResultCode(module = "user", description = "用户中心错误码")
public enum UserExceptionCode implements ExceptionCode {
    USER_NOT_FOUND("U10001", "user.not.found", 404),
    USER_ALREADY_EXISTS("U10002", "user.already.exists", 409);
    // ...
}
```

扫描器在 `ApplicationReadyEvent` 时确定性扫描注册，确保所有错误码在应用就绪后被注册，供 `/actuator/exception-codes` 端点按模块分组展示。

### 全局异常处理

| 类 | 说明 |
|---|---|
| `BaseExceptionHandler` | 异常处理器抽象基类（模板方法模式，统一日志/指标/响应构建） |
| `MvcExceptionHandler` | Spring MVC 全局异常处理器（`@RestControllerAdvice`） |
| `WebFluxExceptionHandler` | Spring WebFlux 全局异常处理器（继承 `BaseExceptionHandler`） |
| `ValidationExceptionHandler` | Jakarta Validation 校验异常处理器（含请求路径和 traceId 提取） |
| `JdbcExceptionHandler` | Spring JDBC 数据访问异常处理器（优先从 MDC 提取 traceId） |

兼容处理的框架异常：
- `MethodArgumentNotValidException` / `ConstraintViolationException` / `BindException`
- `HttpRequestMethodNotSupportedException` / `HttpMessageNotReadableException`
- `MaxUploadSizeExceededException` / `NoHandlerFoundException`
- `MissingServletRequestParameterException` / `MethodArgumentTypeMismatchException`
- `MissingRequestHeaderException`
- `DataAccessException`（Spring JDBC）
- `IllegalArgumentException` / `IllegalStateException` / `NullPointerException`
- 兜底 `Exception` 处理

### traceId 提取

所有异常处理器统一从 SLF4J MDC 提取 traceId，降级到 Request Header：

1. **MDC** — `MDC.get("traceId")`（由 common-core 的 `TraceIdGenerator` 或外部链路追踪组件注入）
2. **Request Header** — `X-Trace-Id` → `X-Request-Id`

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

### 国际化 i18n

- **多环境适配**：开发环境实时加载（cacheSeconds=0），生产环境缓存（cacheSeconds=3600）
- **fail-fast 校验**：启动时校验所有已注册 `ExceptionCode` 的 i18n key 是否可解析
- **懒加载解析**：异常抛出时只存储 key + params，`getMessage()` 调用时才解析
- **多语言支持**：zh_CN（默认）、en_US、zh_TW
- **Hibernate Validator 集成**：校验消息国际化

### 健康检查

`ExceptionHealthIndicator` 通过 Spring Boot Actuator 暴露异常模块运行状态：

- 全局异常处理器是否启用
- 异常指标统计器是否可用
- 响应格式（BaseResponse / ProblemDetail）
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
```

## 配置项

```yaml
ydsz:
  exception:
    metrics-enabled: true              # 异常指标统计开关
    global-handler-enabled: true       # 全局异常处理器开关
    response-format: base-response     # 响应格式：base-response | problem-detail
    include-stack-trace: false         # 是否在响应中包含堆栈信息
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
| `ExceptionHealthAutoConfiguration` | `HealthIndicator` 在类路径 | `ExceptionHealthIndicator` |
| `MvcExceptionHandlerAutoConfiguration` | Servlet Web 应用 | `MvcExceptionHandler` |
| `ValidationExceptionHandlerAutoConfiguration` | Servlet Web 应用 + validation | `ValidationExceptionHandler` |
| `WebFluxExceptionHandlerAutoConfiguration` | Reactive Web 应用 | `WebFluxExceptionHandler` |
| `JdbcExceptionHandlerAutoConfiguration` | `DataAccessException` 在类路径 | `JdbcExceptionHandler` |
| `ExceptionCodeDocEndpointAutoConfiguration` | Actuator + `doc-endpoint-enabled=true` | `ExceptionCodeDocEndpoint`, `ResultCodeScanner`, `ResultCodeRegistry` |

## 依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-exception</artifactId>
</dependency>
```

## 变更日志

### v1.2.0 — "简单够用"原则精简

遵循"简单够用"原则，删除过度设计代码，消除零引用特性，精简模块体积。

**删除的死代码（22 文件 + 3 空目录）**：

| 分类 | 删除内容 | 删除原因 |
|---|---|---|
| 零引用异常类 | `CircuitBreakerException` / `DegradeException` / `ConcurrencyException` / `ExternalException` / `YdszTimeoutException` / `ValidationException` | 全项目零业务引用 |
| 错误码过度设计 | `ErrorCodeEncoder` / `ErrorCodeDecoder` / `ErrorCodeFactory` / `ErrorCodeDocGenerator` / `SubErrorCode` / `ExternalExceptionCode` / `RateLimitExceptionCode` | `A01001-0001#a3f9` 编码格式零使用，子错误码体系零使用 |
| gRPC 翻译器 | `GrpcExceptionTranslator` + `grpc-api` 依赖 | 项目无 gRPC 服务 |
| TraceContext 重复 | `TraceContext` / `TraceContextFilter` / `TraceFilterAutoConfiguration` | 与 common-core 的 `TraceIdGenerator` 功能重叠 |
| 告警体系 | `ExceptionAlertEvent` / `ExceptionAlertListener` / `ExceptionAlertPublisher` / `ExceptionAlertAutoConfiguration` | 全项目零监听器实现 |
| 堆栈脱敏 | `StackTraceSanitizer` | 从未注入到 `BaseExceptionHandler` |

**简化的现有代码**：

| 文件 | 简化内容 |
|---|---|
| `ExceptionCode` | 删除 `getSubCode()` / `getFullCode()` / `fromCode()` 方法 |
| `AbstractYdszException` | 删除 `subCode` / `userMessage` / `getFullCode()` |
| `ExceptionInfo` | 删除 `subCode` / `fullCode` / `encodedCode` / `traceIdShort` 字段及相关方法 |
| `ExceptionProperties` | 删除 6 个告警/trace 配置项（`alertEnabled` / `traceEnabled` / `alertDedupWindowSeconds` / `alertSilencePeriodSeconds` / `asyncAlertEnabled` / `asyncAlertPoolSize`） |
| `BaseExceptionHandler` | 删除 `alertPublisher` / `stackTraceSanitizer` 字段和告警发布逻辑 |
| `ExceptionHealthIndicator` | 移除告警/脱敏检查项 |
| `MvcExceptionHandler` / `WebFluxExceptionHandler` / `JdbcExceptionHandler` / `ValidationExceptionHandler` | `TraceContext.getTraceId()` → `MDC.get("traceId")`，移除告警注入 |
| 各 AutoConfiguration 类 | 移除 `ExceptionAlertPublisher` 参数 |
| `AutoConfiguration.imports` | 移除 `ExceptionAlertAutoConfiguration` / `TraceFilterAutoConfiguration` |
| `additional-spring-configuration-metadata.json` | 移除 6 个告警/trace 配置项 |
| `pom.xml` | 移除 `grpc-api` 依赖 |
| `reflect-config.json` | 移除已删除类的反射配置 |

**保留的异常类**：`BusinessException` / `SysException` / `AbstractYdszException` / `YdszExceptionBuilder`

**额外删除的零引用异常类**（v1.2.0 补充）：`InfrastructureException` / `YdszSecurityException` / `DuplicateException` / `RateLimitException`（引用已迁移到 `SysException` / `BusinessException`）

### v1.1.0 — 深度优化

- P0: `WebFluxExceptionHandler` 继承 `BaseExceptionHandler` 统一体系
- P0: `ExceptionMetrics` 接入 `recordHandlerDuration()` 计时
- P0: `StackTraceSanitizer` 已删除（v1.2.0 零引用清理）
- P1: `ExceptionProperties` 添加 `@Validated` + JSR-303
- P1: `ResultCodeScanner` 双注册中心统一 + 确定性扫描
- P2: `ProblemDetail` type URI 配置化
- P3: 新建 `ExceptionHealthIndicator`
