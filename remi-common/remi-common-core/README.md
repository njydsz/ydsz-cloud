# remi-common-core

> REMI 公共底座核心模块（L1 基础设施层）— 统一响应模型、结果码、请求上下文、链路追踪、分页协议、国际化、Spring Boot 自动配置

`remi-common-core` 是整个 REMI 平台的基石模块，提供最基础且被所有上层模块依赖的核心能力：统一 API 响应封装、RFC 9457 Problem Detail 错误模型、业务结果码定义、请求级上下文传播、多协议链路追踪、分页响应桥接、全局常量、国际化消息资源、Spring Boot 自动配置与 GraalVM native-image 支持。

**当前版本**：`1.8.0+`

---

## 目录

- [快速开始](#快速开始)
- [核心组件](#核心组件)
- [数据结构](#数据结构)
- [RequestContext](#requestcontext)
- [SpanContext / TraceIdGenerator](#spantracecontext)
- [PageResponse](#pageresponse)
- [Header Constants](#header-constants)
- [国际化消息](#国际化消息)
- [Spring Boot 自动配置](#spring-boot-自动配置)
- [GraalVM native-image 支持](#graalvm-native-image-支持)
- [依赖关系](#依赖关系)
- [相关模块](#相关模块)
- [注意事项](#注意事项)

---

## 快速开始

### 1. POM 引入

```xml
<dependency>
    <groupId>com.remisoft</groupId>
    <artifactId>remi-common-core</artifactId>
</dependency>
```

`remi-common-core` 会自动引入以下传递依赖：
- `remi-common-json` — 统一 JSON 引擎（提供 `@JsonInclude`、`@JsonPropertyOrder` 等注解）
- `transmittable-thread-local` — 阿里 TTL，实现线程池上下文传播
- `lombok`（provided 范围）— 编译期代码生成
- `slf4j-api` — 日志门面

### 2. 基础使用

```java
import com.remisoft.common.core.response.BaseResponse;
import com.remisoft.common.core.code.BaseResultCode;

// 成功响应
return BaseResponse.success(user);

// 成功响应（自定义消息）
return BaseResponse.success("查询成功", user);

// 无数据成功响应
return BaseResponse.success();

// 失败响应（使用结果码枚举）
return BaseResponse.error(BaseResultCode.NOT_FOUND);

// 失败响应（自定义消息覆盖默认消息）
return BaseResponse.error(BaseResultCode.VALIDATION_FAILED, "邮箱格式不正确");

// 判断请求是否成功
if (response.isSuccess()) { ... }
```

### 3. RFC 9457 Problem Detail

```java
import com.remisoft.common.core.response.ProblemDetail;

// 简单构造
return ProblemDetail.badRequest("参数错误");

// 自定义构造
ProblemDetail problem = ProblemDetail.ofStatus(429)
    .type("https://docs.remisoft.com/problems/rate-limit")
    .title("请求频率超限")
    .detail("请在 60 秒后重试")
    .instance("/api/users")
    .code("RATE_LIMIT")
    .messageKey("error.RATE_LIMIT")
    .extension("retryAfter", 60)
    .build();
```

### 4. 分页场景

```java
import com.remi.common.core.response.PageResponse;
import com.remisoft.common.core.response.IPageResult;

// 方式 1：从 domain 层 PageResult 桥接
PageResult<UserDO> domainPage = userService.pageQuery(query);
return PageResponse.from(domainPage);

// 方式 2：类型安全桥接
PageResponse<UserVO> resp = PageResponse.fromIPage(domainPage, UserVO.class);

// 方式 3：传统构造
PageResponse<List<User>> resp = PageResponse.success(total, pageNum, pageSize, users);
```

### 5. 请求上下文

```java
import com.remisoft.common.core.context.RequestContext;

// 防御性执行（推荐 —— 自动清理）
RequestContext.runWithCleanup(() -> {
    chain.doFilter(request, response);
});

// 手动设置与清理
RequestContext.setUserId("user-123");
RequestContext.setTenantId("tenant-001");
RequestContext.setTraceId("abc123...");
RequestContext.setLanguage("zh-CN");
RequestContext.setClientIp("192.168.1.1");
RequestContext.setRequestSource("OPEN_API");
RequestContext.setApiVersion("v2");

// 读取当前用户
String userId = RequestContext.getUserId();

// 跨线程快照/恢复
Map<String, String> snapshot = RequestContext.snapshot();
executor.submit(() -> {
    RequestContext.restore(snapshot);
    // 子线程中可读取完整上下文
});

// 清理（请求结束）
RequestContext.clear();
```

### 6. 链路追踪 (SpanContext)

```java
import com.remisoft.common.core.trace.SpanContext;

// 创建根 Span
SpanContext root = SpanContext.newRoot();
// 传递给下游 HTTP 调用
httpRequest.header("traceparent", root.toTraceparent());

// 创建子 Span
SpanContext child = root.newChild();

// 解析上游 W3C traceparent
SpanContext fromUpstream = SpanContext.fromTraceparent(request.getHeader("traceparent"));

// 使用 B3 协议 (Zipkin)
String b3 = root.toB3Single();          // "traceId-spanId-1"
SpanContext fromB3 = SpanContext.fromB3Single(b3);

// SkyWalking 兼容
String sw = root.toSkyWalking();        // "traceId.0.0.1"
```

### 7. 分页参数归一化

```java
import com.remisoft.common.core.constant.PageConstants;

// 归一化页码（<=1 视为第 1 页）
int safePageNum = PageConstants.pageNum(pageNum);

// 归一化页大小（1 ~ MAX_PAGE_SIZE）
int safePageSize = PageConstants.pageSize(pageSize);

// 计算 LIMIT offset
long offset = PageConstants.calcOffset(pageNum, pageSize);
```

---

## 核心组件

| 包 | 类 | 职责 |
|---|---|---|
| `response` | `BaseResponse<T>` | 统一 API 响应封装（code/msg/data/timestamp），使用 `@JsonInclude(NON_NULL)` 控制空值序列化 |
| `response` | `PageResponse<T>` | 分页响应，继承 BaseResponse，扩展 total/pageNum/pageSize/pages 字段；新增 `from(IPageResult)` 桥接 |
| `response` | `ProblemDetail` | RFC 9457 Problem Detail 错误模型，支持 Builder 构造与扩展属性 |
| `response` | `IPageResult` | 分页结果解耦接口，让 domain 层 `PageResult` 可不依赖 core 模块 |
| `code` | `BaseResultCode` | 系统通用结果码枚举（SUCCESS/BAD_REQUEST/NOT_FOUND 等），携带 code/msg/httpStatus 三元组 |
| `context` | `RequestContext` | 请求级上下文（基于 TransmittableThreadLocal，线程池安全）；新增 typed accessor、防御性清理、快照/恢复 |
| `context` | `RequestContextData` | 上下文数据载体（immutable record），支持 withXxx() 派生方法 |
| `trace` | `TraceIdGenerator` | TraceId/SpanId 纯函数式生成器 |
| `trace` | `SpanContext` | Span 上下文四元组（traceId+spanId+traceFlags+traceState），提供 W3C/B3/SkyWalking 协议互转 |
| `constant` | `PageConstants` | 分页常量与归一化工具方法 |
| `constant` | `SystemConstants` | 系统级常量（系统用户 ID、默认租户、默认语言等） |
| `constant.header` | `AuthHeaders` | HTTP 认证/身份 header 常量 |
| `constant.header` | `TraceHeaders` | HTTP 链路追踪 header 常量（含 W3C） |
| `constant.header` | `NetworkHeaders` | HTTP 网络信息 header 常量 |
| `constant.header` | `DataScopeHeaders` | HTTP 数据权限 header 常量 |
| `constant.header` | `ColumnPermissionHeaders` | HTTP 列级权限 header 常量 |
| `config` | `CoreAutoConfiguration` | Spring Boot 自动配置入口，提供 `corePageConfig`、`coreResponseConfig`、`coreMessageSource` 三个 Bean |
| `config` | `CoreProperties` | 配置属性绑定（@ConfigurationProperties("remi.core")） |

---

## 数据结构

### BaseResponse\<T\>

```json
{
  "code": "SUCCESS",
  "msg": "ok",
  "data": {},
  "timestamp": 1722873600000
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | `String` | 业务响应码（成功统一为 `SUCCESS`） |
| `msg` | `String` | 响应消息 |
| `data` | `T` | 业务数据（可为 null，使用 `@JsonInclude(NON_NULL)` 控制序列化） |
| `timestamp` | `Long` | 响应时间戳（毫秒） |

序列化顺序：`code` → `msg` → `data` → `timestamp`（由 `@JsonPropertyOrder` 控制）。

### PageResponse\<T\>

继承 `BaseResponse`，新增分页元数据：

```json
{
  "code": "SUCCESS",
  "msg": "ok",
  "data": [],
  "timestamp": 1722873600000,
  "total": 100,
  "pageNum": 1,
  "pageSize": 20,
  "pages": 5
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `total` | `Long` | 总记录数 |
| `pageNum` | `Long` | 当前页码 |
| `pageSize` | `Long` | 每页大小 |
| `pages` | `Long` | 总页数（由 total 和 pageSize 计算得出） |

序列化顺序：`code` → `msg` → `data` → `timestamp` → `total` → `pageNum` → `pageSize` → `pages`。

### ProblemDetail (RFC 9457)

```json
{
  "type": "https://docs.remisoft.com/problems/bad-request",
  "title": "Bad Request",
  "status": 400,
  "detail": "参数错误",
  "instance": "/api/users",
  "code": "BAD_REQUEST",
  "messageKey": "error.BAD_REQUEST",
  "timestamp": "2026-08-05T21:00:00"
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `type` | `URI` | 问题类型 URI（应指向可读文档） |
| `title` | `String` | 简短、人类可读的问题摘要 |
| `status` | `int` | HTTP 状态码 |
| `detail` | `String` | 针对本次问题的特定解释 |
| `instance` | `URI` | 标识问题发生位置的 URI |
| `code` | `String` | 应用层错误码（扩展字段） |
| `messageKey` | `String` | i18n 消息键（扩展字段） |
| `timestamp` | `LocalDateTime` | 问题发生时间戳 |
| `extensions` | `Map<String, Object>` | RFC 9457 标准扩展属性 |

### BaseResultCode

系统预定义的结果码枚举，每个枚举值包含三元组 `(code, msg, httpStatus)`：

| 枚举值 | code | msg | HTTP Status | 分类 |
|---|---|---|---|---|
| `SUCCESS` | A00000 | ok | 200 | 成功 |
| `BAD_REQUEST` | A10001 | 请求参数错误 | 400 | A1-通用/参数 |
| `VALIDATION_FAILED` | A10002 | 参数校验失败 | 400 | A1-通用/参数 |
| `NOT_FOUND` | A10101 | 资源不存在 | 404 | A1-通用/参数 |
| `RATE_LIMIT` | A10301 | 请求频率超限 | 429 | A1-通用/参数 |
| `INTERNAL_ERROR` | B10201 | 系统内部错误 | 500 | B1-系统 |
| `SERVICE_UNAVAILABLE` | B10202 | 服务暂不可用 | 503 | B1-系统 |
| `UNAUTHORIZED` | A20001 | 未登录 | 401 | A2-认证 |
| `TOKEN_EXPIRED` | A20002 | Token 已过期 | 401 | A2-认证 |
| `FORBIDDEN` | A20101 | 无权限访问 | 403 | A2-认证 |
| `DATA_SCOPE_FORBIDDEN` | A20102 | 数据权限不足 | 403 | A2-认证 |
| `UNKNOWN` | C99999 | 未知错误 | 500 | C9-未知 |

**码段约定**：
- **A 开头**：系统级码（参数校验、认证授权等）
- **B 开头**：业务级码（内部错误、服务不可用等）
- **C 开头**：未知/兜底错误

**自定义结果码**：业务模块应自行实现结果码枚举，不应直接修改 `BaseResultCode`。

---

## RequestContext

基于 `TransmittableThreadLocal`（阿里 TTL）实现的请求级上下文容器，**新增 typed accessor 与防御性封装**。

### 可用上下文项

| 项 | 类型 | 说明 |
|---|---|---|
| `userId` | `String` | 当前登录用户 ID |
| `tenantId` | `String` | 当前租户 ID |
| `traceId` | `String` | 请求链路追踪 ID |
| `requestId` | `String` | 单次入口请求 ID |
| `language` | `String` | 用户语言偏好（如 zh-CN、en-US） |
| `tenantIsolationSkipped` | `boolean` | 是否跳过租户隔离 |
| `clientIp` | `String` | 客户端 IP 地址 |
| `requestSource` | `String` | 请求来源（INTERNAL / OPEN_API / WEB_HOOK） |
| `apiVersion` | `String` | API 版本号 |

### 防御性清理（推荐）

```java
// 自动在 finally 中清理
RequestContext.runWithCleanup(() -> {
    chain.doFilter(request, response);
});

// 或返回结果的场景
return RequestContext.supplyWithCleanup(() -> processRequest(req));
```

### 快照与恢复（跨线程）

```java
// 创建快照
Map<String, String> snapshot = RequestContext.snapshot();

// 子线程中恢复
executor.submit(() -> {
    RequestContext.restore(snapshot);
    // 子线程可读取完整上下文
});
```

### MDC 桥接

```java
// 请求入口：桥接上下文到 SLF4J MDC
RequestContext.bridgeToMdc();
// 此后日志中自动包含 tenantId/userId/traceId/requestId
```

---

## SpanTraceContext

### SpanContext 与多协议互转

`SpanContext` 是基于 record 的 immutable 数据结构，封装 W3C Trace Context 四元组：

```java
// 1. 创建根 Span
SpanContext root = SpanContext.newRoot();                           // 已采样
SpanContext notSampled = SpanContext.newRoot(false);                 // 未采样

// 2. 生成子 Span
SpanContext child = root.newChild();                                 // 同 traceId，新 spanId

// 3. 序列化 / 反序列化（W3C）
String traceparent = root.toTraceparent();                           // "00-{traceId}-{spanId}-01"
SpanContext fromW3C = SpanContext.fromTraceparent(traceparent);

// 4. 序列化 / 反序列化（B3 / Zipkin）
String b3 = root.toB3Single();                                       // "traceId-spanId-1"
SpanContext fromB3 = SpanContext.fromB3Single(b3);

// 5. SkyWalking 兼容
String sw = root.toSkyWalking();                                    // "traceId.0.0.1"

// 6. 状态管理
SpanContext extended = root.withTraceStateEntry("tdm", "trace:abc");
String tracestate = extended.toTracestate();                         // "tdm=trace:abc"
```

### TraceIdGenerator 纯函数式 API

```java
String traceId = TraceIdGenerator.generateTraceId();     // 32 位十六进制
String spanId  = TraceIdGenerator.generateSpanId();      // 16 位十六进制
String traceparent = TraceIdGenerator.newTraceparent();  // W3C 采样
String notSampled = TraceIdGenerator.newTraceparent(false); // W3C 未采样
```

> **性能说明**：TraceIdGenerator 已回退为纯函数式实现。现代 JVM (ZGC/Shenandoah) 的 TLAB 分配在 32 字节以内对象上几乎零成本，无需 ThreadLocal 缓冲池。

---

## PageResponse

### from(IPageResult) 工厂方法

新增 `PageResponse.from(IPageResult)` 方法，一行代码完成 domain → API 层的分页桥接：

```java
// domain 层（remi-common-domain / 业务模块）
public record PageResult<T>(List<T> records, long total, int pageNum, int pageSize) implements IPageResult {}

// API 层
PageResult<UserDO> domainPage = userService.pageQuery(query);
PageResponse<UserDO> resp = PageResponse.from(domainPage);
```

`IPageResult` 接口定义在 core 模块，无需反向依赖。

---

## Header Constants

HTTP header 常量按功能域拆分至 `com.remisoft.common.core.constant.header` 包：

| 类 | 功能域 |
|---|---|
| `AuthHeaders` | 认证/身份 (X-Access-Token、X-Idempotency-Key 等) |
| `TraceHeaders` | 链路追踪 (X-Trace-Id、W3C Trace Context、MDC 键) |
| `NetworkHeaders` | 网络信息 (X-Request-Source、X-Forwarded-For、User-Agent 等) |
| `DataScopeHeaders` | 数据权限 (X-Tenant-Id、X-Data-Scope 等) |
| `ColumnPermissionHeaders` | 列级权限 (X-Visible-Columns、X-Col-Permission-Sign 等) |

> **向后兼容**：原 `HeaderConstants` 类仍可使用，所有常量已标记为 `@deprecated` 并转发到对应细粒度类。新代码应直接引用具体类。

---

## 国际化消息

`remi-common-core` 仅维护 core 模块**自身使用**的结果码消息资源，位于 `src/main/resources/i18n/core/`：

- `i18n/core/messages.properties` — 默认（英文）消息
- `i18n/core/messages_zh_CN.properties` — 简体中文消息
- `i18n/core/` — 新增 `problem.*` key，支持 RFC 9457 Problem Detail 的 `type`/`title` 字段

### 消息 key 覆盖范围

| 分类 | Key 前缀 | 说明 |
|---|---|---|
| 通用响应 | `response.*` | 操作成功/失败通用消息 |
| 结果码 | `error.{ENUM_NAME}` | 与 `BaseResultCode` 枚举一一对应（仅 SUCCESS/BAD_REQUEST/NOT_FOUND 等） |
| RFC 9457 | `problem.{ENUM_NAME}` | 问题类型 URI 和 title（type / title） |
| RFC 9457 (中文) | `problem.{ENUM_NAME}.type/.title` | 中文对应翻译 |

> **设计原则**：业务模块错误码（DB_*、CACHE_*、MQ_*、PASSWORD_* 等）已下放至对应模块的 i18n 文件。

---

## Spring Boot 自动配置

### 注册方式

通过 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 文件注册：

```
com.remisoft.common.core.config.CoreAutoConfiguration
```

### 注入的 Bean

| Bean 名称 | 类型 | 说明 |
|---|---|---|
| `corePageConfig` | `CorePageConfig` | 分页参数配置（可注入使用 defaultPageNum / defaultPageSize / maxPageSize） |
| `coreResponseConfig` | `CoreResponseConfig` | 响应全局配置（includeTimestamp / rfc9457Enabled / rfc9457TypeUriPrefix） |
| `coreMessageSource` | `MessageSource` | Core 模块独立 i18n（basenames=`classpath:i18n/core/messages`，不与业务 i18n 冲突） |

### 可配置属性（`remi.core.*`）

| 属性 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `remi.core.enabled` | `Boolean` | `true` | 是否启用 Core 模块自动配置 |
| `remi.core.page.default-page-size` | `Integer` | `20` | 运行时默认每页记录数 |
| `remi.core.page.default-page-num` | `Integer` | `1` | 运行时默认页码 |
| `remi.core.page.max-page-size` | `Integer` | `1000` | 运行时最大每页记录数 |
| `remi.core.response.include-timestamp` | `Boolean` | `true` | 是否在响应体中自动包含 timestamp |
| `remi.core.response.rfc9457.enabled` | `Boolean` | `false` | 是否启用 RFC 9457 响应格式 |
| `remi.core.response.rfc9457.type-uri-prefix` | `String` | `https://docs.remisoft.com/problems` | RFC 9457 type URI 前缀 |
| `remi.core.context.mdc.enabled` | `Boolean` | `true` | 是否启用 RequestContext 与 MDC 的自动桥接 |
| `remi.core.context.mdc.tenant-id-key` | `String` | `tenantId` | MDC 中 tenantId 的键名 |
| `remi.core.context.mdc.user-id-key` | `String` | `userId` | MDC 中 userId 的键名 |
| `remi.core.context.mdc.trace-id-key` | `String` | `traceId` | MDC 中 traceId 的键名 |

### 配置示例

```yaml
remi:
  core:
    page:
      max-page-size: 500
      default-page-size: 25
    response:
      rfc9457:
        enabled: true
        type-uri-prefix: https://api.remisoft.com/problems
```

---

## GraalVM native-image 支持

`remi-common-core` 提供了 native-image 反射配置。配置文件位于：

```
META-INF/native-image/com.remisoft/remi-common-core/native-image.properties
```

### 反射配置覆盖

| 类 | 说明 |
|---|---|
| `BaseResponse` | 统一响应体（code/msg/data/timestamp）|
| `PageResponse` | 分页响应体（total/pageNum/pageSize/pages）|
| `ProblemDetail` | RFC 9457 Problem Details 模型 |
| `CoreProperties` | 核心配置属性类 |
| `CoreAutoConfiguration` | Spring Boot 自动配置入口 |
| `CoreAutoConfiguration$CorePageConfig` | 分页配置 record |
| `CoreAutoConfiguration$CoreResponseConfig` | 响应配置 record |

### 资源模式

| 模式 | 说明 |
|---|---|
| `META-INF/.*\.properties$` | 加载所有 properties 资源 |
| `META-INF/.*\.yml$` | 加载所有 yml 资源 |
| `META-INF/.*\.json$` | 加载所有 json 资源 |
| `META-INF/spring/.*$` | 加载 Spring 配置资源 |
| `i18n/.*$` | 加载国际化资源 |

---

## 依赖关系

### POM 依赖

```xml
<parent>
    <groupId>com.remisoft</groupId>
    <artifactId>remi-common</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</parent>

<dependencies>
    <!-- Lombok（provided 范围）-->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <scope>provided</scope>
    </dependency>
    <!-- SLF4J API -->
    <dependency>
        <groupId>org.slf4j</groupId>
        <artifactId>slf4j-api</artifactId>
    </dependency>
    <!-- 统一 JSON 引擎（提供 @JsonInclude/@JsonPropertyOrder 等注解）-->
    <dependency>
        <groupId>com.remisoft</groupId>
        <artifactId>remi-common-json</artifactId>
    </dependency>
    <!-- TransmittableThreadLocal -->
    <dependency>
        <groupId>com.alibaba</groupId>
        <artifactId>transmittable-thread-local</artifactId>
    </dependency>
    <!-- Spring Boot Test（test 范围）-->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

### 依赖说明

| 依赖 | 范围 | 用途 |
|---|---|---|
| `lombok` | provided | `@Data`、`@SuperBuilder`、`@NoArgsConstructor`、`@AllArgsConstructor` |
| `slf4j-api` | compile | 日志门面 |
| `remi-common-json` | compile | 提供 `@JsonInclude`、`@JsonPropertyOrder` 注解 |
| `transmittable-thread-local` | compile | 阿里 TTL，实现线程池上下文自动传播 |
| `spring-boot-starter-test` | test | 单元测试框架 |

---

## 相关模块

| 能力 | 所在模块 |
|---|---|
| 多租户隔离 | `remi-common-tenent` |
| Web 层过滤/拦截器 | `remi-common-base` / `remi-common-web` |
| 敏感数据脱敏 | `remi-common-safe` |
| 认证授权 | `remi-common-auth` |
| 数据权限拦截 | `remi-common-jdbc` |
| JSON 序列化 | `remi-common-json` |
| 国际化扩展 | `remi-common-app` |
| 分页领域模型 | `remi-common-domain` |

---

## 注意事项

1. **RequestContext 必须显式清理**：推荐使用 `RequestContext.runWithCleanup()` / `supplyWithCleanup()` / `callWithCleanup()`，它们会在 finally 中自动调用 `clear()`，防止内存泄漏和上下文污染。

2. **业务模块自定义结果码**：不应直接修改 `BaseResultCode`，应在各自模块定义独立枚举，遵循码段约定（A=系统级、B=业务级、C=未知）。

3. **数据权限上下文**：列级权限/行级权限依赖 `DataScopeHeaders` 和 `ColumnPermissionHeaders` 中定义的常量。新代码推荐引用这些细粒度类而非聚合的 `HeaderConstants`。

4. **序列化注解来源**：`BaseResponse` 和 `PageResponse` 上的 `@JsonInclude` 和 `@JsonPropertyOrder` 来自 `remi-common-json` 模块，非 Jackson 原生注解。引入 `remi-common-core` 时会自动传递依赖 `remi-common-json`。

5. **native-image 兼容性**：使用 GraalVM native-image 编译时，确保 `native-image.properties` 中配置的反射白名单覆盖了所有运行时需反射访问的类。

6. **SpanContext 是 immutable**：使用 `withXxx()` 方法派生新对象而非修改原对象，天然线程安全。

7. **i18n 资源物理隔离**：core 模块的 i18n 文件位于 `i18n/core/`，与业务模块 classpath 根的 `messages.properties` 互不冲突。如需扩展 core 的 i18n，在 `CoreAutoConfiguration.coreMessageSource()` 中添加新的 basenames 即可。
