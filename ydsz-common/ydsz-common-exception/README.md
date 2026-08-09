# ydsz-common-exception

> 统一异常处理框架（L3 基础服务层）

提供 YDSZ 统一异常体系、错误码注册中心、RFC 7807 ProblemDetail、国际化 i18n、全局异常处理器（MVC / WebFlux / JDBC / Validation）、异常构建器、Micrometer 指标监控、Actuator 健康检查与文档端点等开箱即用能力，是所有业务模块异常处理的统一基座。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L3 基础服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 提供统一异常层级体系、错误码注册中心、RFC 7807 ProblemDetail、国际化 i18n、全局异常处理器、异常指标监控等能力 |
| **依赖** | common-core、common-json；可选依赖 spring-webmvc、spring-webflux、spring-jdbc、spring-boot-validation、micrometer-core、spring-boot-actuator、spring-boot-health |
| **版本** | 1.0.0 |

## 核心能力

### 1. 异常层级体系

| 类 | 说明 |
|---|---|
| `AbstractYdszException` | YDSZ 异常抽象基类（错误码 + i18n 消息 + 扩展数据 + 链路追踪），懒加载消息解析（DCL 线程安全） |
| `BusinessException` | 业务异常（默认 HTTP 400 / ERROR / BUSINESS），支持链式 `data()` 附加数据 |
| `SysException` | 系统异常（默认 HTTP 500 / ERROR / SYSTEM），用于基础设施故障类异常 |
| `YdszExceptionBuilder` | 异常构建器抽象基类（CRTP 模式，类型安全的链式构建） |
| `ExceptionInfo` | 异常响应信息封装类（code / key / message / details / path / traceId / httpStatus） |

异常层级设计：

```
RuntimeException
  └─ AbstractYdszException            ← YDSZ 异常抽象基类
       ├─ BusinessException             ← 业务异常（HTTP 4xx，可预期）
       └─ SysException                  ← 系统异常（HTTP 5xx，不可预期）
```

> **设计原则**：仅保留 `BusinessException`（业务可预期）和 `SysException`（系统不可预期）两个具体异常类。安全 / 限流 / 重复提交 / 基础设施等场景通过 `ExceptionCategory` 分类标签区分，无需独立异常类。

### 2. 错误码体系

| 类 | 说明 |
|---|---|
| `ExceptionCode` | 异常码 SPI 接口（业务模块实现该接口定义自己的错误码枚举） |
| `UnifiedExceptionCode` | 统一异常码枚举（A/B/C 三类编码体系，启动时自动注册到全局注册中心） |
| `ExceptionCategory` | 异常类别枚举（BUSINESS / SYSTEM / SECURITY / RATE_LIMIT / EXTERNAL + 5 个细分场景） |
| `ExceptionLevel` | 异常级别枚举（INFO / WARN / ERROR / FATAL） |
| `ExceptionCodeRegistry` | 异常码全局注册表（线程安全，支持宽松 / 严格两种注册模式） |

错误码编码规范：`[类型(1位)] + [模块(2位)] + [序号(3位)]`

| 类型 | 含义 | HTTP 范围 | 示例 |
|---|---|---|---|
| A | 业务级错误 | 4xx | `A01052` = 参数错误 |
| B | 系统级错误 | 5xx | `B01051` = 系统内部错误 |
| C | 安全级错误 | 401/403 | `C01051` = 安全访问被拒绝 |

### 3. 错误码自动注册

| 类 | 说明 |
|---|---|
| `YdszResultCode` | 模块错误码注解（标记在枚举类上，声明 `module` 与 `description`） |
| `ResultCodeScanner` | 启动时扫描 `classpath*:com/njydsz/**/*.class` 中所有 `@YdszResultCode` 标注的枚举类，注册到双注册中心 |
| `ResultCodeRegistry` | 错误码文档注册表（按模块分组，供 Actuator 端点展示） |

扫描器在 `ApplicationReadyEvent` 时确定性扫描注册，同时注册到 `ResultCodeRegistry`（文档端点）与 `ExceptionCodeRegistry`（反查 API），确保所有错误码在应用就绪后被注册。

### 4. 全局异常处理

| 类 | 说明 |
|---|---|
| `BaseExceptionHandler` | 异常处理器抽象基类（模板方法模式，统一日志 / 指标 / 响应构建） |
| `MvcExceptionHandler` | Spring MVC 全局异常处理器（`@RestControllerAdvice`，最高优先级） |
| `WebFluxExceptionHandler` | Spring WebFlux 全局异常处理器（继承 `BaseExceptionHandler`） |
| `ValidationExceptionHandler` | Jakarta Validation 校验异常处理器（含请求路径和 traceId 提取） |
| `JdbcExceptionHandler` | Spring JDBC 数据访问异常处理器（识别唯一索引冲突 / 死锁 / 超时） |

兼容处理的框架异常：

- `MethodArgumentNotValidException` / `ConstraintViolationException` / `BindException`
- `HttpRequestMethodNotSupportedException` / `HttpMessageNotReadableException`
- `MaxUploadSizeExceededException` / `NoHandlerFoundException`
- `MissingServletRequestParameterException` / `MethodArgumentTypeMismatchException`
- `MissingRequestHeaderException`
- `DataAccessException`（Spring JDBC）
- `IllegalArgumentException` / `IllegalStateException` / `NullPointerException`
- 兜底 `Exception` 处理

traceId 提取优先级：`MDC.get("traceId")` → Request Header `X-Trace-Id` → `X-Request-Id`。

### 5. 响应格式

| 类 | 说明 |
|---|---|
| `ProblemDetail` | RFC 7807 HTTP Problem Details 标准格式（type / title / status / detail / instance / traceId / errorCode / extensions） |

支持两种响应格式通过配置开关切换：

- `base-response`（默认）：返回 `BaseResponse` 格式
- `problem-detail`：返回 RFC 7807 ProblemDetail 格式

### 6. 国际化 i18n

| 类 | 说明 |
|---|---|
| `I18nConfiguration` | 国际化核心配置，桥接 `MessageSource` 到 `AbstractYdszException`（注入消息解析器） |
| `WebI18nConfiguration` | Web 端国际化配置（`LocaleResolver` + `LocaleChangeInterceptor`），仅在 spring-webmvc 在类路径时装配 |
| `I18nProperties` | i18n 配置属性（`ydsz.i18n.*`） |

i18n 特性：

- **多环境适配**：开发环境实时加载（cacheSeconds=0），生产环境缓存（cacheSeconds=3600）
- **fail-fast 校验**：启动时校验所有已注册 `ExceptionCode` 的 i18n key 是否可解析，缺失则阻止启动
- **懒加载解析**：异常抛出时只存储 key + params，`getMessage()` 调用时才解析（DCL 双重检查锁）
- **多语言支持**：zh_CN（默认）、en_US、zh_TW
- **Hibernate Validator 集成**：校验消息国际化

### 7. 异常指标监控

| 类 | 说明 |
|---|---|
| `ExceptionMetrics` | 异常指标统计器（集成 Micrometer，按异常类型 / 级别 / 类别统计） |
| `ExceptionMetricsAutoConfiguration` | 自动配置，触发条件：`MeterRegistry` Bean 存在 |

| 指标 | 类型 | Tag 维度 | 说明 |
|---|---|---|---|
| `exception.count` | Counter | type, level, category | 异常计数（高基数 code tag 默认关闭） |
| `exception.handler.duration` | Timer | type | 异常处理耗时 |

### 8. 健康检查与文档端点

| 类 | 说明 |
|---|---|
| `ExceptionHealthIndicator` | 异常模块健康检查（暴露全局处理器状态、指标统计器、错误码注册中心信息） |
| `ExceptionCodeDocEndpoint` | 错误码文档端点（`/actuator/exception-codes`，输出全部已注册错误码及 i18n 消息） |

## 接入方式

### 1. POM 引入依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-exception</artifactId>
</dependency>
```

### 2. 配置启用

```yaml
ydsz:
  exception:
    metrics-enabled: true              # 异常指标统计开关
    global-handler-enabled: true       # 全局异常处理器开关
    response-format: base-response     # 响应格式：base-response | problem-detail
    doc-endpoint-enabled: true         # 错误码文档端点开关
  i18n:
    basename: "classpath:i18n/messages"
    encoding: "UTF-8"
    supported-locales: [zh_CN, en_US, zh_TW]
```

### 3. 抛出业务异常

```java
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.exception.code.UnifiedExceptionCode;

throw new BusinessException(UnifiedExceptionCode.NOT_FOUND)
    .data("userId", userId)
    .data("tenant", tenantId);
```

## 配置项

### ExceptionProperties（`ydsz.exception.*`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.exception.metrics-enabled` | true | 是否启用异常指标统计 |
| `ydsz.exception.global-handler-enabled` | true | 是否启用全局异常处理器 |
| `ydsz.exception.response-format` | `base-response` | 响应格式（`base-response` / `problem-detail`） |
| `ydsz.exception.include-stack-trace` | false | 是否在响应中包含堆栈信息（dev/test profile 自动开启） |
| `ydsz.exception.doc-endpoint-enabled` | true | 是否启用错误码文档端点 |
| `ydsz.exception.problem-detail-type-base-url` | `about:blank` | ProblemDetail type URI 基础 URL（RFC 7807） |
| `ydsz.exception.metrics-include-code-tag` | false | 是否在指标中包含高基数 code tag（默认关闭防止 Prometheus 指标爆炸） |

### I18nProperties（`ydsz.i18n.*`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.i18n.basename` | `classpath:i18n/messages` | 资源文件基路径（支持逗号分隔多 basename） |
| `ydsz.i18n.encoding` | `UTF-8` | 资源文件编码 |
| `ydsz.i18n.dev-cache-seconds` | 0 | 开发环境缓存刷新间隔（秒），0 表示不缓存 |
| `ydsz.i18n.prod-cache-seconds` | 3600 | 生产环境缓存刷新间隔（秒） |
| `ydsz.i18n.fallback-to-system-locale` | false | 是否回退到系统语言 |
| `ydsz.i18n.default-message` | `未找到对应的提示信息: {0}` | 找不到国际化消息时的默认提示 |
| `ydsz.i18n.supported-locales` | `[zh_CN, en_US, zh_TW]` | 支持的语言列表 |
| `ydsz.i18n.lang-param-name` | `lang` | 语言切换请求参数名 |
| `ydsz.i18n.i18n-base-names` | `i18n/messages` | 国际化资源文件基础名（备用，与 basename 合并加载） |
| `ydsz.i18n.validate-on-startup` | true | 是否启动时校验 i18n key 可解析（fail-fast） |

## 使用示例

### 1. 抛出业务异常（链式附加数据）

```java
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.exception.code.UnifiedExceptionCode;

throw new BusinessException(UnifiedExceptionCode.NOT_FOUND)
    .data("userId", userId)
    .data("tenant", tenantId);
```

### 2. 使用 Builder 模式构建异常

```java
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.exception.enums.ExceptionLevel;

throw BusinessException.builder()
    .code("CUSTOM_CODE")
    .key("custom.error")
    .httpStatus(422)
    .level(ExceptionLevel.WARN)
    .message("自定义消息")
    .cause(originalException)
    .build();
```

### 3. 抛出系统异常

```java
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.exception.code.UnifiedExceptionCode;

throw new SysException(UnifiedExceptionCode.DATABASE_ERROR, cause)
    .data("dataSource", "master");
```

### 4. 自定义模块错误码（自动注册）

```java
import com.njydsz.common.exception.enums.ExceptionCode;
import com.njydsz.common.exception.registry.YdszResultCode;

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

    @Override public String getCode() { return code; }
    @Override public String getKey() { return key; }
    @Override public int getHttpStatus() { return httpStatus; }
}
```

### 5. 切换 RFC 7807 ProblemDetail 响应格式

```yaml
ydsz:
  exception:
    response-format: problem-detail
    problem-detail-type-base-url: https://api.example.com/errors
```

返回示例：

```json
{
  "type": "https://api.example.com/errors/business",
  "title": "BusinessException",
  "status": 400,
  "detail": "用户不存在",
  "instance": "/api/v1/users/123",
  "traceId": "a1b2c3d4",
  "errorCode": "A04051",
  "timestamp": "2026-08-02T10:30:00Z",
  "extensions": {
    "userId": 123
  }
}
```

## SPI 扩展点

| SPI 接口 | 用途 | 实现方 |
|---|---|---|
| `ExceptionCode` | 异常码接口，业务模块实现该接口定义自己的错误码枚举 | `UnifiedExceptionCode`（内置）+ 业务模块自定义枚举 |
| `ExceptionCategory` | 异常分类 SPI，5 大主分类 + 5 个细分场景 | 框架内置枚举 |
| `ExceptionLevel` | 异常级别 SPI，INFO / WARN / ERROR / FATAL | 框架内置枚举 |
| `YdszResultCode` | 模块错误码注解，标记需要自动扫描注册的枚举类 | 业务模块自定义枚举 |
| `BaseExceptionHandler` | 异常处理器抽象基类，业务可继承扩展自定义异常处理逻辑 | `MvcExceptionHandler` / `WebFluxExceptionHandler` / `JdbcExceptionHandler` / `ValidationExceptionHandler` |

## 健康检查

| 端点 | 说明 | 触发条件 |
|---|---|---|
| `/actuator/health` | 异常模块健康检查（在 details 中查看 `exception` 组件状态） | `spring-boot-health` 在类路径 |
| `/actuator/exception-codes` | 错误码文档端点，输出全部已注册错误码及 i18n 消息 | `spring-boot-actuator` 在类路径 + `ydsz.exception.doc-endpoint-enabled=true` |

`ExceptionHealthIndicator` 暴露信息：

- `globalHandlerEnabled` — 全局异常处理器是否启用
- `metricsEnabled` / `metricsAvailable` — 异常指标统计器是否可用
- `responseFormat` — 当前响应格式（BASE_RESPONSE / PROBLEM_DETAIL）
- `includeStackTrace` — 是否包含堆栈信息
- `docEndpointEnabled` — 错误码文档端点是否启用
- `registeredModules` / `registeredErrorCodes` — 错误码注册中心已注册模块数和错误码总数
- `problemDetailTypeBaseUrl` — ProblemDetail type URI 基础 URL

## 注意事项

1. **仅两个具体异常类**：业务模块不应继承 `AbstractYdszException` 创建新的异常子类，应直接使用 `BusinessException` 或 `SysException`，通过 `ExceptionCategory` 区分场景。
2. **i18n fail-fast 校验**：启动时会校验所有已注册 `ExceptionCode` 的 i18n key 是否可解析，缺失会阻止应用启动。可通过 `ydsz.i18n.validate-on-startup=false` 关闭（不推荐）。
3. **高基数 code tag 治理**：`metrics-include-code-tag` 默认关闭，仅在错误码数量可控且需要按 code 维度查询时显式开启，避免 Prometheus 指标爆炸。
4. **懒加载消息解析**：异常抛出时只存储 i18n key + params，`getMessage()` 调用时才解析。消息解析器由 `I18nConfiguration` 通过 `AbstractYdszException.setMessageResolver()` 静态注入，未注入时降级返回 key。
5. **错误码注册模式**：`ExceptionCodeRegistry.register()` 宽松模式（重复注册保留首次值 + warn 日志）；`registerStrict()` 严格模式（重复注册 fail-fast 抛 `IllegalStateException`），跨模块冲突时建议使用严格模式。
6. **traceId 提取降级链**：所有异常处理器统一从 SLF4J MDC 提取 traceId，降级到 Request Header `X-Trace-Id` → `X-Request-Id`，由 common-core 的 `TraceIdGenerator` 注入 MDC。
7. **ProblemDetail type URI**：`problem-detail-type-base-url` 默认 `about:blank`，配置后会拼接 `/{category}` 作为 type URI，如 `https://api.example.com/errors/business`。
8. **AutoConfiguration 解耦**：`@AutoConfiguration` 与 `@RestControllerAdvice` 解耦，避免在 Advice 类上叠加 Spring Boot 自动配置语义，提升可测试性。

## 变更记录

- **v1.0.0**（2026-08-02）：对标 common-jdbc 标准格式重构 README，补全全部 9 个章节
