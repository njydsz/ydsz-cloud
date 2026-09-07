# ydsz-common-exception

> 统一异常体系与错误码注册（L3 基础服务层）

提供统一异常基类（`AbstractYdszException` / `BusinessException` / `SysException`）、自动错误码扫描注册（`@YdszExceptionCode`）、多维度异常处理器（MVC + WebFlux + JDBC + Validation）、i18n 国际化、异常指标监控、OpenAPI 文档集成、异常脱敏等能力，是所有业务模块异常处理的统一基座。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L3 基础服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 提供统一异常体系、错误码注册、多维度异常处理、i18n、可观测性 |
| **依赖** | ydsz-common-core、spring-webmvc（optional）、spring-web/webflux（optional）、jakarta.servlet-api、spring-boot-autoconfigure、spring-boot-starter-validation、spring-jdbc、spring-orm、io.micrometer、spring-boot-actuator、spring-boot-health、springdoc-openapi、lombok、slf4j |
| **版本** | 2.1.0 |

## 核心能力

### 1. 异常基类体系

| 类 | 说明 |
|---|---|
| `AbstractYdszException` | 抽象异常基类（errorCode / message / level / category / cause），所有异常的根类 |
| `BusinessException` | 业务异常（客户端请求错误，HTTP 4xx / 自定义 code） |
| `SysException` | 系统异常（服务端内部错误，HTTP 5xx） |
| `BatchBusinessException` | 批量业务异常（收集多个业务错误） |
| `YdszExceptionBuilder` | 异常构建器（流畅 API，如 `BusinessException.code(...).args(...).build()`） |

### 2. 错误码扫描注册

| 类 / 注解 | 说明 |
|---|---|
| `@YdszExceptionCode` | 错误码声明注解（标注在 `IExceptionResultCode` 枚举上） |
| `ExceptionCodeScanner` | 类路径扫描器（启动期扫描所有 `@YdszExceptionCode` 注解的枚举类，注册到 ErrorCodeTable） |
| `ErrorCodeTable` | 错误码注册表（静态 Map，枚举 class → code → message） |
| `IExceptionResultCode` | 错误码接口（枚举实现此接口定义业务错误码） |
| `ExceptionCode` | 错误码枚举基类（预定义核心错误码） |

**自定义错误码**：业务模块定义枚举实现 `IExceptionResultCode` 并标注 `@YdszExceptionCode`，启动期自动注册。

```java
@YdszExceptionCode
public enum UserExceptionCode implements IExceptionResultCode {
    USER_NOT_FOUND(10001, "用户不存在"),
    USER_DISABLED(10002, "用户已禁用");
}
```

### 3. 异常分类与级别

| 类 | 说明 |
|---|---|
| `ExceptionCategory` | 异常分类枚举（BUSINESS / SYSTEM / SECURITY / VALIDATION / RATE_LIMIT 等） |
| `ExceptionLevel` | 异常级别（INFO / WARN / ERROR / CRITICAL） |
| `CoreExceptionCode` | 核心模块错误码定义 |
| `SecurityExceptionCode` | 安全模块错误码定义 |
| `RateLimitExceptionCode` | 限流模块错误码定义 |

### 4. 多维度异常处理器

| 类 | 说明 |
|---|---|
| `BaseExceptionHandler` | MVC 统一异常处理入口（`@RestControllerAdvice`，处理所有 AbstractYdszException 并返回 YdszResponse） |
| `MvcExceptionHandler` | Spring MVC 专用异常处理 |
| `WebFluxExceptionHandler` | Spring WebFlux 专用异常处理 |
| `JdbcExceptionHandler` | JDBC 异常处理（SQL 语法错误 / 约束违反等） |
| `ValidationExceptionHandler` | Bean Validation 异常处理（`@Valid` 失败） |

**处理链**：异常 → 识别处理器 → 翻译 messageKey（i18n） → 包装 YdszResponse → 记录 metrics。

### 5. 国际化

| 类 | 说明 |
|---|---|
| `MessageSourceHolder` | MessageSource 持有器（Spring 容器启动后初始化） |
| `MessageSourceAccessor` | MessageSource 简化访问工具 |
| `I18nProperties` | i18n 配置属性（`ydsz.exception.i18n.*`） |

**错误码 message 模板**：
- 枚举内直接写中文（默认）
- 通过 `messages.properties` 覆盖（key = `{ExceptionCodeEnum.fullyQualifiedName}.{code}`）

### 6. 异常上下文与事件

| 类 | 说明 |
|---|---|
| `ExceptionContext` | 异常处理上下文（HttpServletRequest / HandlerMethod） |
| `ExceptionInfo` | 异常信息封装 |
| `ExceptionHandledEvent` | 异常处理事件（发布 Spring ApplicationEvent，供审计 / 监控消费） |

### 7. 异常脱敏

| 类 | 说明 |
|---|---|
| `ExceptionDesensitizer` | 异常脱敏器（移除敏感信息，避免 Response 泄露密码 / Token 等） |

### 8. 链路追踪集成

| 类 | 说明 |
|---|---|
| `OtelTraceInfo` / `OtelTraceInfoExtractor` | OpenTelemetry 链路信息提取（将 traceId / spanId 写入异常响应） |

### 9. 可观测性

| 类 | 说明 |
|---|---|
| `ExceptionMetrics` | 异常计数 Micrometer Gauge（按级别 / 分类聚合） |
| `ExceptionCodeDocEndpoint` | 异常码文档 Actuator 端点（`/actuator/exceptionCodes`） |
| `ExceptionHealthIndicator` | 异常健康检查 Bean |

### 10. OpenAPI 文档集成

| 类 | 说明 |
|---|---|
| `ExceptionCodeOpenApiCustomizer` | OpenAPI 错误码文档自定义器 |
| `YdszExceptionOpenApiAutoConfiguration` | OpenAPI 自动配置 |

## 接入方式

### 1. POM 引入依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-exception</artifactId>
</dependency>
```

### 2. 定义业务错误码

```java
import com.njydsz.common.exception.code.YdszExceptionCode;
import com.njydsz.common.exception.code.IExceptionResultCode;

@YdszExceptionCode
public enum OrderExceptionCode implements IExceptionResultCode {
    ORDER_NOT_FOUND(20001, "订单不存在：{0}"),
    ORDER_PAID(20002, "订单已支付，无法取消"),
    ORDER_EXPIRED(20003, "订单已过期");

    private final int code;
    private final String messageTemplate;

    OrderExceptionCode(int code, String messageTemplate) {
        this.code = code;
        this.messageTemplate = messageTemplate;
    }

    @Override
    public int getCode() { return code; }

    @Override
    public String getMessageTemplate() { return messageTemplate; }
}
```

### 3. 抛出异常

```java
// 使用枚举
throw BusinessException.from(OrderExceptionCode.ORDER_NOT_FOUND, orderId);

// 使用构建器
throw YdszExceptionBuilder.business()
    .code(20004)
    .message("订单状态非法：{0}", status)
    .args(status)
    .level(ExceptionLevel.WARN)
    .build();
```

### 4. 配置 i18n

```yaml
ydsz:
  exception:
    i18n:
      enabled: true
      basename: messages_exception
      fallback-to-system-locale: false
      default-locale: zh_CN
```

### 5. Actuator 错误码文档

```yaml
management:
  endpoints:
    web:
      exposure:
        include: exceptionCodes
  endpoint:
    exceptionCodes:
      enabled: true
```

访问 `/actuator/exceptionCodes` 返回 JSON 格式错误码表。

## SPI 扩展点

| SPI 接口 | 用途 | 注册方式 |
|---|---|---|
| `IExceptionResultCode` **SPI** | 业务错误码枚举接口 | `@YdszExceptionCode` + 枚举实现 |
| `ExceptionAlertListener** SPI** | 异常告警监听器（自定义发送企微 / 邮件告警） | `@Component` |

## 自动配置类

| 类 | 触发条件 |
|---|---|
| `YdszExceptionCoreAutoConfiguration` (META-INF.imports) | `ydsz-common-exception` 在 classpath |
| `YdszExceptionHandlerAutoConfiguration` | Web / WebFlux / Validation / JDBC 处理器加载 |
| `YdszExceptionActuatorAutoConfiguration` | Actuator 存在 |
| `YdszExceptionOpenApiAutoConfiguration` | Springdoc 存在 |

## 注意事项

1. **启动期扫描**：错误码扫描器在 Spring 容器初始化阶段执行，如依赖外部数据源请改为懒加载模式。
2. **错误码唯一性**：不同业务模块建议使用前缀（如 `10xxx` 用户 / `20xxx` 订单）。
3. **message 模板**：支持 `{0}` / `{1}` 占位符（由 `MessageSource` 解析时填充）。
4. **脱敏默开启**：生产环境务必开启脱敏，避免堆栈信息写入响应 Body。
5. **JDBC 异常**：`JdbcExceptionHandler` 将 `DuplicateKeyException` / `DataIntegrityViolationException` 等映射为友好错误码。

## 变更记录

- **2.1.0**（2026-09-04）：新增 BatchBusinessException 批量异常码收集；新增 ExceptionCodeDocEndpoint Actuator 端点；优化 OpenAPI 错误码文档输出。
- **2.0.0**（2026-09-01）：异常体系重构（BusinessException / SysException / AbstractYdszException）；多维度异常处理器（MVC + WebFlux + Validation + JDBC）；新增异常脱敏（ExceptionDesensitizer）；新增 OpenTelemetry 链路集成（OtelTraceInfo）。
- **1.0.0**（2026-08-02）：初始版本。
