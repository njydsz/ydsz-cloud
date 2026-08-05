# remi-common-core

> REMI 公共底座核心模块（L1 基础设施层）— 统一响应模型、结果码、请求上下文、TraceId 生成、常量定义、国际化消息、Spring Boot 自动配置

`remi-common-core` 是整个 REMI 平台的基石模块，提供最基础且被所有上层模块依赖的核心能力：统一 API 响应封装、业务结果码定义、请求上下文传播、链路追踪 ID 生成、全局常量、国际化消息资源、Spring Boot 自动配置与 GraalVM native-image 支持。

**当前版本**：`1.0.0-SNAPSHOT`

---

## 目录

- [快速开始](#快速开始)
- [核心组件](#核心组件)
- [数据结构](#数据结构)
- [RequestContext](#requestcontext)
- [TraceIdGenerator](#traceidgenerator)
- [PageConstants](#pageconstants)
- [HeaderConstants](#headerconstants)
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

### 3. 分页场景

```java
import com.remisoft.common.core.response.PageResponse;

// 标准分页响应
PageResponse<List<User>> resp = PageResponse.success(total, pageNum, pageSize, users);

// 错误响应
PageResponse<List<User>> err = PageResponse.error(
    BaseResultCode.INTERNAL_ERROR.getCode(),
    BaseResultCode.INTERNAL_ERROR.getMsg()
);
```

### 4. 请求上下文

```java
import com.remisoft.common.core.context.RequestContext;

// 设置当前用户
RequestContext.setUserId("user-123");
RequestContext.setTenantId("tenant-001");
RequestContext.setTraceId("abc123...");
RequestContext.setLanguage("zh-CN");

// 读取当前用户
String userId = RequestContext.getUserId();

// 请求结束清理（必须在 finally 或拦截器中调用）
RequestContext.clear();
```

### 5. TraceId 生成

```java
import com.remisoft.common.core.trace.TraceIdGenerator;

// 生成 32 位十六进制 TraceId
String traceId = TraceIdGenerator.generateTraceId();

// 生成 16 位十六进制 SpanId
String spanId = TraceIdGenerator.generateSpanId();

// 生成 W3C Trace Context traceparent header 值
String traceparent = TraceIdGenerator.traceparentHeader(traceId, spanId);
// 输出格式: 00-{32位 traceId}-{16 位 spanId}-01

// 一步生成新的 traceId+spanId 并组合为 traceparent
String traceparent = TraceIdGenerator.newTraceparent();
```

### 6. 分页参数归一化

```java
import com.remisoft.common.core.constant.PageConstants;

// 归一化页码（<=1 视为第 1 页）
int safePageNum = PageConstants.pageNum(pageNum);

// 归一化页大小（1 ~ MAX_PAGE_SIZE=5000）
int safePageSize = PageConstants.pageSize(pageSize);

// 计算 LIMIT offset
long offset = PageConstants.calcOffset(pageNum, pageSize);
```

---

## 核心组件

| 包 | 类 | 职责 |
|---|---|---|
| `response` | `BaseResponse<T>` | 统一 API 响应封装（code/msg/data/timestamp），使用 `@JsonInclude(NON_NULL)` 控制空值序列化 |
| `response` | `PageResponse<T>` | 分页响应，继承 BaseResponse，扩展 total/pageNum/pageSize/pages 字段 |
| `code` | `BaseResultCode` | 系统通用结果码枚举（SUCCESS/BAD_REQUEST/NOT_FOUND 等），携带 code/msg/httpStatus 三元组 |
| `context` | `RequestContext` | 请求级上下文（基于 TransmittableThreadLocal，线程池安全） |
| `trace` | `TraceIdGenerator` | TraceId/SpanId 生成，支持 W3C Trace Context 格式 |
| `constant` | `HeaderConstants` | 全局 HTTP 请求头常量定义（认证、数据权限、列权限、链路追踪、网络信息） |
| `constant` | `PageConstants` | 分页常量与归一化工具方法 |
| `constant` | `SystemConstants` | 系统级常量（系统用户 ID、默认租户、默认语言等） |

---

## 数据结构

### BaseResponse\<T\>

```json
{
  "code": "A00000",
  "msg": "ok",
  "data": {},
  "timestamp": 1722873600000
}
```

| 字段 | 类型 | 说明 |
|---|---|
| `code` | `String` | 业务响应码（成功统一为 `A00000`） |
| `msg` | `String` | 响应消息 |
| `data` | `T` | 业务数据（可为 null，使用 `@JsonInclude(NON_NULL)` 控制序列化） |
| `timestamp` | `Long` | 响应时间戳（毫秒） |

序列化顺序：`code` → `msg` → `data` → `timestamp`（由 `@JsonPropertyOrder` 控制）。

### PageResponse\<T\>

继承 `BaseResponse`，新增分页元数据：

```json
{
  "code": "A00000",
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
|---|---|
| `total` | `Long` | 总记录数 |
| `pageNum` | `Long` | 当前页码 |
| `pageSize` | `Long` | 每页大小 |
| `pages` | `Long` | 总页数（由 total 和 pageSize 计算得出） |

序列化顺序：`code` → `msg` → `data` → `timestamp` → `total` → `pageNum` → `pageSize` → `pages`。

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

**国际化支持**：每个枚举值可通过 `getMessageKey()` 获取 i18n key（格式为 `error.{ENUM_NAME}`），配合 `messages.properties` 使用。

---

## RequestContext

基于 `TransmittableThreadLocal`（阿里 TTL）实现的请求级上下文容器，确保线程池异步场景下上下文自动传播。

### 可用上下文项

| 项 | 类型 | 说明 |
|---|---|---|
| `userId` | `String` | 当前登录用户 ID |
| `tenantId` | `String` | 当前租户 ID |
| `traceId` | `String` | 请求链路追踪 ID |
| `requestId` | `String` | 单次入口请求 ID |
| `language` | `String` | 用户语言偏好（如 zh-CN、en-US） |
| `tenantIsolationSkipped` | `boolean` | 是否跳过租户隔离 |

### 实现细节

- 使用 `TransmittableThreadLocal<Context>` 存储，内部 Context 为私有静态内部类
- `ctl()` 方法采用懒初始化模式：首次 get 时创建 Context 实例
- `clear()` 调用 `CTL.remove()` 彻底清理 ThreadLocal 值

### 使用建议

- **写入时机**：在登录鉴权拦截器/过滤器中设置
- **清理时机**：在响应处理结束阶段统一调用 `RequestContext.clear()`
- **安全提示**：`RequestContext.clear()` 必须调用，否则会造成内存泄漏和上下文污染

---

## TraceIdGenerator

高性能 TraceId 生成器，基于 `ThreadLocalRandom` + ThreadLocal 字节缓冲（避免频繁内存分配）。

### 核心方法

| 方法 | 输出长度 | 说明 |
|---|---|
| `generateTraceId()` | 32 位十六进制 | 生成随机 TraceId |
| `generateSpanId()` | 16 位十六进制 | 生成随机 SpanId |
| `traceparentHeader(traceId, spanId)` | — | 组合为 W3C traceparent 格式 |
| `newTraceparent()` | — | 一步生成新 traceId+spanId 并组合 |

### 实现细节

- `TRACE_BUF`：16 字节（128 位随机数 → 32 位十六进制）
- `SPAN_BUF`：8 字节（64 位随机数 → 16 位十六进制）
- 使用 `java.util.HexFormat`（Java 17+）进行十六进制编码，相比传统 `String.format` 或手写循环性能更优

### W3C Trace Context 格式

```java
// traceparent 格式
00-{32位traceId}-{16位spanId}-01
// 示例
00-1a2b3c4d5e6f7890abcdef1234567890-1a2b3c4d5e6f7890-01
```

### 对应 HTTP Header

| 常量名 | Header 名称 | 说明 |
|---|---|---|
| `HeaderConstants.TRACE_ID_HEADER` | `X-Trace-Id` | 自定义 TraceId 透传 Header |
| `HeaderConstants.W3C_TRACEPARENT` | `traceparent` | W3C 标准 traceparent |
| `HeaderConstants.W3C_TRACESTATE` | `tracestate` | W3C 标准 tracestate |
| `HeaderConstants.MDC_TRACE_ID_KEY` | — | MDC key：`traceId` |
| `HeaderConstants.MDC_REQUEST_ID_KEY` | — | MDC key：`requestId` |

---

## PageConstants

分页参数常量与归一化工具：

```java
// 常量
PageConstants.DEFAULT_PAGE_NUM  // 1
PageConstants.DEFAULT_PAGE_SIZE   // 20
PageConstants.MAX_PAGE_SIZE       // 5000

// 工具方法
PageConstants.pageNum(pageNum)     // 归一化页码
PageConstants.pageSize(pageSize)   // 归一化页大小（1~5000）
PageConstants.calcOffset(pageNum, pageSize) // 计算 LIMIT offset
```

### 配置覆盖

分页参数可通过 `application.yml` 运行时覆盖（需启用 `remi.core` 自动配置）：

```yaml
remi:
  core:
    max-page-size: 2000       # ｜ 运行时最大每页记录数（1-5000）
    default-page-size: 10       # 运行时默认每页记录数
```

---

## HeaderConstants

全局 HTTP 请求头常量定义类，按功能域分为以下组：

### 认证/身份

| 常量 | Header 名称 | 说明 |
|---|---|---|
| `X_ACCESS_TOKEN` | `X-Access-Token` | 登录访问令牌 |
| `X_USER_LANGUAGE` | `X-User-Language` | 用户系统语言 |
| `X_DISTINCT_ID` | `X-Distinct-Id` | 用户设备唯一标识 |
| `X_IDENTITY_TYPE` | `X-Identity-Type` | 身份类型（公司用户/访客/remi用户） |
| `X_SERVICE_TYPE` | `X-Service-Type` | 请求来源服务类型 |
| `IDEMPOTENCY_KEY` | `X-Idempotency-Key` | 幂等键（参考 Stripe API 设计） |

### 数据权限

| 常量 | Header 名称 | 说明 |
|---|---|
| `X_DATA_SCOPE` | `X-Data-Scope` | 数据权限范围类型（tenant/group/company/dept/user/project/region） |
| `X_TENANT_ID` | `X-Tenant-Id` | 租户 ID |
| `X_UNIQUE_ID` | `X-Unique-Id` | 当前登录用户唯一标识 |
| `X_COMPANY_IDS` | `X-Company-Ids` | 公司 ID 集合（CSV） |
| `X_DEPT_IDS` | `X-Dept-Ids` | 部门 ID 集合（CSV） |
| `X_PROJECT_IDS` | `X-Project-Ids` | 项目 ID 集合（CSV） |
| `X_REGION_IDS` | `X-Region-Ids` | 区域 ID 集合（CSV） |
| `X_CUSTOM_SQL_CONDITION` | `X-Custom-Sql-Condition` | 自定义 SQL 条件标识键 |

### 列级权限

| 常量 | Header 名称 | 说明 |
|---|---|
| `X_VISIBLE_COLUMNS` | `X-Visible-Columns` | 表级可见列规则 |
| `X_EDITABLE_COLUMNS` | `X-Editable-Columns` | 表级可编辑列规则 |
| `X_COL_PERMISSION_SIGN` | `X-Col-Permission-Sign` | 列权限数据签名（HMAC-SHA256） |

列规则格式：`table:col1,col2;table2:col3,col4`

### 链路追踪

| 常量 | Header 名称 | 说明 |
|---|---|---|
| `TRACE_ID_HEADER` | `X-Trace-Id` | 请求追踪 ID |
| `W3C_TRACEPARENT` | `traceparent` | W3C Trace Context |
| `W3C_TRACESTATE` | `tracestate` | W3C Trace Context 供应商扩展 |
| `MDC_TRACE_ID_KEY` | — | SLF4J MDC key：`traceId` |
| `MDC_REQUEST_ID_KEY` | — | SLF4J MDC key：`requestId` |

### 网络信息

| 常量 | Header 名称 | 说明 |
|---|---|---|
| `X_REQUEST_SOURCE` | `X-Request-Source` | 请求来源渠道（PC Web/H5/APP/小程序） |
| `X_FORWARDED_FOR` | `X-Forwarded-For` | 客户端真实 IP（单值透传） |

---

## 国际化消息

`remi-common-core` 提供了基础的国际化消息资源文件，位于 `src/main/resources/i18n/`：

- `messages.properties` — 默认（英文）消息
- `messages_zh_CN.properties` — 简体中文消息

### 消息 key 覆盖范围

| 分类 | Key 前缀 | 说明 |
|---|---|---|
| 通用响应 | `response.*` | 操作成功/失败通用消息 |
| 结果码 | `error.{ENUM_NAME}` | 与 `BaseResultCode` 枚举一一对应 |
| 数据库 | `error.DB_*` | 数据库相关错误消息 |
| 认证授权 | `error.A2*` | 认证/授权相关错误消息 |
| 第三方 | `error.THIRD_PARTY_*` | 第三方服务错误消息 |
| 系统状态 | `error.SYSTEM_*` | 系统维护/功能禁用等 |
| 基础设施 | `error.CACHE_*`, `error.MQ_*` | 缓存/消息队列错误消息 |

> 注：`messages_zh_CN.properties` 的完整内容请直接查看资源文件。

---

## Spring Boot 自动配置

### 注册方式

通过 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 文件注册：

```
com.remisoft.common.core.config.CoreAutoConfiguration
```

> 注意：当前源代码中 `CoreAutoConfiguration` 及对应的 `CoreProperties` 配置类尚未实装。上述配置注册入口文件已就位，实装后即可生效。

### 可配置属性（`remi.core.*`）

| 属性 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `remi.core.enabled` | `Boolean` | `true` | 是否启用 Core 模块自动配置（i18n 解析器、分页配置同步、Micrometer 指标注册） |
| `remi.core.max-page-size` | `Integer` | `1000` | 运行时最大每页记录数上限（1-5000） |
| `remi.core.default-page-size` | `Integer` | `20` | 运行时默认每页记录数 |
| `remi.core.tenant-mdc-filter-order` | `Integer` | `Integer.MIN_VALUE + 100` | 租户 MDC 过滤器注册顺序 |

---

## GraalVM native-image 支持

`remi-common-core` 提供了 native-image 配置，支持 GraalVM 原生编译。配置文件位于：

```
META-INF/native-image/com.remisoft/remi-common-core/native-image.properties```

### 反射配置覆盖

| 类 | 说明 |
|---|---|
| `BaseResponse` | 统一响应体（含无参构造、带参构造、code/msg/data/traceId/timestamp 字段）|
| `PageResponse` | 分页响应体（含无参构造、全参构造、total/pageNum/pageSize/pages 字段）|
| `ProblemDetail` | RFC 9457 Problem Details 模型（保留，当前源代码中未实装） |
| `CoreProperties` | 核心配置属性类 |
| `StringConcatFactory` | 兼容 native-image 下的字符串拼接 |

### 资源模式

| 模式 | 说明 |
|---|---|
| `META-INF/.*\.properties$` | 加载所有 properties 资源 |
| `META-INF/.*\.yml$` | 加载所有 yml 资源 |
| `META-INF/.*\.json$` | 加载所有 json 资源 |
| `META-INF/spring/.*$` | 加载 Spring 配置资源 |

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
| `slf4j-api` | compile | 日志门面（预留，当前模块无日志输出） |
| `remi-common-json` | compile | 提供 `@JsonInclude`、`@JsonPropertyOrder` 注解，统一 JSON 序列化行为 |
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

---

## 注意事项

1. **RequestContext 必须显式清理**：请求结束必须调用 `clear()`，建议通过过滤器的 finally 块统一管理，否则会造成内存泄漏和上下文污染。

2. **业务模块自定义结果码**：不应直接修改 `BaseResultCode`，应在各自模块定义独立枚举，遵循码段约定（A=系统级、B=业务级、C=未知）。

3. **数据权限上下文**：列级权限/行级权限依赖 `HeaderConstants` 中定义的 Header，需确保 Feign 透传和网关配置同步。

4. **序列化注解来源**：`BaseResponse` 和 `PageResponse` 上的 `@JsonInclude` 和 `@JsonPropertyOrder` 来自 `remi-common-json` 模块，非 Jackson 原生注解。引入 `remi-common-core` 时会自动传递依赖 `remi-common-json`。

5. **native-image 兼容性**：使用 GraalVM native-image 编译时，确保 `native-image.properties` 中配置的反射白名单覆盖了所有运行时需反射访问的类。

6. **TraceIdGenerator 线程安全**：使用 `ThreadLocal` 缓冲字节数组 + `ThreadLocalRandom` 生成随机数，无需外部同步，完全线程安全。