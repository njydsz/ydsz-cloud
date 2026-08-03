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
| `BaseResponse<T>` | 统一 API 响应体，字段：`code` / `msg` / `data` / `traceId` / `timestamp`。使用 `@SuperBuilder` + `@YdszJsonField(notWriteNullValue=true)` + `@YdszJsonPropertyOrder` 控制序列化。提供 `success()` / `error()` / `of()` / `errorWithDetail()` 静态工厂方法，消息支持 i18n 国际化解析 |
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

### 4. TraceId 生成与传播（trace 包）

| 类 | 说明 |
|---|---|
| `TraceIdGenerator` | TraceId 生成器（纯 UUID，无配置切换）：一次读取 16 字节 `SecureRandom` 随机数，编码为 32 位 hex，零中间 String 分配。保证全局唯一，满足请求追踪需求 |
| `TraceIdPropagation` | TraceId 传播工具类（纯 JDK，无框架依赖）：从 MDC 读取 traceId 并生成 `X-Trace-Id` 请求头，供 RestTemplate / WebClient / OkHttp 等 HTTP 客户端拦截器复用，实现服务间调用链路贯穿。提供 `traceHeader()` / `traceHeaderOrCreate()` / `currentTraceId()` 等方法 |

### 5. 敏感数据脱敏（sensitive 包）

| 类 | 说明 |
|---|---|
| `Sensitive` | 字段标注注解：`@Sensitive(type = SensitiveType.MOBILE)` 标注需要脱敏的字段，支持自定义脱敏器 |
| `SensitiveType` | 敏感数据类型枚举：MOBILE（保留前3后4）/ ID_CARD（保留前4后4）/ BANK_CARD / EMAIL / NAME / ADDRESS / PASSWORD / CUSTOM |
| `SensitiveDataMasker` | 纯 Java 脱敏算法工具类（与 JSON 引擎解耦）：`mask(value, type)` 单值脱敏 + `maskObject(obj)` 反射遍历 `@Sensitive` 标注字段就地脱敏，递归处理父类字段。自定义脱敏器通过 `SensitiveMasker` SPI + `Sensitive#masker()` 指定 |

> **JSON 序列化集成**：core 模块保持零依赖原则不绑定具体 JSON 引擎，上层模块（如 ydsz-common-web）可在序列化链路中读取 `@Sensitive` 注解并调用 `SensitiveDataMasker.mask()` 实现自动脱敏。

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
| `HeaderConstants` | HTTP 请求头常量（认证 Token / 数据权限维度 / 列级权限 / 链路追踪 `X-Trace-Id` + MDC key / 网络信息），链路追踪统一入口 |
| `TokenConstants` | Token 相关常量（标识 / 前缀 "ydsz" / 回调 URL 参数名） |
| `PageConstants` | 分页默认值与上限（`DEFAULT_PAGE_NUM=1` / `DEFAULT_PAGE_SIZE=20` / `MAX_PAGE_SIZE=5000`）。运行时值由 `CoreProperties` 配置覆盖 |
| `FilterIgnoreConstant` | 过滤器忽略 URL 模式与认证忽略服务名称 |
| `CacheConstants` | 缓存名称常量（workflow / nextwiki / system 三个模块的 8 个 cache name） |
| `SystemConstants` | 系统级常量（`SYSTEM_USER_ID="SYSTEM"` / `DEFAULT_TENANT_ID="1"` / `DEFAULT_LOCALE="zh-CN"`） |
| `YdszMessageTopics` | 消息中心 RocketMQ Topic / ConsumerGroup 常量 |

### 8. 自动配置（config 包）

| 配置类 | 激活条件 | 注册的 Bean |
|---|---|---|
| `CoreAutoConfiguration` | `ydsz.core.enabled=true`（默认启用） | `SpringMessageResolver`（i18n 消息解析器）、`TenantMdcFilter`（FilterRegistrationBean）、`CoreHealthIndicator`（Actuator 健康指标）、`PageConstantsInitializer`（分页配置运行时同步） |

| 属性类 | 前缀 | 说明 |
|---|---|---|
| `CoreProperties` | `ydsz.core` | 分页配置（`maxPageSize` / `defaultPageSize`，带 `@Min` / `@Max` 校验）+ 租户 MDC 过滤器优先级 |
| `FilterIgnoreProperties` | `ydsz.core.filter-ignore` | 过滤器忽略路径配置，支持配置覆盖与默认值合并 |

### 9. 健康检查（health 包）

| 类 | 说明 |
|---|---|
| `CoreHealthIndicator` | Spring Boot Actuator 健康指标，访问 `/actuator/health` 时报告：TraceId 生成探针（pass/fail）、配置校验结果、i18n 解析器状态 |

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

### 5. TraceId 使用

```java
import com.njydsz.common.core.trace.TraceIdGenerator;

// 生成 32 位唯一 TraceId
String traceId = TraceIdGenerator.generate();
```

## SPI 扩展点

| SPI 接口 | 用途 | 实现方 |
|---|---|---|
| `BaseResponse.MessageResolver` | 国际化消息解析 SPI，将响应消息委托到 Spring MessageSource 等实现 | 框架内置 `SpringMessageResolver`（自动注册），业务可覆盖 |
| `TenantContextHolder` | 租户上下文持有者 SPI，避免 core 模块循环依赖 | 业务模块（如 ydsz-common-auth）提供实现 |
| `SensitiveDataMasker.SensitiveMasker` | 自定义脱敏器 SPI，通过 `@Sensitive(masker=...)` 指定 | 业务按需实现 |

## 健康检查

| 端点 | 说明 | 触发条件 |
|---|---|---|
| `/actuator/health` | Core 模块健康检查作为整体 health 端点的一部分 | `spring-boot-health` 在类路径，`ydsz.core.enabled=true` |

**`CoreHealthIndicator` 暴露信息**：

- `configValidation` — 配置项合法性校验结果（PASS / FAIL）
- `traceIdProbe` — TraceId 生成探针（pass / fail）
- `i18nResolverRegistered` — i18n 解析器是否注册

**健康状态规则**：

- TraceId 生成探针失败 → DOWN
- 配置项非法 → DOWN
- 所有检查通过 → UP

## 注意事项

1. **业务响应码与 HTTP 状态码区分**：`BaseResponse.code` 是业务响应码（`String` 类型，如 `"A00000"`），`ResultCode.getHttpStatusCode()` 返回对应的 HTTP 状态码（`int` 类型，如 `200` / `400` / `500`）。前端判断成功应检查 `resp.code === "A00000"`，而非 HTTP 状态码 `200`。
2. **RequestContext 必须显式清理**：基于 TransmittableThreadLocal 的上下文必须在请求结束时调用 `RequestContext.clear()`，建议使用 `try-with-resources` 配合 `RequestContext.newCleanupGuard()`，避免线程池复用导致内存泄漏或上下文串扰。
3. **业务模块自定义错误码请实现 `ResultCode` 接口**：项目/合同/商机（B4xxxx）、财务/成本（B5xxxx）、资源/工时（B6xxxx）、报表（B8xxxx）等业务域专属错误码已从 `BaseResultCode` 中删除，不应再放入。
4. **配置校验 fail-fast**：`CoreProperties` 使用 JSR-303 校验注解（`@Min` / `@Max`），配合 `@Validated` 实现启动时校验。配置非法时应用启动失败。
5. **零依赖原则**：本模块不含 Spring AOP / AspectJ / Micrometer / SpEL / Spring Web MVC / MyBatis-Plus / Redis 依赖。监控等上层能力通过 SPI 接口解耦，由上层模块实现。
6. **国际化资源**：模块自带 `i18n/messages.properties`（英文默认）和 `i18n/messages_zh_CN.properties`（中文）资源文件，覆盖全部 56 个 `BaseResultCode` 错误码。消息 key 格式为 `error.{ENUM_NAME}`，与 `ResultCode.getMessageKey()` 默认实现一致。
7. **GraalVM Native Image 支持**：`META-INF/native-image/com.njydsz/ydsz-common-core/native-image.properties` 注册了 `BaseResponse` / `PageResponse` / `ProblemDetail` / `CoreProperties` / `FilterIgnoreProperties` 的反射配置。

## 变更记录

- **v1.1.1**（2026-08-03）：移除 Snowflake 策略（`SnowflakeTraceIdSupplier` / `TraceIdSupplier` / `TraceAutoConfiguration` / `id-type` 配置），统一 UUID TraceId；删除零消费死代码 `TraceConstants` / `SecurityConstants`（合并到 `HeaderConstants`）；`HeaderConstants.X_REQUEST_ID` 重命名为 `X_TRACE_ID`
- **v1.1.0**（2026-08-03）：新增敏感数据脱敏能力（`sensitive` 包）、TraceId 传播工具（`TraceIdPropagation`）、分页归一化方法（`PageConstants.normalizePageSize/normalizePageNum/calcOffset`）、`ResultCode` 前缀推断 HTTP 状态码默认实现；移除无消费方的 `metrics` 包（`CoreMetrics` / `CoreMetricsCallback`）；`FilterIgnoreProperties` 统一为"合并 + replace-builtin 开关"策略；`RequestContext` 改为懒初始化；修复 `SnowflakeTraceIdSupplier.toHex16` 输出反转 Bug；补齐 157 个单元测试
- **v1.0.0**（2026-08-02）：对标 common-jdbc 标准格式重构 README，补全全部 9 个章节
