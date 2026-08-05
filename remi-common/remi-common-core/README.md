# remi-common-core

> REMI 公共底座核心模块（L1 基础设施层）— 统一响应模型、结果码、请求上下文、TraceId、常量定义

最小化依赖的核心库，**不包含** Spring AOP、Micrometer、SpEL、AspectJ、Spring Web MVC、MyBatis-Plus、Redis 等框架能力。仅作为全项目最底层的类型定义与工具常量库被所有模块依赖。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L1 基础设施层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 提供统一响应模型、结果码体系、请求上下文、TraceId 生成、全局常量等基础能力 |
| **依赖** | Lombok、SLF4J、Jakarta Validation、Jakarta Annotation、remi-common-json、TransmittableThreadLocal、spring-boot、spring-boot-autoconfigure |
| **版本** | 1.0.0 |

## 核心能力

### 1. 统一响应模型（response 包）

| 类 | 说明 |
|---|---|
| `IResponse<T>` | 响应标记接口，定义 `getCode()` / `getMsg()` / `getData()` / `isSuccess()` 契约 |
| `BaseResponse<T>` | 统一 API 响应体，字段：`code` / `msg` / `data` / `traceId` / `timestamp`。提供 `success()` / `error()` / `of()` / `errorWithDetail()` 静态工厂方法，消息支持 i18n 国际化解析 |
| `PageResponse<T>` | 分页响应体（继承 BaseResponse），字段：`total` / `pageNum` / `pageSize` / `pages`。自动计算总页数，提供 `success()` / `fail()` / `empty()` / `hasNext()` / `hasPrevious()` 方法 |
| `ProblemDetail` | RFC 7807 Problem Details 标准错误详情载体，字段：`type` / `title` / `status` / `detail` / `instance`。通过 `BaseResponse.errorWithDetail()` 使用 |

### 2. 结果码体系（code 包）

| 类 | 说明 |
|---|---|
| `ResultCode` | 结果码接口，定义 `getCode()` / `getMsg()` / `getMessageKey()`（默认返回 `"error." + 枚举名`，用于 i18n）/ `getHttpStatusCode()` |
| `BaseResultCode` | 标准结果码枚举（实现 ResultCode），共 56 个错误码，按段位规划，每个枚举显式声明 HTTP 状态码 |
| `IExceptionResultCode` | 异常模块桥接契约接口（1.7.0 新增）。异常实现 `resultCode()` 方法后即可通过 `BaseResponse.error(Throwable)` 提取错误码，无需反射探测。用于 core 与 exception 模块之间的解耦桥接 |

**错误码段位规划**：

| 段位 | 含义 |
|---|---|
| `A00000` | 成功 |
| `A1xxxx` | 通用错误（参数校验、资源不存在、限流等） |
| `A2xxxx` | 认证授权 |
| `B1xxxx` | 系统级业务异常 |
| `B2xxxx` | 系统状态异常（维护中、功能禁用、熔断） |
| `B3xxxx` | 用户/组织/人员 |
| `B7xxxx` | 工作流/审批 |
| `C1xxxx` | 第三方服务异常（数据库、缓存、MQ 等） |
| `C9xxxx` | 未知错误 |

> 业务模块自定义错误码请实现 `ResultCode` 接口，在各模块内自行定义，不应放入 `BaseResultCode`。

### 3. 请求上下文（context 包）

| 类 | 说明 |
|---|---|
| `RequestContext` | 基于 `TransmittableThreadLocal` 的请求上下文，支持 `userId` / `tenantId` / `traceId` / `requestId` / `language` / `tenantIsolationSkipped` / 自定义属性。提供 `runAndClear()` / `newCleanupGuard()` 等自动清理模式 |
| `ContextKeys` | 预定义上下文键常量库（1.7.0 新增）。`USER_ID` / `TENANT_ID` / `TRACE_ID` / `REQUEST_ID` / `LANGUAGE` / `TENANT_ISOLATION_SKIPPED` 六个强类型常量，避免业务代码裸字符串 |
| `ProblemDetail` | RFC 7807 标准错误详情载体（详见 response 包） |

### 4. TraceId 生成与传播（trace 包）

| 类 | 说明 |
|---|---|
| `TraceIdGenerator` | TraceId 生成器（UUID），保证全局唯一，满足请求追踪需求 |
| `TraceIdPropagation` | TraceId 传播工具类（纯 JDK，无框架依赖）：从 MDC 读取 traceId 并生成 `X-Trace-Id` 请求头，供 RestTemplate / WebClient / OkHttp 等 HTTP 客户端拦截器复用，实现服务间调用链路贯穿。提供 `traceHeader()` / `traceHeaderOrCreate()` / `currentTraceId()` 等方法 |

### 5. 全局常量（constant 包）

| 常量类 | 说明 |
|---|---|
| `HeaderConstants` | 全项目共享 HTTP 请求头常量契约（认证 Token / 数据权限维度 / 链路追踪 `X-Trace-Id` / 网络信息）。供 common-auth / common-base / common-web / gateway 共同引用 |
| `PageConstants` | 分页默认值与上限（`DEFAULT_PAGE_NUM=1` / `DEFAULT_PAGE_SIZE=20` / `MAX_PAGE_SIZE=5000`），运行时值由 `CoreProperties` 配置覆盖，提供 `normalizePageSize()` / `normalizePageNum()` / `calcOffset()` 归一化工具 |
| `SystemConstants` | 系统级常量（`SYSTEM_USER_ID="SYSTEM"` / `DEFAULT_TENANT_ID="0"` / `DEFAULT_LOCALE="zh-CN"`） |

### 6. 自动配置（config 包）

| 配置类 | 激活条件 | 注册的 Bean |
|---|---|---|
| `CoreAutoConfiguration` | `remi.core.enabled=true`（默认启用） | `SpringMessageResolver`（i18n 消息解析器）、`PageConstantsInitializer`（分页配置运行时同步）、`CoreHealthIndicator`（健康检查，需 Actuator）、`CoreMetrics`（1.7.0 新增，Micrometer 响应指标，需 MeterRegistry Bean） |

| 属性类 | 前缀 | 说明 |
|---|---|---|
| `CoreProperties` | `remi.core` | 分页配置（`maxPageSize` / `defaultPageSize`，带 `@Min` / `@Max` 校验）、租户 MDC 过滤器优先级 |
| `FilterIgnoreProperties` | `remi.core.filter-ignore`（1.7.0 新增） | 网关过滤器放行路径配置：`overrideMode`（false=合并、true=覆盖内置名单，默认 false）、`authFilterIgnoreServiceNames`（待放行服务名列表） |

| 指标运营包 | 说明 |
|---|---|
| `CoreMetrics` | Micrometer 指标注册器（1.7.0 新增）。提供 `incrementResponse(resultCodePrefix)` 记录响应码计数（含 `result_code_prefix` + `success` tags）、`recordHoldTime(Duration)` 记录请求上下文驻留时长；Micrometer no-bea 时自动 no-op |
| `CoreHealthIndicator` | Spring Boot 4.1.0 `org.springframework.boot.health.contributor.HealthIndicator` 迁移后的健康指示器；`PageConstants.isInitialized()` 未初始化返回 UNKNOWN，避免误报 DOWN |

## 接入方式

### 1. POM 引入依赖

```xml
<dependency>
    <groupId>com.remisoft</groupId>
    <artifactId>remi-common-core</artifactId>
</dependency>
```

### 2. 配置启用

```yaml
remi:
  core:
    enabled: true                          # 模块总开关（默认启用）
    max-page-size: 1000                    # 最大每页记录数上限（1-5000）
    default-page-size: 20                  # 默认每页记录数（1-5000）
```

### 3. 基础使用

```java
import com.remisoft.common.core.response.BaseResponse;
import com.remisoft.common.core.code.BaseResultCode;

// 返回成功
return BaseResponse.success(user);

// 返回失败（使用 ResultCode 枚举）
return BaseResponse.error(BaseResultCode.VALIDATION_FAILED);

// 返回携带 RFC 7807 Problem Details 的失败消息
return BaseResponse.errorWithDetail(BaseResultCode.NOT_FOUND, "订单不存在");
```

## 配置项

| 配置 | 默认值 | 说明 |
|---|---|---|
| `remi.core.enabled` | true | 模块总开关，关闭后 CoreAutoConfiguration 不生效 |
| `remi.core.max-page-size` | 1000 | 最大每页记录数上限（1-5000），运行时同步到 `PageConstants` |
| `remi.core.default-page-size` | 20 | 默认每页记录数（1-5000），运行时同步到 `PageConstants` |

> 所有配置项已通过 `additional-spring-configuration-metadata.json` 注册，IDE 自动补全支持，包含 `groups` 分组和详细描述。

## 使用示例

### 1.7.0 新增特性概览

1. **异常→响应桥接**：新增 `IExceptionResultCode` 契约接口，异常实现 `resultCode()` 后通过 `BaseResponse.error(ex)` 自动提取错误码。旧的反射探测移除，性能从 O(classDepth) 降至 O(1) 且消除 `setAccessible(true)` 安全风险。
2. **链路追踪贯通**：所有错误响应构建路径（`errorWithDetail`、`error(Throwable, URI)`）自动从 MDC 注入当前 `traceId`，关联日志与响应。
3. **ProblemDetail 序列化瘦身**：`ProblemDetail` 类级 `@JsonInclude(NON_NULL)` 抑制 null 字段输出，约减响应体积。
4. **分页配置健康检查**：`CoreHealthIndicator` 增加 `PageConstants.isInitialized()` 回退检测，未注入运行时值返回 UNKNOWN 而非误判 DOWN。
5. **过滤器自定义放行**：新增 `FilterIgnoreProperties` 配置类（含 `overrideMode` 开关），让网关可追加或完整替换过滤器放行服务名单。
6. **Micrometer 响应指标**：新增 `CoreMetrics` 静态门面，业务过滤器可无门槛上报计数 / 驻留时长。
7. **强类型上下文键**：新增 `ContextKeys` 预定义常量，消除 `RequestContext.get("userId")` 类散串字符串。

### 1. 统一响应返回

```java
import com.remisoft.common.core.response.BaseResponse;
import com.remisoft.common.core.response.PageResponse;
import com.remisoft.common.core.code.BaseResultCode;

// 成功响应（带数据）
return BaseResponse.success(userVO);

// 失败响应（从异常提取消息）
return BaseResponse.error(BaseResultCode.INTERNAL_ERROR, exception);

// 携带 RFC 7807 Problem Details 的失败响应（含请求路径）
return BaseResponse.errorWithDetail(
        BaseResultCode.NOT_FOUND,
        "订单不存在",
        URI.create("/api/v1/orders/123"));
```

### 2. 分页响应返回

```java
import com.remisoft.common.core.response.PageResponse;

PageResponse<List<UserVO>> page(PageQuery query) {
    long total = userMapper.selectCount(query);
    List<UserVO> items = userMapper.selectPage(query);
    return PageResponse.success(total, query.getPageNum(), query.getPageSize(), items);
}
```

### 3. 业务模块自定义错误码

```java
import com.remisoft.common.core.code.ResultCode;

public enum OrderResultCode implements ResultCode {
    ORDER_NOT_FOUND("B02001", "订单不存在", 404),
    ORDER_CANCELLED("B02002", "订单已取消", 400);

    private final String code;
    private final String msg;
    private final int httpStatus;

    OrderResultCode(String code, String msg, int httpStatus) {
        this.code = code;
        this.msg = msg;
        this.httpStatus = httpStatus;
    }

    @Override public String getCode() { return code; }
    @Override public String getMsg() { return msg; }
    @Override public int getHttpStatusCode() { return httpStatus; }
}

// 使用
return BaseResponse.error(OrderResultCode.ORDER_NOT_FOUND);
```

### 4. 请求上下文传递

```java
import com.remisoft.common.core.context.RequestContext;

// 在网关或拦截器中设置上下文
RequestContext.setUserId("user123");
RequestContext.setTenantId("tenant456");
RequestContext.setTraceId(TraceIdGenerator.generate());

// try-with-resources 自动清理
try (RequestContext.CleanupGuard guard = RequestContext.newCleanupGuard()) {
    RequestContext.setUserId("user123");
    // 业务逻辑
} // 自动清理上下文，防止内存泄漏
```

### 5. TraceId 使用

```java
import com.remisoft.common.core.trace.TraceIdGenerator;

// 生成 32 位唯一 TraceId
String traceId = TraceIdGenerator.generate();
```

## SPI 扩展点

| SPI 接口 | 用途 | 实现方 |
|---|---|---|
| `BaseResponse.MessageResolver` | 国际化消息解析 SPI，将响应消息委托到 Spring MessageSource 等实现 | 框架内置 `SpringMessageResolver`（自动注册），业务可覆盖 |

## 注意事项

1. **业务响应码与 HTTP 状态码区分**：`BaseResponse.code` 是业务响应码（`String` 类型，如 `"A00000"`），`ResultCode.getHttpStatusCode()` 返回对应的 HTTP 状态码（`int` 类型）。前端判断成功应检查 `resp.code === "A00000"`，而非 HTTP 状态码 `200`。
2. **RequestContext 必须显式清理**：基于 TransmittableThreadLocal 的上下文必须在请求结束时调用 `RequestContext.clear()`，建议使用 `try-with-resources` 配合 `RequestContext.newCleanupGuard()`，避免线程池复用导致内存泄漏或上下文串扰。
3. **业务模块自定义错误码请实现 `ResultCode` 接口**：需显式实现 `getCode()` / `getMsg()` / `getHttpStatusCode()` 三个方法，不应放入 `BaseResultCode`。
4. **配置校验 fail-fast**：`CoreProperties` 使用 JSR-303 校验注解（`@Min` / `@Max`），配合 `@Validated` 实现启动时校验。配置非法时应用启动失败。
5. **零依赖原则**：本模块不含 Spring AOP / AspectJ / Micrometer / SpEL / Spring Web MVC / MyBatis-Plus / Redis 依赖。多租户、脱敏、健康检查等上层能力由 `remi-common-tenant`、`remi-common-safe`、`remi-common-base` 等模块提供。
6. **国际化资源**：模块自带 `i18n/messages.properties`（英文默认）和 `i18n/messages_zh_CN.properties`（中文）资源文件，覆盖全部 56 个 `BaseResultCode` 错误码。消息 key 格式为 `error.{ENUM_NAME}`，与 `ResultCode.getMessageKey()` 默认实现一致。

## 相关模块

| 能力 | 所在模块 |
|---|---|
| 多租户隔离（TenantContextHolder / TenantContextWebFilter / TenantMdcFilter） | `remi-common-tenant` |
| Web 层过滤器（TenantMdcFilter / TraceFilter / RequestContextCleanupFilter） | `remi-common-base` / `remi-common-web` |
| 敏感数据脱敏（SensitiveData / SensitiveUtil / SensitiveDataAdvice） | `remi-common-safe` |
| 健康检查（CoreHealthIndicator / WebHealthIndicator） | `remi-common-base` / `remi-common-web` |
| 过滤器忽略配置（FilterIgnoreConstant / FilterIgnoreProperties） | `remi-common-web` |

## 变更记录

- **v1.7.0**（2026-08-05）：对标 5 大竞品规范落地质量 + 性能 + 体验的系统性优化
  - **BREAKING**（接 v1.7.0 2026-08-04）：移除已废弃的 `setResolver()` 和带 `Class<T>` 参数的 `errorWithDetail()` 重载版本
  - 新增 `IExceptionResultCode` 契约接口，桥接异常 → 响应码（消除反射探测，性能提升 + 安全性提升）
  - 新增 `ContextKeys` 强类型上下文常量库（`USER_ID` / `TENANT_ID` / `TRACE_ID` / `REQUEST_ID` / `LANGUAGE` / `TENANT_ISOLATION_SKIPPED`）
  - 新增 `FilterIgnoreProperties` 配置类（`overrideMode` + `authFilterIgnoreServiceNames`），由 `CoreAutoConfiguration` 注册 Bean
  - 新增 `CoreMetrics` Micrometer 指标门面（`incrementResponse` / `recordHoldTime`），无依赖时自动 no-op
  - `CoreHealthIndicator` 增加 `PageConstants.isInitialized()` 回退检测（返回 `UNKNOWN` 非 `DOWN`）
  - `BaseResponse` 所有错误响应构建路径自动注入 MDC traceId（链路追踪贯通）
  - `ProblemDetail` 类级 `@JsonInclude(NON_NULL)` 抑制 null 字段输出
  - 清理：移除旧 `RESOLVER.findField()` 反射路径、移除 `CoreHealthIndicator` 内部 `PageConstantsRuntimeInfo` 类
  - 补充 `additional-spring-configuration-metadata.json` 配置描述（`remi.core` + `remi.core.filter-ignore` 双分组）
  - 新增 `micrometer-core`、`spring-boot-actuator` 为 optional 依赖（按需激活）
  - **异常模块桥接**：`remi-common-exception` 的 `AbstractRemiException` 实现 `IExceptionResultCode`，对齐全仓异常类体系；遗留 `AbstractYdszException` 无子类为死码，不作改动
  - **单元测试治理**：`BaseResponseTest` 添加 `@AfterEach` 清理 `RESOLVER` 静态状态（修复 pre-existing 4 个 flake）；新增 6 个桥接/traceId/注解测试覆盖 1.7.0 新特性
- **v1.6.0**（2026-08-04）：基于竞品与互联网大厂规范的第一波规范化：`UNKNOWN_CODE`、`CoreHealthIndicator`、`RequestContext` Builder / `view()` / `newCleanupGuard(Duration)`、`PageConstants.normalizePageSizeWithResult()`、`PageResponse.successWithNormalization()`、`ContextKey` 修复、修正 `DEFAULT_TENANT_ID` 文档、`setResolverIfAbsent()` 调试日志、`BaseResponse.extractResultCode()` 反射失败 DEBUG 日志
- **v1.2.0**（2026-08-03）：对齐代码重构 README，删除 12 个不属于本模块的"幽灵类"描述；`ResultCode` 移除前缀推断 default 实现，`BaseResultCode` 显式声明 HTTP 状态码；删除无调用方的 `BaseResponse.error(String msg, T data)` 重载；删除重复的 `sensitive` 包（由 `remi-common-safe` 统一提供脱敏能力）；清理配置元数据幽灵项
- **v1.1.1**（2026-08-03）：移除 Snowflake 策略（`SnowflakeTraceIdSupplier` / `TraceIdSupplier` / `TraceAutoConfiguration` / `id-type` 配置），统一 UUID TraceId；删除零消费死代码 `TraceConstants` / `SecurityConstants`（合并到 `HeaderConstants`）；`HeaderConstants.X_REQUEST_ID` 重命名为 `X_TRACE_ID`
- **v1.1.0**（2026-08-03）：新增敏感数据脱敏能力（`sensitive` 包）、TraceId 传播工具（`TraceIdPropagation`）、分页归一化方法（`PageConstants.normalizePageSize/normalizePageNum/calcOffset`）、`ResultCode` 前缀推断 HTTP 状态码默认实现；移除无消费方的 `metrics` 包（`CoreMetrics` / `CoreMetricsCallback`）；`FilterIgnoreProperties` 统一为"合并 + replace-builtin 开关"策略；`RequestContext` 改为懒初始化；修复 `SnowflakeTraceIdSupplier.toHex16` 输出反转 Bug；补齐 157 个单元测试
- **v1.0.0**（2026-08-02）：对标 common-jdbc 标准格式重构 README，补全全部 9 个章节
