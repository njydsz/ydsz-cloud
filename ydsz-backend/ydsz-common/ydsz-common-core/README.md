# ydsz-common-core

PMIS 公共底座核心模块 — 统一响应模型、请求上下文、TraceId、常量与枚举。

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
| `BaseResponse<T>` | 统一 API 响应体（code / msg / data / traceId / timestamp） |
| `PageResponse<T>` | 分页响应体（继承 BaseResponse，含 total / pages / pageNum / pageSize） |
| `ResultCode` | 结果码接口，业务模块自定义错误码需实现此接口 |
| `BaseResultCode` | 标准结果码枚举（A 类用户端错误 / B 类业务异常 / C 类第三方异常） |
| `IResponse<T>` | 响应标记接口 |

### 请求上下文

| 类 | 说明 |
|---|---|
| `RequestContext` | 基于 TransmittableThreadLocal 的请求上下文（traceId / userId / tenantId / 自定义属性） |
| `ContextKey<T>` | 类型安全的上下文 Key 定义，支持编译期类型检查 |
| `RequestContext.CleanupGuard` | try-with-resources 模式的上下文清理守卫 |
| `RequestContext.capture()` | 捕获上下文快照用于异步传播 |
| `RequestContext.wrapCallable()` | 包装 Callable 自动传播上下文 |

### 常量定义

| 类 | 说明 |
|---|---|
| `HeaderConstants` | HTTP 请求头常量（Token / 数据权限 / 链路追踪 / 安全头部） |
| `TokenConstants` | Token 相关常量（标识 / 前缀 / 回调 URL） |
| `SecurityConstants` | 安全常量（密钥属性名 / BCrypt 强度 / CSRF / 安全头部） |
| `ProtocolConstants` | 协议前缀常量（RMI / LDAP / HTTP / HTTPS） |
| `PageConstants` | 分页默认值与参数名 |
| `FilterIgnoreConstant` | 过滤器忽略 URL 模式与服务名称 |

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
| `PageRequest` | 分页请求封装（pageNum / pageSize / orderBy / orderDir + 安全校验） |

### TraceId

| 类 | 说明 |
|---|---|
| `TraceIdSupplier` | TraceId 生成策略接口（@FunctionalInterface） |
| `TraceIdGenerator` | 默认 TraceId 生成器（UUID 去连字符，32 位） |

### 配置

| 类 | 说明 |
|---|---|
| `CoreProperties` | 核心配置属性（分页 + 链路追踪） |
| `CoreAutoConfiguration` | 核心自动配置（总是激活） |
| `TraceAutoConfiguration` | TraceId 自动配置（注册 TraceIdSupplier Bean） |

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
    default-page-size: 10              # 默认每页记录数
    trace:
      enabled: true                    # 链路追踪开关
      header-name: X-Trace-Id          # TraceId 请求头名称
      generate-if-missing: true         # 缺失时自动生成
```

## 依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-core</artifactId>
</dependency>
```
