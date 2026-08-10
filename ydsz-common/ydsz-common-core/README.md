# ydsz-common-core

> YDSZ 公共底座核心模块（L1 基础设施层）— 统一响应模型、结果码、请求上下文、链路追踪、分页协议、国际化、Spring Boot 自动配置

`ydsz-common-core` 是整个 YDSZ 平台的基石模块，提供最基础且被所有上层模块依赖的核心能力：统一 API 响应封装、业务结果码定义、请求级上下文传播、多协议链路追踪、分页响应封装、全局常量、国际化消息资源、Spring Boot 自动配置与 GraalVM native-image 支持。

**当前版本**：`1.0.0-SNAPSHOT`

---

## 目录

- [快速开始](#快速开始)
- [核心组件](#核心组件)
- [数据结构](#数据结构)
- [RequestContext](#requestcontext)
- [链路追踪](#链路追踪)
- [BaseResponse (分页场景)](#pageresponse)
- [Header Constants](#header-constants)
- [国际化消息](#国际化消息)
- [Spring Boot 自动配置](#spring-boot-自动配置)
- [GraalVM native-image 支持](#graalvm-native-image-支持)
- [依赖关系](#依赖关系)
- [相关模块](#相关模块)
- [注意事项](#注意事项)
- [Roadmap (Planned)](#roadmap-planned)

---

## 快速开始

### 1. POM 引入

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-core</artifactId>
</dependency>
```

`ydsz-common-core` 会自动引入以下传递依赖：
- `ydsz-common-json` — 统一 JSON 引擎（提供 `@JsonInclude`、`@JsonPropertyOrder` 等注解）
- `transmittable-thread-local` — 阿里 TTL，实现线程池上下文传播
- `lombok`（provided 范围）— 编译期代码生成
- `slf4j-api` — 日志门面

### 2. 基础使用

```java
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.code.BaseResultCode;

// 成功响应
return BaseResponse.success(user);

// 成功响应（自定义消息）
return BaseResponse.success("查询成功", user);

// 无数据成功响应
return BaseResponse.success();

// 失败响应（使用结果码枚举，自动走 i18n）
return BaseResponse.error(BaseResultCode.NOT_FOUND);

// 失败响应（自定义消息覆盖）
return BaseResponse.error(BaseResultCode.VALIDATION_FAILED, "邮箱格式不正确");

// 判断请求是否成功
if (response.isSuccess()) { ... }
```

### 3. 分页场景

```java
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;

// 标准分页响应（推荐：使用专用分页信封 PageResponse<T>）
PageResponse<List<User>> resp = PageResponse.success(total, pageNum, pageSize, users);

// 等价地，也可使用 BaseResponse.successPage(...) 工厂（返回 BaseResponse<T>）
BaseResponse<List<User>> resp2 = BaseResponse.successPage(total, pageNum, pageSize, users);

// 无数据分页响应
PageResponse<List<User>> empty = PageResponse.empty(pageNum, pageSize);

// 判断请求是否成功
if (resp.isSuccess()) { ... }
```

> **提示**：`PageResponse<T>` 是 `BaseResponse<T>` 的子类型，额外提供 `getPages()` 等便捷方法；
> 分页元信息（total / pageNum / pageSize）收口于该类型，`BaseResponse` 仍保留兼容字段。
> `getExtensions()` 返回<b>不可变视图</b>，如需写入扩展字段请使用 `putExtension(key, value)`。

### 4. 请求上下文

```java
import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.core.trace.TraceIdGenerator;

// 推荐写法：防御性执行（自动清理上下文与 MDC）
RequestContext.runWithCleanup(() -> {
    chain.doFilter(request, response);
});

// 手动设置类型化属性
RequestContext.setUserId("user-123");
RequestContext.setTenantId("tenant-001");
RequestContext.setTraceId(TraceIdGenerator.generateTraceId());
RequestContext.setLanguage("zh-CN");
RequestContext.setClientIp("192.168.1.1");
RequestContext.setRequestSource("OPEN_API");
RequestContext.setApiVersion("v2");
RequestContext.setTenantIsolationSkipped(true);

// 读取
String userId = RequestContext.getUserId();

// 跨线程快照 / 恢复
Map<String, String> snapshot = RequestContext.snapshot();
executor.submit(() -> {
    RequestContext.restore(snapshot);
    // 子线程可读取完整上下文
});

// 清理
RequestContext.clear();

// 桥接上下文到 SLF4J MDC
RequestContext.bridgeToMdc();   // 写入 tenantId/userId/traceId/requestId
RequestContext.clearMdc();      // 清理
```

### 5. 链路追踪

```java
import com.njydsz.common.core.trace.TraceIdGenerator;
import com.njydsz.common.core.trace.TraceIdPropagation;

// 生成 TraceId（32 位十六进制）
String traceId = TraceIdGenerator.generateTraceId();

// 生成 SpanId（16 位十六进制）
String spanId = TraceIdGenerator.generateSpanId();

// 生成 W3C traceparent
String traceparent = TraceIdGenerator.traceparentHeader();
// "00-a1b2c3d4e5f67890abcdef1234567890-e5f67890abcdef12-01"

// 跨服务传播：获取当前 traceId 并注入请求头
Map<String, String> headers = TraceIdPropagation.traceHeaders();
headers.forEach(httpRequest.getHeaders()::set);

// 缺失时自动生成
Map<String, String> headers2 = TraceIdPropagation.traceHeadersOrCreate();
```

### 6. 分页参数归一化

```java
import com.njydsz.common.core.constant.PageConstants;

// 归一化页码
int safePageNum = PageConstants.normalizePageNum(pageNum);

// 归一化页大小（运行时受 max-page-size 约束）
int safePageSize = PageConstants.normalizePageSize(pageSize);

// 计算 LIMIT offset
long offset = PageConstants.calcOffset(pageNum, pageSize);
```

---

## 核心组件

| 包 | 类 | 职责 |
|---|---|---|
| `response` | `BaseResponse<T>` | 统一 API 响应封装（code/msg/data/timestamp/traceId/extensions），使用 `@JsonInclude(NON_NULL)` 控制空值序列化 |
| `response` | ~~`PageResponse<T>`~~ | （已弃用）分页响应，推荐使用 `BaseResponse` 并将分页元数据放入 extensions |
| `response` | `IResponse<T>` | 统一响应接口，定义 code/msg/data/success/traceId/timestamp 标准契约 |
| `code` | `BaseResultCode` | 系统通用结果码枚举，携带 code/msg/httpStatus 三元组 |
| `code` | `ResultCode` | 结果码接口，业务模块自定义错误码应实现此接口 |
| `context` | `RequestContext` | 请求级上下文（基于 TransmittableThreadLocal，线程池安全）；提供 typed accessor、防御性清理、快照/恢复、MDC 桥接 |
| `context` | `ContextKey<T>` | 类型安全上下文键工厂，编译期保证类型安全 |
| `trace` | `TraceIdGenerator` | TraceId/SpanId 生成器，使用 ThreadLocalRandom + HexFormat + ThreadLocal 缓冲区 |
| `trace` | `TraceIdPropagation` | TraceId 传播工具类，基于 MDC 读取当前 traceId，生成 X-Trace-Id 和 traceparent header |
| `constant` | `PageConstants` | 分页常量 + 运行时值覆盖 + 归一化工具方法 |
| `constant` | `SystemConstants` | 系统级常量（系统用户 ID、默认租户、默认语言等） |
| `constant` | `TokenConstants` | 令牌相关常量（Authorization 等，由 auth/util 模块消费） |
| `constant` | `HeaderConstants` | 统一 HTTP 请求头常量（认证/身份、数据权限、列级权限、链路追踪、网络信息） |
| `config` | `CoreAutoConfiguration` | Spring Boot 自动配置入口，注册 springMessageResolver、pageConstantsInitializer |
| `config` | `CoreProperties` | 配置属性绑定（`@ConfigurationProperties("ydsz.core")`） |
| `config` | `SpringMessageResolver` | Spring MessageSource 适配器，将 i18n 解析绑定到 BaseResponse |

---

## 数据结构

### BaseResponse\<T\>

```json
{
  "code": "A00000",
  "msg": "操作成功",
  "data": {},
  "traceId": "a1b2c3d4e5f67890abcdef1234567890",
  "timestamp": 1722873600000,
  "extensions": {"requestId": "req-123"}
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | `String` | 业务响应码（成功统一为 `A00000`） |
| `msg` | `String` | 响应消息（支持 i18n） |
| `data` | `T` | 业务数据（可为 null） |
| `traceId` | `String` | 链路追踪 ID（从 MDC 自动提取） |
| `timestamp` | `Long` | 响应时间戳（毫秒） |
| `extensions` | `Map<String, Object>` | 扩展字段（null 时不序列化） |

序列化顺序：`code` → `msg` → `data` → `traceId` → `timestamp` → `extensions`（由 `@JsonPropertyOrder` 控制）。

### ~~PageResponse\<T\>~~（已弃用）

> 已标记 `@Deprecated`，推荐将分页元数据放入 `BaseResponse.extensions` 中替代。

~~继承 `BaseResponse`，新增分页元数据：~~

```json
{
  "code": "A00000",
  "msg": "操作成功",
  "data": [],
  "traceId": "a1b2...",
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

### IResponse\<T\>

统一响应接口，定义所有响应类必须遵循的标准契约：

| 方法 | 返回值 | 说明 |
|---|---|---|
| `getCode()` | `String` | 获取响应码 |
| `getMsg()` | `String` | 获取响应消息 |
| `getData()` | `T` | 获取响应数据 |
| `isSuccess()` | `boolean` | 判断是否成功 |
| `getTraceId()` | `String` | 获取链路追踪 ID（default: null） |
| `getTimestamp()` | `Long` | 获取时间戳（default: null） |

### ResultCode

结果码接口，业务模块自定义错误码应优先实现 `ExceptionCode` 接口：

| 方法 | 返回值 | 说明 |
|---|---|---|
| `getCode()` | `String` | 结果码字符串 |
| `getKey()` | `String` | 国际化消息 key（default: "core." + code） |
| `getMsg()` | `String` | 结果消息描述 |

> HTTP 状态码（`getHttpStatus`）等异常下沉语义定义在 `ExceptionCode` 子接口中。

### BaseResultCode

系统预定义的结果码枚举，每个枚举值包含二元组 `(code, msg)`，共 17 个协议级常量。

**码段规划**：
- **A 开头**（A1xxxx/A2xxxx）：系统级码（参数校验、认证授权等）
- **B 开头**（B1xxxx/B2xxxx）：业务级码（内部错误、系统状态等）
- **C 开头**（C1xxxx/C9xxxx）：第三方服务异常与未知/兜底错误

| 枚举值 | code | msg | HTTP Status | 分类 |
|---|---|---|---|---|
| `SUCCESS` | A00000 | ok | 200 | 成功 |
| `BAD_REQUEST` | A10001 | 请求参数错误 | 400 | A1-通用/参数 |
| `VALIDATION_FAILED` | A10002 | 参数校验失败 | 400 | A1-通用/参数 |
| `MISSING_PARAMETER` | A10003 | 缺少参数 | 400 | A1-通用/参数 |
| `METHOD_NOT_ALLOWED` | A10004 | 请求方法不允许 | 405 | A1-通用/参数 |
| `UNSUPPORTED_MEDIA_TYPE` | A10005 | 不支持的媒体类型 | 400 | A1-通用/参数 |
| `NOT_FOUND` | A10101 | 资源不存在 | 404 | A1-通用/资源 |
| `DUPLICATE_KEY` | A10102 | 资源已存在 | 409 | A1-通用/资源 |
| `BIZ_ERROR` | A10103 | 业务规则校验失败 | 400 | A1-通用/资源 |
| `INTERNAL_ERROR` | B10201 | 系统内部错误 | 500 | B1-系统 |
| `SERVICE_UNAVAILABLE` | B10202 | 服务暂不可用 | 503 | B1-系统 |
| `REQUEST_TIMEOUT` | A10203 | 请求超时 | 408 | A1-请求语义 |
| `DB_DUPLICATE_KEY` | C10401 | 数据唯一性冲突 | 409 | C1-数据库 |
| `DB_CONSTRAINT_VIOLATION` | C10402 | 数据约束冲突 | 400 | C1-数据库 |
| `DB_DATA_INTEGRITY` | C10403 | 数据完整性错误 | 400 | C1-数据库 |
| `DB_QUERY_TIMEOUT` | C10404 | 数据库查询超时 | 503 | C1-数据库 |
| `DB_CONNECTION_FAILED` | C10405 | 数据库连接失败 | 503 | C1-数据库 |
| `DB_LOCK_CONTENTION` | C10406 | 数据库锁冲突 | 409 | C1-数据库 |
| `RESOURCE_LOCKED` | A10501 | 资源锁冲突 | 409 | A1-资源冲突 |
| `RESOURCE_CONFLICT` | A10502 | 资源冲突 | 409 | A1-资源冲突 |
| `INVALID_RANGE` | A10601 | 请求范围无效 | 400 | A1-请求语义 |
| `PAYLOAD_TOO_LARGE` | A10602 | 请求体过大 | 400 | A1-请求语义 |
| `TOO_MANY_REQUESTS` | A10603 | 请求过多 | 429 | A1-限流 |
| `SYSTEM_MAINTENANCE` | B20001 | 系统维护中 | 503 | B2-系统状态 |
| `FEATURE_DISABLED` | B20002 | 功能已禁用 | 409 | B2-系统状态 |
| `CIRCUIT_BREAKER_OPEN` | B20003 | 熔断器已开启，请稍后重试 | 500 | B2-系统状态 |
| `THIRD_PARTY_SERVICE_ERROR` | C10501 | 第三方服务异常 | 500 | C1-第三方 |
| `THIRD_PARTY_TIMEOUT` | C10502 | 第三方服务调用超时 | 503 | C1-第三方 |
| `THIRD_PARTY_RATE_LIMITED` | C10503 | 第三方服务限流 | 503 | C1-第三方 |
| `CACHE_OPERATION_FAILED` | C10601 | 缓存操作失败 | 500 | C1-缓存 |
| `MQ_PUBLISH_FAILED` | C10701 | 消息发送失败 | 500 | C1-消息队列 |
| `MQ_CONSUME_FAILED` | C10702 | 消息消费失败 | 500 | C1-消息队列 |
| `UNAUTHORIZED` | A20001 | 未登录 | 401 | A2-认证 |
| `TOKEN_EXPIRED` | A20002 | Token 已过期 | 401 | A2-认证 |
| `TOKEN_INVALID` | A20003 | Token 无效 | 401 | A2-认证 |
| `FORBIDDEN` | A20101 | 无权限访问 | 403 | A2-授权 |
| `DATA_SCOPE_FORBIDDEN` | A20102 | 数据权限不足 | 403 | A2-授权 |
| `PASSWORD_WEAK` | A20103 | 密码强度不足 | 400 | A2-授权 |
| `PASSWORD_EXPIRED` | A20104 | 密码已过期，请修改 | 401 | A2-授权 |
| `PASSWORD_REUSED` | A20105 | 不能使用最近使用过的密码 | 400 | A2-授权 |
| `MFA_REQUIRED` | A20108 | 需要双因素认证 | 401 | A2-授权 |
| `MFA_INVALID` | A20109 | 双因素认证码无效 | 401 | A2-授权 |
| `ACCOUNT_LOCKED` | A20110 | 账号已锁定 | 423 | A2-授权 |
| `SESSION_KICKED` | A20111 | 账号已在其他设备登录 | 401 | A2-授权 |
| `UNKNOWN` | C99999 | 未知错误 | 500 | C9-未知 |

**自定义结果码**：业务模块应实现 `ExceptionCode` 接口（继承自 `ResultCode`，新增 `getKey()` 供 i18n 查找），不应直接修改 `BaseResultCode`。

---

## RequestContext

基于 `TransmittableThreadLocal`（阿里 TTL）实现的请求级上下文容器，支持线程池场景下的自动上下文传递。

### 可用上下文项

| 项 | 类型 | 说明 |
|---|---|---|
| `userId` | `String` | 当前登录用户 ID |
| `tenantId` | `String` | 当前租户 ID |
| `traceId` | `String` | 请求链路追踪 ID（与 MDC traceId 键同值） |
| `requestId` | `String` | 单次入口请求 ID |
| `language` | `String` | 用户语言偏好（如 zh-CN、en-US） |
| `tenantIsolationSkipped` | `boolean` | 是否跳过租户隔离 |
| `clientIp` | `String` | 客户端 IP 地址 |
| `requestSource` | `String` | 请求来源（INTERNAL / OPEN_API / WEB_HOOK 等） |
| `apiVersion` | `String` | API 版本号（用于灰度分流 / API 生命周期管理） |

### 自定义上下文项（字符串键）

对于内置键之外的扩展上下文，请使用字符串键直接读写：

```java
import static com.njydsz.common.core.context.BizContextKeys.KEY_AUTH_INFO;

// 写入（键名使用 BizContextKeys 常量保证来源统一）
RequestContext.put(KEY_AUTH_INFO, authInfo);

// 读取（显式强转）
AuthInfo info = (AuthInfo) RequestContext.get(KEY_AUTH_INFO);
```

### 防御性清理（推荐）

```java
// 自动在 finally 中清理上下文与 MDC
RequestContext.runWithCleanup(() -> {
    chain.doFilter(request, response);
});

// 返回值的场景
return RequestContext.supplyWithCleanup(() -> processRequest(req));

// 允许受检异常
return RequestContext.callWithCleanup(() -> readFromFile(path));

// 带 TTL 泄漏检测的 CleanupGuard
try (RequestContext.CleanupGuard guard =
        RequestContext.newCleanupGuard(Duration.ofSeconds(30))) {
    // ... 业务逻辑
} // 若超过 30 秒，输出 WARN 日志
```

### 快照与恢复（跨线程）

```java
// 当前线程快照
Map<String, String> snapshot = RequestContext.snapshot();

// 子线程中恢复
executor.submit(() -> {
    RequestContext.restore(snapshot);
    // 子线程可读取完整上下文
});

Map<String, Object> diagnostic = RequestContext.dump();    // 诊断快照（不可变）
Map<String, Object> view = RequestContext.view();           // 实时只读视图（零拷贝）
```

### MDC 桥接

```java
// 请求入口：桥接上下文到 SLF4J MDC
RequestContext.bridgeToMdc();
// 此后日志 %X{tenantId} / %X{userId} / %X{traceId} / %X{requestId} 自动生效

// 请求结束：清理 MDC 条目（runWithCleanup 会自动调用）
RequestContext.clearMdc();
```

---

## 链路追踪

### TraceIdGenerator

使用 `ThreadLocalRandom`（无锁、线程本地伪随机数）+ `HexFormat` + `ThreadLocal` 缓冲区重用，生成 32 位十六进制 TraceId：

```java
// 生成 TraceId（16 bytes → 32 位十六进制）
String traceId = TraceIdGenerator.generateTraceId();

// 生成 SpanId（8 bytes → 16 位十六进制，W3C 标准）
String spanId = TraceIdGenerator.generateSpanId();

// 生成完整 W3C traceparent
String traceparent = TraceIdGenerator.traceparentHeader();
// "00-{traceId}-{spanId}-01"

// 使用已有 traceId 构建（透传上游 traceId）
String traceparent2 = TraceIdGenerator.traceparentHeader(incomingTraceId, localSpanId);
```

> **性能说明**：使用 ThreadLocal 缓冲区重用 byte 数组，避免每次生成时分配新数组，减少 GC 压力。

### TraceIdPropagation

基于 MDC 读取当前 traceId，生成 HTTP 传播请求头：

```java
// 获取当前 MDC 中的 traceId，生成传播 header
// 若 MDC 中无 traceId，返回空 Map（调用方决定是否兜底）
Map<String, String> headers = TraceIdPropagation.traceHeaders();
headers.forEach(request.getHeaders()::set);
// 包含 X-Trace-Id 和 traceparent 两个 header

// 缺失时自动生成 traceId
Map<String, String> headers2 = TraceIdPropagation.traceHeadersOrCreate();

// 直接获取当前 traceId
String traceId = TraceIdPropagation.currentTraceId();

// 获取当前 traceId（缺失时自动生成并写入 MDC）
String traceId2 = TraceIdPropagation.currentTraceIdOrCreate();
```

---

## PageResponse

> **已弃用**：`PageResponse` 已标记为 `@Deprecated`。推荐使用 `BaseResponse` 并将分页元数据（total、pageNum、pageSize、pages）放入 `extensions` 中。
> 下一版本将移除 `PageResponse`，请尽快迁移至 `BaseResponse`。

（以下为兼容性保留的 API 参考，不推荐在新代码中使用。）

### 静态工厂方法

```java
// 标准分页
@SuppressWarnings("deprecation")
PageResponse<List<User>> resp = PageResponse.success(total, pageNum, pageSize, users);

// 空分页（total=0, defaultPageSize, null data）
@SuppressWarnings("deprecation")
PageResponse<List<User>> empty = PageResponse.empty();
```

---

## Header Constants

`HeaderConstants` 是单一的 HTTP 请求头常量类，定义项目自定义的所有 header 名称：

### 认证 / 身份

| 常量 | 值 | 说明 |
|---|---|---|
| `X_ACCESS_TOKEN` | `X-Access-Token` | 登录访问令牌 |
| `X_USER_LANGUAGE` | `X-User-Language` | 用户系统语言 |
| `X_DISTINCT_ID` | `X-Distinct-Id` | 用户设备唯一标识 |
| `X_IDENTITY_TYPE` | `X-Identity-Type` | 身份类型 |
| `X_SERVICE_TYPE` | `X-Service-Type` | 请求服务类型 |
| `IDEMPOTENCY_KEY` | `X-Idempotency-Key` | 幂等键 |

### 数据权限

| 常量 | 值 | 说明 |
|---|---|---|
| `X_DATA_SCOPE` | `X-Data-Scope` | 数据权限范围类型 |
| `X_TENANT_ID` | `X-Tenant-Id` | 租户ID |
| `X_UNIQUE_ID` | `X-Unique-Id` | 当前登录用户唯一标识 |
| `X_COMPANY_IDS` | `X-Company-Ids` | 公司ID集合（CSV） |
| `X_DEPT_IDS` | `X-Dept-Ids` | 部门ID集合（CSV） |
| `X_PROJECT_IDS` | `X-Project-Ids` | 项目ID集合（CSV） |
| `X_REGION_IDS` | `X-Region-Ids` | 区域ID集合（CSV） |
| `X_CUSTOM_SQL_CONDITION` | `X-Custom-Sql-Condition` | 自定义数据权限标识 |

### 列级权限

| 常量 | 值 | 说明 |
|---|---|---|
| `X_VISIBLE_COLUMNS` | `X-Visible-Columns` | 表级可见列规则 |
| `X_EDITABLE_COLUMNS` | `X-Editable-Columns` | 表级可编辑列规则 |
| `X_COL_PERMISSION_SIGN` | `X-Col-Permission-Sign` | 列权限签名 |

### 链路追踪

| 常量 | 值 | 说明 |
|---|---|---|
| `TRACE_ID_HEADER` | `X-Trace-Id` | 请求追踪 ID |
| `MDC_TRACE_ID_KEY` | `traceId` | MDC 中的 traceId 键名 |
| `W3C_TRACEPARENT` | `traceparent` | W3C traceparent header |
| `W3C_TRACESTATE` | `tracestate` | W3C tracestate header |

### 网络信息

| 常量 | 值 | 说明 |
|---|---|---|
| `X_REQUEST_SOURCE` | `X-Request-Source` | 请求来源 |
| `X_FORWARDED_FOR` | `X-Forwarded-For` | 客户端真实 IP |

---

## 国际化消息

`ydsz-common-core` 仅维护 core 模块**自身使用**的结果码消息资源，位于 `src/main/resources/i18n/core/`：

- `i18n/core/messages.properties` — 默认（英文）消息
- `i18n/core/messages_zh_CN.properties` — 简体中文消息

### 消息 key 覆盖范围

| 分类 | Key 前缀 | 说明 |
|---|---|---|
| 通用响应 | `response.*` | 操作成功/失败通用消息 |
| 结果码 | `error.{ENUM_NAME}` | 与 `BaseResultCode` 枚举一一对应 |

> **设计原则**：业务模块错误码建议在各模块的 `i18n/messages.properties` 中自定义 Key，不在 core 模块维护。

---

## Spring Boot 自动配置

### 注册方式

通过 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 文件注册：

```
com.njydsz.common.core.config.CoreAutoConfiguration
```

### 注入的 Bean

| Bean 名称 | 类型 | 说明 |
|---|---|---|
| `springMessageResolver` | `SpringMessageResolver` | Spring i18n 解析器，绑定到 `BaseResponse`。需 classpath 含 `MessageSource` Bean 时生效 |
| `pageConstantsInitializer` | `SmartInitializingSingleton` | 启动时将 `CoreProperties` 注入 `PageConstants` |

> **健康检查**：core 模块不内置健康指示器，统一由 `ydsz-common-base` 的 `CoreHealthIndicator`（`@ConditionalOnMissingBean` 注册，版本号从构建 MANIFEST 读取）提供，避免重复定义。

### 可配置属性（`ydsz.core.*`）

| 属性 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `ydsz.core.enabled` | `Boolean` | `true` | 是否启用 ydsz-core 自动配置 |
| `ydsz.core.max-page-size` | `Integer` | `1000` | 运行时最大每页记录数（1-5000） |
| `ydsz.core.default-page-size` | `Integer` | `20` | 运行时默认每页记录数（1-5000） |
| `ydsz.core.tenant-mdc-filter-order` | `Integer` | `HIGHEST_PRECEDENCE + 100` | 租户 MDC 过滤器优先级 |

### 配置示例

```yaml
ydsz:
  core:
    max-page-size: 500
    default-page-size: 25
    tenant-mdc-filter-order: 200
```

### JSR-303 交叉校验

`CoreProperties` 声明 `@AssertTrue` 确保 `default-page-size <= max-page-size`，非法配置将导致应用启动失败（fail-fast）。

---

## GraalVM native-image 支持

`ydsz-common-core` 提供了 native-image 反射配置。配置文件位于：

```
META-INF/native-image/com.njydsz/ydsz-common-core/native-image.properties
```

### 反射配置

| 类 | 说明 |
|---|---|
| `BaseResponse` | 统一响应体（含构造函数、所有字段） |
| ~~`PageResponse`~~ | （已弃用）分页响应体 |
| `IResponse` | 响应接口 |
| `CoreProperties` | 核心配置属性类 |
| `CoreAutoConfiguration` | Spring Boot 自动配置入口 |
| `CoreAutoConfiguration$PageConstantsInitializer` | 分页配置初始化器 |
| `PageConstants` | 分页常量类 |
| `BaseResultCode` | 结果码枚举（全部字段、方法） |
| `ContextKey` | 类型安全上下文键 |
| `RequestSnapshot` | 不可变请求快照 |
| `PageResponse` | 分页响应信封 |
| `Response` | 统一响应门面 |

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
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common</artifactId>
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
    <!-- 统一 JSON 引擎 -->
    <dependency>
        <groupId>com.njydsz</groupId>
        <artifactId>ydsz-common-json</artifactId>
    </dependency>
    <!-- TransmittableThreadLocal -->
    <dependency>
        <groupId>com.alibaba</groupId>
        <artifactId>transmittable-thread-local</artifactId>
    </dependency>
    <!-- Test -->
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
| `slf4j-api` | compile | 日志门面（MDC、LoggerFactory） |
| `ydsz-common-json` | compile | 提供 `@JsonInclude`、`@JsonPropertyOrder` 注解 |
| `transmittable-thread-local` | compile | 阿里 TTL，实现线程池上下文自动传播 |
| `spring-boot-actuator` | optional | 接入方如需暴露健康端点（由 `ydsz-common-base` 提供）可引入，core 本身不依赖 |
| `spring-boot-starter-test` | test | 单元测试框架 |

---

## 相关模块

| 能力 | 所在模块 |
|---|---|
| Web 层过滤/拦截器 | `ydsz-common-base` / `ydsz-common-web` |
| 认证授权 | `ydsz-common-auth` |
| 数据权限拦截 | `ydsz-common-jdbc` |
| JSON 序列化 | `ydsz-common-json` |
| 国际化扩展 | `ydsz-common-app` |

---

## 注意事项

1. **RequestContext 必须显式清理**：推荐使用 `RequestContext.runWithCleanup()` / `supplyWithCleanup()` / `callWithCleanup()`，它们会在 finally 中自动调用 `clear()` 和 `clearMdc()`，防止内存泄漏和上下文污染。

2. **业务模块自定义结果码**：不应直接修改 `BaseResultCode`，应在各自模块定义独立枚举并实现 `ExceptionCode` 接口（或直接继承 `ResultCode` 用于非 i18n 的纯协议层），遵循码段约定（A=系统级、B=业务级、C=第三方/未知）。

3. **HeaderConstants 是单一常量类**：项目中所有自定义 HTTP header 常量统一在 `HeaderConstants` 类中定义，按功能域分段注释。

4. **序列化注解来源**：`BaseResponse`（及已弃用的 `PageResponse`）上的 `@JsonInclude` 和 `@JsonPropertyOrder` 来自 `ydsz-common-json` 模块，非 Jackson 原生注解。引入 `ydsz-common-core` 时会自动传递依赖 `ydsz-common-json`。

5. **native-image 兼容性**：使用 GraalVM native-image 编译时，确保 `native-image.properties` 中配置的反射白名单覆盖了所有运行时需反射访问的类。

6. **i18n 资源物理隔离**：core 模块的 i18n 文件位于 `i18n/core/`，与业务模块 classpath 根的 `messages.properties` 互不冲突。业务模块应自行扩展 `messages.properties` 覆盖所需 error code 的 i18n Key。

7. **PageConstants 一次性注入**：`PageConstants.init()` 采用一次性设置语义，重复调用会抛出 `IllegalStateException`；运行时通过 `ydsz.core.max-page-size` 和 `ydsz.core.default-page-size` 控制分页参数。

8. **BaseResponse MessageResolver 一次性设置**：`BaseResponse.setResolverIfAbsent()` 仅首次调用生效，由 `CoreAutoConfiguration` 在应用启动时注入 `SpringMessageResolver`。

---

## Roadmap (Planned)

以下特性尚未在当前版本实现，处于规划阶段：

- **RFC 9457/9457 ProblemDetail**：已采用 Spring 标准 `org.springframework.http.ProblemDetail`（RFC 7807/9457）作为异常响应格式，ydsz-common-exception 模块提供完整支持，无需额外实现。
- **SpanContext 完整版**：新增基于 record 的 immutable Span 上下文四元组，提供 W3C/B3/SkyWalking 协议互转能力。当前仅有 TraceIdGenerator 提供 traceId/spanId 生成与 traceparent 构建。
- **IPageResult 桥接**（规划中，随 `PageResponse` 弃用而优先级降低）：新增 `IPageResult` 接口，让 domain 层 `PageResponse` 可实现该接口，配合 BaseResponse 分页元数据简化分页桥接。
