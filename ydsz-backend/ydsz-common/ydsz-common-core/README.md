# ydsz-common-core

> YDSZ 公共底座核心模块（L1 基础设施层）— 统一响应模型、结果码、请求上下文、TraceId、常量定义、枚举

最小化依赖的核心库，**不包含** Spring AOP、Micrometer、SpEL、AspectJ 等框架能力。仅作为全项目最底层的类型定义与工具常量库被所有模块依赖。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L1 基础设施层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 提供统一响应模型、结果码体系、请求上下文、TraceId 生成、全局常量、通用枚举等基础能力 |
| **依赖** | Lombok、SLF4J、Jakarta Validation、Jakarta Annotation、ydsz-common-json、TransmittableThreadLocal；可选依赖 spring-boot-health（健康检查）、jakarta.servlet-api（provided） |
| **版本** | 1.0.0 |

## 核心能力

### 1. 统一响应模型（response 包）

| 类 | 说明 |
|---|---|
| `IResponse<T>` | 响应标记接口，定义 `getCode()` / `getMsg()` / `getData()` / `isSuccess()` 契约 |
| `BaseResponse<T>` | 统一 API 响应体，字段：`code` / `msg` / `data` / `traceId` / `timestamp`。使用 `@SuperBuilder` + `@YdszJsonField(notWriteNullValue=true)` + `@YdszJsonPropertyOrder` 控制序列化。提供 `success()` / `error()` / `of()` / `errorWithDetail()` 静态工厂方法，响应创建时自动通过 `CoreMetrics` SPI 上报指标 |
| `PageResponse<T>` | 分页响应体（继承 BaseResponse），字段：`total` / `pageNum` / `pageSize` / `pages`。自动计算总页数，提供 `success()` / `fail()` / `empty()` / `hasNext()` / `hasPrevious()` 方法 |
| `ProblemDetail` | RFC 7807 Problem Details 标准错误详情载体，字段：`type` / `title` / `status` / `detail` / `instance`。通过 `BaseResponse.errorWithDetail()` 使用 |

### 2. 结果码体系（code 包）

| 类 | 说明 |
|---|---|
| `ResultCode` | 结果码接口，定义 `getCode()` / `getMsg()` / `getMessageKey()`（默认返回 `"error." + 枚举名`，用于 i18n）/ `getHttpStatusCode()`（默认返回 500） |
| `BaseResultCode` | 标准结果码枚举（实现 ResultCode），共 56 个错误码，按段位规划 |

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

> 业务模块自定义错误码请实现 `ResultCode` 接口，在各模块内自行定义，不应放入 `BaseResultCode`。

### 3. 请求上下文（context 包）

| 类 | 说明 |
|---|---|
| `RequestContext` | 基于 `TransmittableThreadLocal` 的请求上下文，支持 `userId` / `tenantId` / `traceId` / `requestId` / `language` / `tenantIsolationSkipped` / 自定义属性。提供 `runAndClear()` / `newCleanupGuard()` 等自动清理模式 |
| `TenantContextHolder` | 租户上下文持有者接口（SPI），定义 `getTenantId()` / `isSuperTenant()`。由业务模块（如 ydsz-common-auth）提供具体实现，避免 core 模块循环依赖 |
| `TenantMdcFilter` | Jakarta Servlet Filter，在请求处理前将 `tenantId` / `userId` / `traceId` 从 RequestContext 写入 SLF4J MDC，请求结束后自动清理 |

### 4. TraceId 生成（trace 包）

| 类 | 说明 |
|---|---|
| `TraceIdSupplier` | TraceId 生成策略接口（`@FunctionalInterface`），SPI 扩展点 |
| `TraceIdGenerator` | TraceId 生成器统一入口（静态工具类），内部委托到 `TraceIdSupplier`。使用 `volatile` 持有当前策略，通过 `setSupplier()` 注入。生成 TraceId 后自动通过 `CoreMetrics` SPI 上报指标 |
| `SnowflakeTraceIdSupplier` | 基于 Snowflake 算法的有序 TraceId 生成器，生成 16 位十六进制字符串。使用 `AtomicLong` + CAS 自旋（无锁），支持时钟回拨检测与等待；workerId/datacenterId 支持 K8s 环境变量推导 |

### 5. 轻量级指标回调（metrics 包）

| 类 | 说明 |
|---|---|
| `CoreMetricsCallback` | 指标回调 SPI 接口，定义 `onTraceIdGenerated(strategy)` / `onResponseCreated(success, code)` 方法。上层模块（如 ydsz-common-base）可实现此接口桥接到 Micrometer / Prometheus。未注册时使用 NOOP 空操作，零性能开销 |
| `CoreMetrics` | 指标采集器统一入口，使用 `volatile` 静态 holder 管理 `CoreMetricsCallback` 实例。提供 `recordTraceIdGenerated()` / `recordResponseCreated()` 方法，由 `TraceIdGenerator` 和 `BaseResponse` 内部调用 |

### 6. 通用枚举（enums 包）

| 枚举 | 类型参数 | 说明 |
|---|---|---|
| `TypeEnum<T>` | 接口 | 通用枚举接口，定义 `getCode()` / `getDesc()` + 静态工具方法 `buildCodeMap()` / `codeOf()` |
| `DataScopeType` | `TypeEnum<String>` | 数据权限范围类型（TENANT / GROUP / COMPANY / DEPT / USER / PROJECT / REGION / CUSTOM），含 `priority` 优先级字段和 `max()` 比较方法 |
| `IdentityType` | `TypeEnum<String>` | 身份类型（YDSZ / COMPANY / VISITOR） |
| `ServiceType` | `TypeEnum<String>` | 服务类型（WEB_SERVICE / APP_SERVICE），含 `pathPrefix` 路径前缀字段 |

### 7. 全局常量（constant 包）

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

### 8. 自动配置（config 包）

| 配置类 | 激活条件 | 注册的 Bean |
|---|---|---|
| `CoreAutoConfiguration` | `ydsz.core.enabled=true`（默认启用） | `SpringMessageResolver`（i18n 消息解析器）、`TenantMdcFilter`（FilterRegistrationBean）、`CoreHealthIndicator`（Actuator 健康指标）、`PageConstantsInitializer`（分页配置运行时同步）、`coreMetricsRegistrar`（CoreMetricsCallback 自动注册） |
| `TraceAutoConfiguration` | `ydsz.core.trace.enabled=true`（默认启用） | `TraceIdSupplier`（UUID 或 Snowflake），并注入到 `TraceIdGenerator` 静态 holder |

| 属性类 | 前缀 | 说明 |
|---|---|---|
| `CoreProperties` | `ydsz.core` | 分页配置（`maxPageSize` / `defaultPageSize`，带 `@Min` / `@Max` 校验）+ 链路追踪配置（`TraceConfig` 内部类，`idType` 带 `@NotBlank` + `@Pattern` 校验）+ 租户 MDC 过滤器优先级 |
| `FilterIgnoreProperties` | `ydsz.core.filter-ignore` | 过滤器忽略路径配置，支持配置覆盖与默认值合并 |

### 9. 健康检查（health 包）

| 类 | 说明 |
|---|---|
| `CoreHealthIndicator` | Spring Boot Actuator 健康指标，访问 `/actuator/health` 时报告：TraceId 策略名称、Trace 是否启用、TraceId 类型、配置校验结果、TraceId 生成探针（pass/fail）、Snowflake workerId（当策略为 Snowflake 时）、分页配置及合法性校验、i18n 解析器状态、指标回调注册状态、过滤器忽略路径配置摘要 |

## 接入方式

### 1. POM 引入依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-core</artifactId>
</dependency>
```

### 2. 配置启用

```yaml
ydsz:
  core:
    enabled: true                          # 模块总开关（默认启用）
    max-page-size: 1000                    # 最大每页记录数上限（1-5000）
    default-page-size: 20                  # 默认每页记录数（1-5000）
    tenant-mdc-filter-order: -2147483647   # 租户 MDC 过滤器优先级
    trace:
      enabled: true                        # 链路追踪开关（默认启用）
      generate-if-missing: true           # 请求头缺失 TraceId 时自动生成
      id-type: uuid                        # uuid（默认）或 snowflake
    tenant-mdc-filter:
      enabled: true                        # 租户 MDC 过滤器开关
```

### 3. 基础使用

```java
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.code.BaseResultCode;

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
| `ydsz.core.enabled` | true | 模块总开关，关闭后 CoreAutoConfiguration 不生效 |
| `ydsz.core.max-page-size` | 1000 | 最大每页记录数上限（1-5000），运行时同步到 `PageConstants` |
| `ydsz.core.default-page-size` | 20 | 默认每页记录数（1-5000），运行时同步到 `PageConstants` |
| `ydsz.core.tenant-mdc-filter-order` | `Ordered.HIGHEST_PRECEDENCE + 100` | 租户 MDC 过滤器优先级 |
| `ydsz.core.trace.enabled` | true | 链路追踪开关，关闭后 TraceAutoConfiguration 不生效 |
| `ydsz.core.trace.generate-if-missing` | true | 请求头缺失 TraceId 时是否自动生成 |
| `ydsz.core.trace.id-type` | uuid | TraceId 生成策略：`uuid`（32 位无序）或 `snowflake`（16 位有序） |
| `ydsz.core.tenant-mdc-filter.enabled` | true | 租户 MDC 过滤器开关 |
| `ydsz.core.filter-ignore.common-ignore-urls` | 内置默认值（合并） | 公共忽略 URL 模式列表 |
| `ydsz.core.filter-ignore.auth-filter-ignore-service-names` | 内置默认值（覆盖） | 认证过滤器忽略服务名称列表 |
| `ydsz.core.filter-ignore.security-exclude-urls` | 内置默认值（合并） | 安全排除 URL 模式列表 |

> 所有配置项已通过 `additional-spring-configuration-metadata.json` 注册，IDE 自动补全支持，包含 `groups` 分组和详细描述。

## 使用示例

### 1. 统一响应返回

```java
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.core.code.BaseResultCode;

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
import com.njydsz.common.core.response.PageResponse;

PageResponse<List<UserVO>> page(PageQuery query) {
    long total = userMapper.selectCount(query);
    List<UserVO> items = userMapper.selectPage(query);
    return PageResponse.success(total, query.getPageNum(), query.getPageSize(), items);
}
```

### 3. 业务模块自定义错误码

```java
import com.njydsz.common.core.code.ResultCode;

public enum OrderResultCode implements ResultCode {
    ORDER_NOT_FOUND("B02001", "订单不存在"),
    ORDER_CANCELLED("B02002", "订单已取消");

    private final String code;
    private final String msg;

    OrderResultCode(String code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    @Override public String getCode() { return code; }
    @Override public String getMsg() { return msg; }

    @Override public int getHttpStatusCode() {
        return this == ORDER_NOT_FOUND ? 404 : 400;
    }
}

// 使用
return BaseResponse.error(OrderResultCode.ORDER_NOT_FOUND);
```

### 4. 请求上下文传递

```java
import com.njydsz.common.core.context.RequestContext;

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

### 5. 切换 TraceId 生成策略

```yaml
ydsz:
  core:
    trace:
      id-type: snowflake   # 切换为 Snowflake 有序策略
```

```java
import com.njydsz.common.core.trace.TraceIdGenerator;

// 自动使用配置的策略，无需感知 SPI 细节
String traceId = TraceIdGenerator.generate();
```

## SPI 扩展点

| SPI 接口 | 用途 | 实现方 |
|---|---|---|
| `TraceIdSupplier` | TraceId 生成策略扩展点，自定义 TraceId 格式与生成逻辑 | 框架内置 `UuidTraceIdSupplier`（lambda）与 `SnowflakeTraceIdSupplier`，业务可覆盖 |
| `BaseResponse.MessageResolver` | 国际化消息解析 SPI，将响应消息委托到 Spring MessageSource 等实现 | 框架内置 `SpringMessageResolver`（自动注册），业务可覆盖 |
| `CoreMetricsCallback` | 核心模块指标回调 SPI，将 TraceId 生成与响应创建事件桥接到监控系统 | 上层模块（如 ydsz-common-base）实现并注册为 Bean，未注册时为 NOOP |
| `TenantContextHolder` | 租户上下文持有者 SPI，避免 core 模块循环依赖 | 业务模块（如 ydsz-common-auth）提供实现 |

## 健康检查

| 端点 | 说明 | 触发条件 |
|---|---|---|
| `/actuator/health` | Core 模块健康检查作为整体 health 端点的一部分 | `spring-boot-health` 在类路径，`ydsz.core.enabled=true` |

**`CoreHealthIndicator` 暴露信息**：

- `traceIdStrategy` — 当前 TraceId 策略类名（如 `UuidTraceIdSupplier` / `SnowflakeTraceIdSupplier`）
- `traceEnabled` / `traceIdType` — 链路追踪开关与配置类型
- `configValidation` — 配置项合法性校验结果（PASS / FAIL）
- `traceIdProbe` — TraceId 生成探针（pass / fail）
- `snowflakeWorkerId` — Snowflake workerId（当策略为 Snowflake 时）
- `maxPageSize` / `defaultPageSize` — 运行时分页配置
- `pageSizeValidation` — 分页合法性校验（WARN 表示 default > max）
- `i18nResolverRegistered` — i18n 解析器是否注册
- `metricsCallbackRegistered` — 指标回调是否注册
- `filterIgnoreCommonUrls` / `filterIgnoreSecurityExcludeUrls` / `authFilterIgnoreServiceNames` — 过滤器忽略路径配置摘要

**健康状态规则**：

- TraceId 生成探针失败 → DOWN
- 配置项非法（如 `id-type` 不在 `uuid` / `snowflake` 范围内） → DOWN
- 所有检查通过 → UP

## 注意事项

1. **业务响应码与 HTTP 状态码区分**：`BaseResponse.code` 是业务响应码（`String` 类型，如 `"A00000"`），`ResultCode.getHttpStatusCode()` 返回对应的 HTTP 状态码（`int` 类型，如 `200` / `400` / `500`）。前端判断成功应检查 `resp.code === "A00000"`，而非 HTTP 状态码 `200`。
2. **静态 holder 模式**：`TraceIdGenerator` / `BaseResponse` / `CoreMetrics` 使用 `volatile` 静态字段持有策略实例，而非 Spring Bean 依赖注入。原因是这些组件在项目极早期（如 Filter 初始化、日志框架启动）即被调用，Spring 容器可能尚未就绪。代价是多 ApplicationContext 场景下最后一个上下文的策略会覆盖前一个；单元测试间需调用 `TraceIdGenerator.resetToDefault()` 隔离状态。
3. **RequestContext 必须显式清理**：基于 TransmittableThreadLocal 的上下文必须在请求结束时调用 `RequestContext.clear()`，建议使用 `try-with-resources` 配合 `RequestContext.newCleanupGuard()`，避免线程池复用导致内存泄漏或上下文串扰。
4. **业务模块自定义错误码请实现 `ResultCode` 接口**：项目/合同/商机（B4xxxx）、财务/成本（B5xxxx）、资源/工时（B6xxxx）、报表（B8xxxx）等业务域专属错误码已从 `BaseResultCode` 中删除，不应再放入。
5. **配置校验 fail-fast**：`CoreProperties` 及其 `TraceConfig` 内部类使用 JSR-303 校验注解（`@Min` / `@Max` / `@NotBlank` / `@Pattern`），配合 `@Validated` 和 `@Valid` 实现启动时校验。配置非法时应用启动失败。
6. **零依赖原则**：本模块不含 Spring AOP / AspectJ / Micrometer / SpEL / Spring Web MVC / MyBatis-Plus / Redis 依赖。监控能力通过 `CoreMetricsCallback` SPI 解耦，由上层模块桥接。
7. **国际化资源**：模块自带 `i18n/messages.properties`（英文默认）和 `i18n/messages_zh_CN.properties`（中文）资源文件，覆盖全部 56 个 `BaseResultCode` 错误码。消息 key 格式为 `error.{ENUM_NAME}`，与 `ResultCode.getMessageKey()` 默认实现一致。
8. **GraalVM Native Image 支持**：`META-INF/native-image/com.njydsz/ydsz-common-core/native-image.properties` 注册了 `BaseResponse` / `PageResponse` / `ProblemDetail` / `CoreProperties` / `CoreProperties$TraceConfig` / `FilterIgnoreProperties` 的反射配置。

## 变更记录

- **v1.0.0**（2026-08-02）：对标 common-jdbc 标准格式重构 README，补全全部 9 个章节
