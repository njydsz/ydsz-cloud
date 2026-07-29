# ydsz-common-core

YDSZ 公共底座核心模块 — 统一响应模型、请求上下文、TraceId 生成、常量定义、枚举与错误码。

最小化依赖，**不包含** Spring AOP、Micrometer、SpEL、AspectJ 等框架能力。仅作为全项目最底层的类型定义与工具常量库被所有模块依赖。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L1 基础设施层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 被 common 所有子模块及全部业务模块依赖 |
| **构建顺序** | 最先编译（无业务依赖） |
| **源文件数** | 33 个 Java 文件（main） |
| **依赖范围** | Lombok + SLF4J + Jakarta Validation + ydsz-common-json + TTL + Spring Boot（可选） |

## 目录结构

```
com.njydsz.common.core
├── response/        # 统一响应模型（4 个类）
├── code/            # 结果码体系（2 个类）
├── context/         # 请求上下文与租户上下文（3 个类）
├── trace/           # TraceId 生成策略（3 个类）
├── enums/           # 通用枚举（5 个类）
├── constant/        # 全局常量定义（8 个类）
├── config/          # 自动配置与属性绑定（5 个类）
├── metrics/         # 轻量级指标回调 SPI（2 个类）
└── health/          # 健康检查（1 个类）
```

## 核心能力

### 1. 统一响应模型（`response` 包）

| 类 | 说明 |
|---|---|
| `IResponse<T>` | 响应标记接口，定义 `getCode()` / `getMsg()` / `getData()` / `isSuccess()` 契约 |
| `BaseResponse<T>` | 统一 API 响应体，字段：`code` / `msg` / `data` / `traceId` / `timestamp`。使用 `@SuperBuilder` + `@YdszJsonField(notWriteNullValue=true)` + `@YdszJsonPropertyOrder` 控制序列化。提供 `success()` / `error()` / `of()` 静态工厂方法，以及 `error(ResultCode, Throwable)` / `errorWithDetail(ResultCode, String)` 便捷方法。响应创建时自动通过 `CoreMetrics` SPI 上报指标 |
| `PageResponse<T>` | 分页响应体（继承 BaseResponse），字段：`total` / `pageNum` / `pageSize` / `pages`。自动计算总页数，提供 `success()` / `fail()` / `empty()` / `hasNext()` / `hasPrevious()` 方法 |
| `ProblemDetail` | RFC 7807 Problem Details 标准错误详情载体，字段：`type` / `title` / `status` / `detail` / `instance`。通过 `BaseResponse.errorWithDetail()` 使用 |

**业务响应码与 HTTP 状态码区分**：

- `BaseResponse.code` 是**业务响应码**（`String` 类型），如 `"A00000"` 表示成功
- `ResultCode.getHttpStatusCode()` 返回对应的 **HTTP 状态码**（`int` 类型），如 `200` / `400` / `500`
- 前端判断成功应检查 `resp.code === "A00000"`，而非 HTTP 状态码 `200`

**RFC 7807 Problem Details 支持**：

```java
// 返回携带标准错误详情的失败消息
return BaseResponse.errorWithDetail(BaseResultCode.VALIDATION_FAILED, "字段 'username' 不能为空");

// 返回携带标准错误详情和请求路径的失败消息
return BaseResponse.errorWithDetail(BaseResultCode.NOT_FOUND, "订单不存在", URI.create("/api/v1/orders/123"));
```

**国际化支持**：

`BaseResponse` 内置 `MessageResolver` 接口，当 Spring `MessageSource` 可用时，`CoreAutoConfiguration` 自动注册 `SpringMessageResolver` 并绑定到 `BaseResponse`，使成功/失败消息支持 i18n 国际化解析。模块自带 `i18n/messages.properties`（英文默认）和 `i18n/messages_zh_CN.properties`（中文）资源文件，覆盖全部 `BaseResultCode` 错误码。

```java
// 返回成功
return BaseResponse.success(user);

// 返回失败（使用 ResultCode 枚举）
return BaseResponse.error(BaseResultCode.VALIDATION_FAILED);

// 返回失败（从异常对象提取消息）
return BaseResponse.error(BaseResultCode.INTERNAL_ERROR, exception);

// 返回携带 RFC 7807 Problem Details 的失败消息
return BaseResponse.errorWithDetail(BaseResultCode.NOT_FOUND, "订单不存在");
```

### 2. 结果码体系（`code` 包）

| 类 | 说明 |
|---|---|
| `ResultCode` | 结果码接口，定义 `getCode()` / `getMsg()` / `getMessageKey()`（默认返回 `"error." + 枚举名`，用于 i18n）/ `getHttpStatusCode()`（默认返回 500） |
| `BaseResultCode` | 标准结果码枚举（实现 ResultCode），共 49 个错误码，按段位规划 |

**错误码段位规划**：

| 段位 | 含义 | HTTP 映射 |
|---|---|---|
| `A00000` | 成功 | 200 |
| `A1xxxx` | 通用错误（参数校验、资源不存在、限流等） | 400 / 404 / 405 / 408 / 409 / 429 |
| `A106xx` | 请求语义错误（范围无效、请求体过大等） | 400 / 429 |
| `A2xxxx` | 认证授权（未登录、Token 过期、权限不足等） | 401 / 403 / 423 |
| `B1xxxx` | 系统级业务异常（内部错误、服务不可用等） | 500 / 503 |
| `B2xxxx` | 系统状态异常（维护中、功能禁用、熔断） | 409 / 500 / 503 |
| `B3xxxx` | 用户/组织/人员 | 404 / 401 |
| `B7xxxx` | 工作流/审批 | 404 / 400 / 403 |
| `C1xxxx` | 第三方服务异常（数据库、缓存、MQ 等） | 400 / 409 / 503 / 500 |
| `C9xxxx` | 未知错误 | 500 |

> **注意**：项目/合同/商机（B4xxxx）、财务/成本（B5xxxx）、资源/工时（B6xxxx）、报表（B8xxxx）等业务域专属错误码已从 `BaseResultCode` 中删除。业务模块自定义错误码请实现 `ResultCode` 接口，在各模块内自行定义。

### 3. 请求上下文（`context` 包）

| 类 | 说明 |
|---|---|
| `RequestContext` | 基于 `TransmittableThreadLocal` 的请求上下文，支持 `userId` / `tenantId` / `traceId` / `requestId` / `language` / 自定义属性。配合 TTL 自动传播上下文 |
| `TenantContextHolder` | 租户上下文持有者接口（SPI），定义 `getTenantId()` / `isSuperTenant()`。由业务模块（如 `ydsz-common-auth`）提供具体实现，避免 core 模块循环依赖 |
| `TenantMdcFilter` | Jakarta Servlet Filter，在请求处理前将 `tenantId` / `userId` / `traceId` 从 RequestContext 写入 SLF4J MDC，请求结束后自动清理。Filter 优先级可通过 `ydsz.core.tenant-mdc-filter-order` 配置 |

### 4. TraceId 生成（`trace` 包）

| 类 | 说明 |
|---|---|
| `TraceIdSupplier` | TraceId 生成策略接口（`@FunctionalInterface`），SPI 扩展点 |
| `TraceIdGenerator` | TraceId 生成器统一入口（静态工具类），内部委托到 `TraceIdSupplier`。使用 `volatile` 持有当前策略，通过 `setSupplier()` 注入。生成 TraceId 后自动通过 `CoreMetrics` SPI 上报指标 |
| `SnowflakeTraceIdSupplier` | 基于 Snowflake 算法的有序 TraceId 生成器。生成 16 位十六进制字符串。使用 `AtomicLong` + CAS 自旋（无锁），支持时钟回拨检测与等待。workerId/datacenterId 支持 K8s 环境变量推导 |

### 5. 轻量级指标回调（`metrics` 包）

| 类 | 说明 |
|---|---|
| `CoreMetricsCallback` | 指标回调 SPI 接口，定义 `onTraceIdGenerated(strategy)` / `onResponseCreated(success, code)` 方法。上层模块（如 `ydsz-common-base`）可实现此接口桥接到 Micrometer / Prometheus。未注册时使用 NOOP 空操作，零性能开销 |
| `CoreMetrics` | 指标采集器统一入口，使用 `volatile` 静态 holder 管理 `CoreMetricsCallback` 实例。提供 `recordTraceIdGenerated()` / `recordResponseCreated()` 方法，由 `TraceIdGenerator` 和 `BaseResponse` 内部调用 |

**设计理由**：core 模块定位为最小核心，不含 Micrometer 依赖。通过 SPI 回调解耦，上层模块可按需桥接到监控系统，未注册时零开销。

```java
// 上层模块实现示例（ydsz-common-base）
@Bean
public CoreMetricsCallback coreMetricsCallback(MeterRegistry registry) {
    return new CoreMetricsCallback() {
        @Override
        public void onTraceIdGenerated(String strategy) {
            registry.counter("ydsz.core.traceid.generated", "strategy", strategy).increment();
        }
        @Override
        public void onResponseCreated(boolean success, String code) {
            registry.counter("ydsz.core.response.created",
                    "success", String.valueOf(success), "code", code).increment();
        }
    };
}
```

### 6. 通用枚举（`enums` 包）

| 枚举 | 类型参数 | 说明 |
|---|---|---|
| `TypeEnum<T>` | 接口 | 通用枚举接口，定义 `getCode()` / `getDesc()` + 静态工具方法 `buildCodeMap()` / `codeOf()` |
| `DataScopeType` | `TypeEnum<String>` | 数据权限范围类型（TENANT / GROUP / COMPANY / DEPT / USER / PROJECT / REGION / CUSTOM），含 `priority` 优先级字段和 `max()` 比较方法 |
| `IdentityType` | `TypeEnum<String>` | 身份类型（YDSZ / COMPANY / VISITOR） |
| `ServiceType` | `TypeEnum<String>` | 服务类型（WEB_SERVICE / APP_SERVICE），含 `pathPrefix` 路径前缀字段 |
### 7. 全局常量（`constant` 包）

| 常量类 | 说明 |
|---|---|
| `HeaderConstants` | HTTP 请求头常量（认证 Token / 数据权限维度 / 列级权限 / 链路追踪 / 网络信息），共 20 个自定义头常量 |
| `TokenConstants` | Token 相关常量（标识 / 前缀 "ydsz" / 回调 URL 参数名） |
| `SecurityConstants` | 安全常量（密钥属性名 / BCrypt 强度 12 / CSRF 头部与参数名 / 安全头部引用） |
| `PageConstants` | 分页默认值与上限（`DEFAULT_PAGE_NUM=1` / `DEFAULT_PAGE_SIZE=20` / `MAX_PAGE_SIZE=5000`）。运行时值由 `CoreProperties` 配置覆盖 |
| `FilterIgnoreConstant` | 过滤器忽略 URL 模式与认证忽略服务名称 |
| `TraceConstants` | 链路追踪常量（`TRACE_ID_HEADER="X-Trace-Id"` / `MDC_TRACE_ID_KEY="traceId"`） |
| `CacheConstants` | 缓存名称常量（workflow / nextwiki / system 三个模块的 8 个 cache name） |
| `SystemConstants` | 系统级常量（`SYSTEM_USER_ID="SYSTEM"` / `DEFAULT_TENANT_ID="1"` / `DEFAULT_LOCALE="zh-CN"`） |
| `YdszMessageTopics` | 消息中心 RocketMQ Topic / ConsumerGroup 常量 |

### 8. 自动配置（`config` 包）

| 配置类 | 激活条件 | 注册的 Bean |
|---|---|---|
| `CoreAutoConfiguration` | `ydsz.core.enabled=true`（默认启用） | `SpringMessageResolver`（i18n 消息解析器）、`TenantMdcFilter`（FilterRegistrationBean）、`CoreHealthIndicator`（Actuator 健康指标）、`PageConstantsInitializer`（分页配置运行时同步）、`coreMetricsRegistrar`（CoreMetricsCallback 自动注册） |
| `TraceAutoConfiguration` | `ydsz.core.trace.enabled=true`（默认启用） | `TraceIdSupplier`（UUID 或 Snowflake） |

| 属性类 | 前缀 | 说明 |
|---|---|---|
| `CoreProperties` | `ydsz.core` | 分页配置（`maxPageSize` / `defaultPageSize`，带 `@Min` / `@Max` 校验）+ 链路追踪配置（`TraceConfig` 内部类，`idType` 带 `@NotBlank` + `@Pattern` 校验）+ 租户 MDC 过滤器优先级 |
| `FilterIgnoreProperties` | `ydsz.core.filter-ignore` | 过滤器忽略路径配置，支持配置覆盖与默认值合并 |

### 9. 健康检查（`health` 包）

| 类 | 说明 |
|---|---|
| `CoreHealthIndicator` | Spring Boot Actuator 健康指标，访问 `/actuator/health` 时报告：TraceId 策略名称、Trace 是否启用、TraceId 类型、配置校验结果、TraceId 生成探针（pass/fail）、Snowflake workerId（当策略为 Snowflake 时）、分页配置及合法性校验、i18n 解析器状态、指标回调注册状态、过滤器忽略路径配置摘要 |

**健康状态规则**：
- TraceId 生成探针失败 → DOWN
- 配置项非法（如 `id-type` 不在 `uuid`/`snowflake` 范围内） → DOWN
- 所有检查通过 → UP

## 配置项

```yaml
ydsz:
  core:
    enabled: true                          # 模块总开关（默认启用）
    max-page-size: 1000                    # 最大每页记录数上限（1-5000），运行时生效
    default-page-size: 20                  # 默认每页记录数（1-5000），运行时生效
    tenant-mdc-filter-order: -2147483647   # 租户 MDC 过滤器优先级（默认 HIGHEST_PRECEDENCE + 100）
    trace:
      enabled: true                        # 链路追踪开关（默认启用）
      generate-if-missing: true            # 请求头缺失 TraceId 时自动生成
      id-type: uuid                        # uuid（默认，32位无序）或 snowflake（16位有序）
    tenant-mdc-filter:
      enabled: true                        # 租户 MDC 过滤器开关（默认启用）
    filter-ignore:
      common-ignore-urls:                  # 公共忽略 URL 模式（与内置默认值合并）
        - /custom/path/**
      auth-filter-ignore-service-names:    # 认证忽略服务名（覆盖内置默认值）
        - ydsz-custom-web
      security-exclude-urls:               # 安全排除 URL 模式（与内置默认值合并）
        - /custom/auth/**
```

所有配置项已通过 `additional-spring-configuration-metadata.json` 注册，IDE 自动补全支持，包含 `groups` 分组和详细描述。

## 国际化资源

模块自带 i18n 资源文件，覆盖全部 `BaseResultCode` 错误码：

| 文件 | 语言 | 说明 |
|---|---|---|
| `i18n/messages.properties` | English（默认） | 全部 49 个错误码的英文消息 |
| `i18n/messages_zh_CN.properties` | 简体中文 | 全部 49 个错误码的中文消息 |

消息 key 格式：`error.{ENUM_NAME}`（如 `error.VALIDATION_FAILED` = `参数校验失败`），与 `ResultCode.getMessageKey()` 默认实现一致。

## 依赖

### Maven 依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-core</artifactId>
</dependency>
```

### 编译期依赖

| 依赖 | Scope | 用途 |
|---|---|---|
| `lombok` | provided | `@Data` / `@SuperBuilder` / `@Getter` 等注解 |
| `slf4j-api` | compile | MDC 日志上下文 |
| `jakarta.validation-api` | compile | `@Min` / `@Max` / `@NotBlank` / `@Pattern` 校验注解 |
| `jakarta.annotation-api` | compile | Jakarta 标准注解 |
| `ydsz-common-json` | compile | `@YdszJsonField` / `@YdszJsonPropertyOrder` 序列化注解 |
| `transmittable-thread-local` | compile | TTL 线程池上下文传递 |
| `jakarta.servlet-api` | provided | `TenantMdcFilter` 实现 `jakarta.servlet.Filter` |
| `spring-boot` | compile | `@AutoConfiguration` / `@ConfigurationProperties` |
| `spring-boot-autoconfigure` | compile | 自动配置基础类 |
| `spring-boot-configuration-processor` | optional | 配置元数据生成 |
| `spring-boot-health` | optional | `HealthIndicator` 接口（`CoreHealthIndicator` 依赖） |

### 零依赖原则

本模块**不含**以下依赖：
- Spring AOP / AspectJ（无切面）
- Micrometer（无指标采集，通过 `CoreMetricsCallback` SPI 解耦）
- SpEL（无表达式求值）
- Spring Web MVC（无 Controller / RestController）
- MyBatis-Plus（无数据访问层）
- Redis（无缓存操作）

## AutoConfiguration 注册

`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`：

```
com.njydsz.common.core.config.CoreAutoConfiguration
com.njydsz.common.core.config.TraceAutoConfiguration
```

## GraalVM Native Image 支持

`META-INF/native-image/com.njydsz/ydsz-common-core/native-image.properties` 注册了以下类的反射配置：
- `BaseResponse` — 无参构造器 + `(String, String, Object)` 构造器 + 5 个字段
- `PageResponse` — 无参构造器 + 7 参数构造器 + 4 个字段
- `ProblemDetail` — 无参构造器 + 5 个字段
- `CoreProperties` / `CoreProperties$TraceConfig` / `FilterIgnoreProperties` — 无参构造器

## 设计决策

### 静态 holder 模式（TraceIdGenerator / BaseResponse / CoreMetrics）

`TraceIdGenerator`、`BaseResponse` 和 `CoreMetrics` 使用 `volatile` 静态字段持有策略实例，而非 Spring Bean 依赖注入。这是因为这些组件在项目极早期（如 Filter 初始化、日志框架启动）即被调用，此时 Spring 容器可能尚未就绪。静态 holder 保证零依赖、即时可用。

### CoreMetricsCallback SPI 模式

`CoreMetricsCallback` 遵循与 `BaseResponse.MessageResolver` 相同的 SPI 模式：core 模块定义接口和静态 holder，上层模块通过 `@Bean` 注册实现，`CoreAutoConfiguration` 通过 `ObjectProvider` 自动发现并注入。未注册时使用 NOOP 空操作，零性能开销。

### 配置校验 fail-fast

`CoreProperties` 及其 `TraceConfig` 内部类使用 JSR-303 校验注解（`@Min` / `@Max` / `@NotBlank` / `@Pattern`），配合 `@Validated` 和 `@Valid` 实现启动时校验。配置非法时应用启动失败，避免运行时出现意外行为。
