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
| **源文件数** | 32 个 Java 文件（main），4 个测试文件（test） |
| **依赖范围** | Lombok + SLF4J + Jakarta Validation + ydsz-common-json + TTL + Spring Boot（可选） |

## 目录结构

```
com.njydsz.common.core
├── response/        # 统一响应模型（3 个类）
├── code/            # 结果码体系（2 个类）
├── context/         # 请求上下文与租户上下文（4 个类）
├── trace/           # TraceId 生成策略（3 个类）
├── request/         # 请求标记接口（1 个类，预留 API）
├── enums/           # 通用枚举（5 个类）
├── constant/        # 全局常量定义（8 个类）
├── config/          # 自动配置与属性绑定（5 个类）
└── health/          # 健康检查（1 个类）
```

## 核心能力

### 1. 统一响应模型（`response` 包）

| 类 | 说明 |
|---|---|
| `IResponse<T>` | 响应标记接口，定义 `getCode()` / `getMsg()` / `getData()` / `isSuccess()` 契约 |
| `BaseResponse<T>` | 统一 API 响应体，字段：`code` / `msg` / `data` / `traceId` / `timestamp`。使用 `@SuperBuilder` + `@YdszJsonField(notWriteNullValue=true)` + `@YdszJsonPropertyOrder` 控制序列化。提供 `success()` / `error()` / `of()` 静态工厂方法，以及 `error(ResultCode, Throwable)` 便捷方法。函数式 API（`orElse` / `orElseThrow` / `map` / `ifSuccess` / `ifFailed`）已标记 `@Deprecated` |
| `PageResponse<T>` | 分页响应体（继承 BaseResponse），字段：`total` / `pageNum` / `pageSize` / `pages`。自动计算总页数，提供 `success()` / `fail()` / `empty()` / `hasNext()` / `hasPrevious()` 方法 |

**业务响应码与 HTTP 状态码区分**：

- `BaseResponse.code` 是**业务响应码**（`String` 类型），如 `"A00000"` 表示成功
- `ResultCode.getHttpStatusCode()` 返回对应的 **HTTP 状态码**（`int` 类型），如 `200` / `400` / `500`
- 前端判断成功应检查 `resp.code === "A00000"`，而非 HTTP 状态码 `200`

**国际化支持**：

`BaseResponse` 内置 `MessageResolver` 接口，当 Spring `MessageSource` 可用时，`CoreAutoConfiguration` 自动注册 `SpringMessageResolver` 并绑定到 `BaseResponse`，使成功/失败消息支持 i18n 国际化解析。可通过 `BaseResponse.isResolverRegistered()` 检查解析器是否已注册。

```java
// 返回成功
return BaseResponse.success(user);

// 返回成功（带自定义消息）
return BaseResponse.success("操作成功", user);

// 返回失败（带错误码）
return BaseResponse.error("A01002", "用户名已存在");

// 返回失败（使用 ResultCode 枚举）
return BaseResponse.error(BaseResultCode.VALIDATION_FAILED);

// 返回失败（从异常对象提取消息）
return BaseResponse.error(BaseResultCode.INTERNAL_ERROR, exception);
```

### 2. 结果码体系（`code` 包）

| 类 | 说明 |
|---|---|
| `ResultCode` | 结果码接口，定义 `getCode()` / `getMsg()` / `getMessageKey()`（默认返回 `"error." + 枚举名`，用于 i18n）/ `getHttpStatusCode()`（默认返回 500） |
| `BaseResultCode` | 标准结果码枚举（实现 ResultCode），共 30+ 个错误码，按段位规划 |

**错误码段位规划**：

| 段位 | 含义 | HTTP 映射 |
|---|---|---|
| `A00000` | 成功 | 200 |
| `A1xxxx` | 通用错误（参数校验、资源不存在、限流等） | 400 / 404 / 405 / 408 / 409 / 429 |
| `A2xxxx` | 认证授权（未登录、Token 过期、权限不足等） | 401 / 403 / 423 |
| `B3xxxx` | 用户/组织/人员 | 404 / 401 |
| `B7xxxx` | 工作流/审批 | 404 / 400 / 403 |
| `B102xx` | 系统级业务异常 | 500 / 503 |
| `C1xxxx` | 数据库/第三方异常 | 409 / 400 / 503 |
| `C99999` | 未知错误 | 500 |

> **注意**：项目/合同/商机（B4xxxx）、财务/成本（B5xxxx）、资源/工时（B6xxxx）、报表（B8xxxx）等业务域专属错误码已从 `BaseResultCode` 中删除。业务模块自定义错误码请实现 `ResultCode` 接口，在各模块内自行定义。

```java
public enum OrderResultCode implements ResultCode {
    ORDER_NOT_FOUND("B02001", "订单不存在");
    
    private final String code;
    private final String msg;
    
    @Override public String getCode() { return code; }
    @Override public String getMsg() { return msg; }
    @Override public int getHttpStatusCode() { return 404; } // 覆盖默认 500
}
```

### 3. 请求上下文（`context` 包）

| 类 | 说明 |
|---|---|
| `RequestContext` | 基于 `TransmittableThreadLocal` 的请求上下文，支持 `userId` / `tenantId` / `traceId` / `requestId` / `language` / 自定义属性。手动快照/传播方法（`snapshot()` / `restore()` / `wrapCallable()` / `wrapRunnable()`）已标记 `@Deprecated`，推荐使用 TTL 自动传播 |
| `ContextKey<T>` | 强类型上下文 Key（已标记 `@Deprecated`）。当前项目全部使用 String key 方式 |
| `TenantContextHolder` | 租户上下文持有者接口（SPI），定义 `getTenantId()` / `isSuperTenant()`。由业务模块（如 `ydsz-common-auth`）提供具体实现，避免 core 模块循环依赖 |
| `TenantMdcFilter` | Jakarta Servlet Filter，在请求处理前将 `tenantId` / `userId` / `traceId` 从 RequestContext 写入 SLF4J MDC，请求结束后自动清理。Filter 优先级可通过 `ydsz.core.tenant-mdc-filter-order` 配置 |

**内置上下文 Key**：

| Key 常量 | 说明 |
|---|---|
| `KEY_USER_ID` | 用户 ID |
| `KEY_TENANT_ID` | 租户 ID |
| `KEY_TRACE_ID` | 链路追踪 ID |
| `KEY_REQUEST_ID` | 请求 ID |
| `KEY_LANGUAGE` | 语言区域 |
| `KEY_TENANT_ISOLATION_SKIPPED` | 租户隔离跳过标记（用于 anon-urls 白名单场景） |

```java
// try-with-resources 自动清理
try (RequestContext.CleanupGuard guard = RequestContext.newCleanupGuard()) {
    RequestContext.setUserId("user123");
    RequestContext.setTenantId("tenant456");
    RequestContext.setRequestId("req-789");
    RequestContext.setLanguage("zh-CN");
    // ... 业务逻辑
}

// 自定义属性（String key 方式）
RequestContext.put("deptCode", "R&D");
String code = RequestContext.get("deptCode");
```

### 4. TraceId 生成（`trace` 包）

| 类 | 说明 |
|---|---|
| `TraceIdSupplier` | TraceId 生成策略接口（`@FunctionalInterface`），SPI 扩展点 |
| `TraceIdGenerator` | TraceId 生成器统一入口（静态工具类），内部委托到 `TraceIdSupplier`。使用 `volatile` 持有当前策略，通过 `setSupplier()` 注入 |
| `SnowflakeTraceIdSupplier` | 基于 Snowflake 算法的有序 TraceId 生成器。生成 16 位十六进制字符串。使用 `AtomicLong` + CAS 自旋（无锁），支持时钟回拨检测与等待。**每轮 CAS 循环重新读取时间戳**，避免高并发场景下误判时钟回拨。workerId/datacenterId 支持 K8s 环境变量推导 |

**两种生成策略**：

| 策略 | 配置值 | ID 格式 | 特点 |
|---|---|---|---|
| UUID（默认） | `ydsz.core.trace.id-type=uuid` | 32 位十六进制 | 无序，全局唯一 |
| Snowflake | `ydsz.core.trace.id-type=snowflake` | 16 位十六进制 | 按时间有序，可排序日志还原请求时序 |

```yaml
ydsz:
  core:
    trace:
      id-type: snowflake  # 切换为有序 TraceId
```

**Snowflake workerId/datacenterId 推导优先级**：

1. 环境变量 `SNOWFLAKE_WORKER_ID` / `SNOWFLAKE_DATACENTER_ID`（K8s deployment 显式配置）
2. 环境变量 `POD_NAME` / `HOSTNAME` 的 hashCode 取低 5 位（workerId）
3. 环境变量 `POD_IP` 的 IP 地址最后一字节低 5 位（datacenterId）
4. PID % 32 / 本机网卡 IP 降级方案

业务方可提供自定义 `TraceIdSupplier` Bean 覆盖默认实现：

```java
@Bean
public TraceIdSupplier customTraceIdSupplier() {
    return () -> "trace-" + System.nanoTime();
}
```

### 5. 请求标记接口（`request` 包）

| 类 | 说明 |
|---|---|
| `IRequest` | 请求标记接口（`extends Serializable`），当前为预留接口 |

> **注意**：`BaseRequest` 和 `PageRequest` 已删除（零业务使用）。业务模块使用 `PageQuery`（domain 模块）作为分页查询基类。如需 HTTP API 层的请求封装，可实现 `IRequest` 接口。

### 6. 通用枚举（`enums` 包）

| 枚举 | 类型参数 | 说明 |
|---|---|---|
| `TypeEnum<T>` | 接口 | 通用枚举接口，定义 `getCode()` / `getDesc()` + 静态工具方法 `buildCodeMap()` / `codeOf()` |
| `DataScopeType` | `TypeEnum<String>` | 数据权限范围类型（TENANT / GROUP / COMPANY / DEPT / USER / PROJECT / REGION / CUSTOM），含 `priority` 优先级字段和 `max()` 比较方法 |
| `IdentityType` | `TypeEnum<String>` | 身份类型（YDSZ / COMPANY / VISITOR） |
| `ServiceType` | `TypeEnum<String>` | 服务类型（WEB_SERVICE / APP_SERVICE），含 `pathPrefix` 路径前缀字段 |
| `YesOrNo` | `TypeEnum<Integer>` | 是/否枚举（NO=0 / YES=1），已标记 `@Deprecated`（项目实际使用 `Integer` 或 `Boolean`） |

所有枚举均通过 `TypeEnum.buildCodeMap()` 构建不可变映射，提供 `of()`（安全查找，返回 null）和 `codeOf()`（严格查找，抛异常）两种查找方式。

### 7. 全局常量（`constant` 包）

| 常量类 | 说明 |
|---|---|
| `HeaderConstants` | HTTP 请求头常量（认证 Token / 数据权限维度 / 列级权限 / 链路追踪 / 网络信息），共 20 个自定义头常量。已删除 22 个零引用的标准 HTTP 头常量 |
| `TokenConstants` | Token 相关常量（标识 / 前缀 "ydsz" / 回调 URL 参数名） |
| `SecurityConstants` | 安全常量（密钥属性名 / BCrypt 强度 12 / CSRF 头部与参数名 / 安全头部引用） |
| `PageConstants` | 分页默认值与上限（`DEFAULT_PAGE_NUM=1` / `DEFAULT_PAGE_SIZE=20` / `MAX_PAGE_SIZE=5000`）。**运行时值**由 `CoreProperties` 配置覆盖，通过 `getDefaultPageSize()` / `getMaxPageSize()` 获取 |
| `FilterIgnoreConstant` | 过滤器忽略 URL 模式（静态资源 / API 文档 / actuator）与认证忽略服务名称（10 个 web 模块），不可变 Set。建议通过 `FilterIgnoreProperties` 配置覆盖 |
| `TraceConstants` | 链路追踪常量（`TRACE_ID_HEADER="X-Trace-Id"` / `MDC_TRACE_ID_KEY="traceId"`） |
| `CacheConstants` | 缓存名称常量（workflow / nextwiki / system 三个模块的 8 个 cache name） |
| `SystemConstants` | 系统级常量（`SYSTEM_USER_ID="SYSTEM"` / `DEFAULT_TENANT_ID="1"` / `DEFAULT_LOCALE="zh-CN"`） |
| `YdszMessageTopics` | 消息中心 RocketMQ Topic / ConsumerGroup 常量（单条消息 / 批量消息 / 死信队列） |

> **关于 CacheConstants / YdszMessageTopics**：这两个常量类包含业务模块特定的 cache name 和 MQ topic。当前保留在 core 模块作为"项目级共享常量"，后续可考虑迁移到 `ydsz-common-domain` 或各自模块的 API 包。

### 8. 自动配置（`config` 包）

| 配置类 | 激活条件 | 注册的 Bean |
|---|---|---|
| `CoreAutoConfiguration` | `ydsz.core.enabled=true`（默认启用） | `SpringMessageResolver`（i18n 消息解析器）、`TenantMdcFilter`（FilterRegistrationBean，order 可配置）、`CoreHealthIndicator`（Actuator 健康指标）、`PageConstantsInitializer`（分页配置运行时同步） |
| `TraceAutoConfiguration` | `ydsz.core.trace.enabled=true`（默认启用） | `TraceIdSupplier`（UUID 或 Snowflake，根据 `id-type` 配置选择） |

| 属性类 | 前缀 | 说明 |
|---|---|---|
| `CoreProperties` | `ydsz.core` | 分页配置（`maxPageSize` / `defaultPageSize`，带 `@Min` / `@Max` 校验）+ 链路追踪配置（`TraceConfig` 内部类）+ 租户 MDC 过滤器优先级（`tenantMdcFilterOrder`） |
| `FilterIgnoreProperties` | `ydsz.core.filter-ignore` | 过滤器忽略路径配置，支持配置覆盖与默认值合并（`getMergedCommonIgnoreUrls()` / `getMergedSecurityExcludeUrls()` / `getResolvedAuthFilterIgnoreServiceNames()`） |
| `SpringMessageResolver` | — | `BaseResponse.MessageResolver` 实现，适配 Spring `MessageSource`，从 `LocaleContextHolder` 获取 Locale 解析国际化消息 |

### 9. 健康检查（`health` 包）

| 类 | 说明 |
|---|---|
| `CoreHealthIndicator` | Spring Boot Actuator 健康指标，访问 `/actuator/health` 时报告：TraceId 生成策略名称、Trace 是否启用、TraceId 类型、maxPageSize、defaultPageSize、i18n 解析器状态、过滤器忽略路径配置摘要 |

## 配置项

```yaml
ydsz:
  core:
    enabled: true                          # 模块总开关（默认启用）
    max-page-size: 1000                    # 最大每页记录数上限（1-5000），运行时生效
    default-page-size: 20                  # 默认每页记录数（≥1），运行时生效
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

所有配置项已通过 `additional-spring-configuration-metadata.json` 注册，IDE 自动补全支持。

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
| `jakarta.validation-api` | compile | `@Min` / `@Max` 校验注解 |
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
- Micrometer（无指标采集，Metrics 基类已迁移到 `ydsz-common-base`）
- SpEL（无表达式求值，DAG 条件评估已迁移到 `ydsz-common-domain`）
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
- `CoreProperties` / `CoreProperties$TraceConfig` / `FilterIgnoreProperties` — 无参构造器

## 设计决策

### 静态 holder 模式（TraceIdGenerator / BaseResponse）

`TraceIdGenerator` 和 `BaseResponse` 使用 `volatile` 静态字段持有策略实例，而非 Spring Bean 依赖注入。这是因为这些组件在项目极早期（如 Filter 初始化、日志框架启动）即被调用，此时 Spring 容器可能尚未就绪。静态 holder 保证零依赖、即时可用。

**代价**：
- 多 ApplicationContext 场景下最后一个上下文的策略会覆盖前一个
- 单元测试间需手动调用 `resetToDefault()` / `setResolver(null)` 隔离状态

### 分页配置运行时覆盖

`PageConstants` 的 `DEFAULT_PAGE_SIZE` / `MAX_PAGE_SIZE` 为编译期常量（用于 `@Max` 注解等编译时常量场景）。运行时实际值由 `CoreProperties` 配置控制，通过 `CoreAutoConfiguration.PageConstantsInitializer`（`SmartInitializingSingleton`）在容器启动后同步到 `PageConstants` 的 volatile 字段。

**使用建议**：
- 需要运行时值时使用 `PageConstants.getDefaultPageSize()` / `PageConstants.getMaxPageSize()`
- 需要编译时常量时（如 `@Max` 注解）使用 `PageConstants.DEFAULT_PAGE_SIZE` / `PageConstants.MAX_PAGE_SIZE`

### 废弃 API 策略

以下 API 已标记 `@Deprecated`，当前版本保留但不再推荐使用：

| API | 原因 | 替代方案 |
|---|---|---|
| `ContextKey<T>` 类 + `RequestContext` 的 4 个 ContextKey 方法 | 项目全部使用 String key 方式 | `RequestContext.put(String, Object)` / `RequestContext.get(String)` |
| `RequestContext` 的 `snapshot()` / `restore()` / `wrapCallable()` / `wrapRunnable()` | TTL 已自动传播上下文 | 配合 `common-thread` 的 `ThreadPoolTaskExecutor` 使用 |
| `BaseResponse` 的 `orElse()` / `orElseThrow()` / `map()` / `ifSuccess()` / `ifFailed()` | 项目未使用函数式风格处理响应 | 直接使用 `isSuccess()` + `getData()` 判断 |
| `YesOrNo` 枚举 | 项目使用 `Integer`（0/1）或 `Boolean` | 直接使用基本类型 |

> 如 6 个月内仍无使用场景，这些 API 将在下一个大版本中删除。

## 变更记录

### 2025-07-30 "简单够用"原则评估与过度设计清理

| 级别 | 变更内容 |
|---|---|
| **P0-1** | 删除 `PageRequest` + `BaseRequest`（零业务使用，业务模块全部使用 `PageQuery`） |
| **P0-2** | 删除 `BaseResponse` 的 Clock 抽象（`CLOCK_HOLDER` / `setClock()` / `getClock()`），改用 `System.currentTimeMillis()` 直接调用。新增 `isResolverRegistered()` 替代 `getClock() != null` 健康检查 |
| **P0-3** | 删除空 featureflag 测试目录 |
| **P1-1** | `HeaderConstants` 删除 22 个零引用的标准 HTTP 头常量（如 `CONTENT_TYPE` / `AUTHORIZATION` / `ACCEPT_LANGUAGE` 等），仅保留 20 个项目自定义头常量 |
| **P1-2** | `BaseResultCode` 删除 13 个业务域专属错误码（B4xxxx 项目/合同/商机、B5xxxx 财务/成本、B6xxxx 资源/工时、B8xxxx 报表），业务模块自定义错误码请实现 `ResultCode` 接口 |
| **P2-1** | `ContextKey<T>` 类标记 `@Deprecated`；`RequestContext` 的 `put(ContextKey, T)` / `get(ContextKey)` / `getOptional(ContextKey)` / `remove(ContextKey)` 4 个方法标记 `@Deprecated` |
| **P2-2** | `BaseResponse` 的 `orElse()` / `orElseThrow()` / `map()` / `ifSuccess()` / `ifFailed()` 5 个函数式方法标记 `@Deprecated` |
| **P2-3** | `RequestContext` 的 `snapshot()` / `restore()` / `wrapCallable()` / `wrapRunnable()` 4 个手动传播方法标记 `@Deprecated` |
| **P3-1** | `YesOrNo` 枚举标记 `@Deprecated` |

### 2025-07-30 深度优化迭代

| 级别 | 变更内容 |
|---|---|
| **P0-1** | SnowflakeTraceIdSupplier CAS 循环每轮重新读取时间戳，修复高并发场景下误判时钟回拨 |
| **P0-2** | PageConstants 新增运行时覆盖机制（`getDefaultPageSize()` / `getMaxPageSize()`），`CoreAutoConfiguration` 启动时同步 `CoreProperties` 配置，消除编译期常量与运行时配置脱节 |
| **P0-3** | TraceIdGenerator / BaseResponse 静态 holder 设计决策文档化 |
| **P0-4** | 删除残留 FeatureFlag 测试文件（引用已删除的类） |
| **P1-1** | `ResultCode` 接口新增 `getHttpStatusCode()` 默认方法（默认 500） |
| **P1-2** | `PageResponse` 新增 `success(long, int, int, T)` 基本类型便捷重载 |
| **P1-3** | `SnowflakeTraceIdSupplier` workerId/datacenterId 推导增强：支持 K8s 环境变量（`SNOWFLAKE_WORKER_ID` / `POD_NAME` / `HOSTNAME` / `POD_IP`） |
| **P1-4** | `RequestContext` 新增 `getRequestId()` / `setRequestId()` / `getLanguage()` / `setLanguage()` 便捷方法 |
| **P1-5** | `CoreHealthIndicator` 增强：新增 traceIdType、i18nResolver 状态、filterIgnore 配置摘要 |
| **P1-6** | `BaseResponse` 新增 `error(ResultCode, Throwable)` 便捷方法 |
| **P2-1** | 删除 `ProtocolConstants`（零外部引用死代码） |
| **P2-2** | 删除 `PageConstants` 中零引用的参数名常量（`PAGE_NUM` / `PAGE_SIZE` / `ORDER_BY_COLUMN` / `IS_ASC` / `IS_DESC`） |
| **P2-4** | `TraceIdGenerator` 移除对 `config` 包的反向依赖 |
| **P2-5** | `SnowflakeTraceIdSupplier` 使用手动 hex 编码替代 `String.format`，提升性能 |
| **P3-1** | 测试文件中 `Executors.newFixedThreadPool` 替换为 `ThreadPoolExecutor` |
| **P3-2** | `FilterIgnoreConstant` Javadoc 补充配置覆盖说明 |
| **P3-3** | 配置元数据新增 `ydsz.core.tenant-mdc-filter-order` 配置项，`CoreAutoConfiguration` 使用配置的 filter order |

### 2025-07-29 瘦身重构

以下功能已从 core 模块迁出：

| 原功能 | 迁移目标 | 原因 |
|---|---|---|
| Feature Flag（特性开关） | 直接删除 | 无业务模块使用，Spring Boot 内置条件装配已满足需求 |
| Graceful Shutdown（优雅停机） | 直接删除 | 无业务模块使用，Spring Boot 内置 `server.shutdown.grace-period` |
| AbstractModuleMetrics（Metrics 基类） | `ydsz-common-base` | 需要 Micrometer 依赖，不属于最小核心 |
| DAG 条件评估（SpELConditionEvaluator） | `ydsz-common-domain` | 需要 SpEL 依赖，属于领域层能力 |
| Job 框架（JobHandler / MapProcessor 等） | `ydsz-common-domain` | 属于领域层调度能力 |

**core 模块最终状态**：32 个 Java 文件，仅包含 response / request / context / trace / constant / enums / code / config / health 核心能力，零 AOP / SpEL / Micrometer 依赖。
