# ydsz-common-core

YDSZ 公共底座核心模块 — 统一响应模型、请求上下文、TraceId、常量与枚举。最小化依赖，不包含 Spring AOP、Micrometer、SpEL 等框架能力。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L1 基础设施层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 被 common 所有子模块及全部 10 个部署单元依赖 |
| **构建顺序** | 最先编译 |

## 核心能力

### 统一响应模型

| 类 | 说明 |
|---|---|
| `BaseResponse<T>` | 统一 API 响应体（code / msg / data / traceId / timestamp）+ 函数式 API（orElse / orElseThrow / map / ifSuccess / ifFailed） |
| `PageResponse<T>` | 分页响应体（继承 BaseResponse，含 total / pages / pageNum / pageSize） |
| `ResultCode` | 结果码接口，业务模块自定义错误码需实现此接口 |
| `BaseResultCode` | 标准结果码枚举（A 类用户端错误 / B 类系统业务异常 / C 类第三方异常） |
| `IResponse<T>` | 响应标记接口 |

**业务响应码与 HTTP 状态码区分**：
- `BaseResponse.code` 是**业务响应码**（String 类型），如 `"A00000"` 表示成功
- `BaseResultCode.getHttpStatusCode()` 返回对应的 **HTTP 状态码**（int 类型），如 `200` / `400` / `500`
- 前端判断成功应检查 `resp.code === "A00000"`，而非 HTTP 状态码 `200`

### 请求上下文

| 类 | 说明 |
|---|---|
| `RequestContext` | 基于 TransmittableThreadLocal 的请求上下文（traceId / userId / tenantId / 自定义属性） |
| `ContextKey<T>` | 类型安全的上下文 Key 定义，支持编译期类型检查 |
| `RequestContext.CleanupGuard` | try-with-resources 模式的上下文清理守卫 |
| `RequestContext.snapshot()` | 捕获上下文快照 |
| `RequestContext.restore()` | 从快照恢复上下文 |
| `RequestContext.wrapCallable()` | 包装 Callable 自动传播上下文到异步线程 |
| `RequestContext.wrapRunnable()` | 包装 Runnable 自动传播上下文到异步线程 |

### TraceId

| 类 | 说明 |
|---|---|
| `TraceIdSupplier` | TraceId 生成策略接口（@FunctionalInterface） |
| `TraceIdGenerator` | 默认 TraceId 生成器（UUID 去连字符，32 位） |
| `SnowflakeTraceIdSupplier` | Snowflake 有序 TraceId 生成器（CAS 无锁，时间排序友好） |

### 常量定义

| 类 | 说明 |
|---|---|
| `HeaderConstants` | HTTP 请求头常量（Token / 数据权限 / 链路追踪 / 安全头部） |
| `TokenConstants` | Token 相关常量（标识 / 前缀 / 回调 URL） |
| `SecurityConstants` | 安全常量（密钥属性名 / BCrypt 强度 / CSRF / 安全头部） |
| `ProtocolConstants` | 协议前缀常量（RMI / LDAP / HTTP / HTTPS） |
| `PageConstants` | 分页默认值与参数名 |
| `FilterIgnoreConstant` | 过滤器忽略 URL 模式与服务名称 |
| `TraceConstants` | 链路追踪常量 |
| `CacheConstants` | 缓存名称常量 |

### 枚举

| 类 | 说明 |
|---|---|
| `TypeEnum<T>` | 通用枚举接口，提供 `getCode()` / `getDesc()` 及工具方法 |
| `DataScopeType` | 数据权限范围类型（租户 / 集团 / 公司 / 部门 / 用户 / 项目 / 区域 / 自定义） |
| `IdentityType` | 身份类型（ydsz 账号 / 公司账号 / 游客） |
| `ServiceType` | 服务类型（Web 管理端 / App 移动端） |
| `YesOrNo` | 是/否枚举（数据库布尔值表示） |

### 请求模型

| 类 | 说明 |
|---|---|
| `IRequest` | 请求标记接口（extends Serializable） |
| `BaseRequest` | 基础请求对象（Lombok @SuperBuilder） |
| `PageRequest` | 分页请求封装（pageNum / pageSize / orderBy + 安全校验 + 排序白名单校验） |

### 健康检查

| 类 | 说明 |
|---|---|
| `CoreHealthIndicator` | Core 模块健康指标（报告 TraceId 策略、分页配置） |

## 自动配置

| 配置类 | 激活条件 |
|---|---|
| `CoreAutoConfiguration` | `ydsz.core.enabled=true` 时激活（默认启用） |
| `TraceAutoConfiguration` | `ydsz.core.trace.enabled=true` 时激活（默认启用） |

## 配置项

```yaml
ydsz:
  core:
    enabled: true                      # 模块总开关
    max-page-size: 1000                # 最大每页记录数上限
    default-page-size: 20              # 默认每页记录数
    trace:
      enabled: true                    # 链路追踪开关
      generate-if-missing: true         # 缺失时自动生成
      id-type: uuid                    # uuid（默认）或 snowflake（有序）
    tenant-mdc-filter:
      enabled: true                    # 租户 MDC 过滤器开关
```

## 依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-core</artifactId>
</dependency>
```
