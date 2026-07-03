# 后端基础设施使用手册

> 文档版本: V1.0 | 编制日期: 2026-06-30
> 适用版本: ydsz-pmis 1.0.0-SNAPSHOT

## 1. 总体架构

后端采用 Spring Boot 3.3 + Spring Cloud Alibaba 2023 微服务架构，所有服务共享 `ydsz-pmis-common` 模块。

```
┌─────────────────────────────────────────────────────────────┐
│                    ydsz-pmis-common                          │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────────────┐  │
│  │ 统一响应 │ │ 异常处理 │ │ 日志追踪 │ │ 工具/常量/安全 │  │
│  │ R/Page   │ │ BizEx   │ │ TraceId  │ │ Crypto/Sec     │  │
│  └──────────┘ └──────────┘ └──────────┘ └────────────────┘  │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ AOP 切面：@PrePermission / @OperationLog / @RateLimit│   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
            │                │                │
            ▼                ▼                ▼
        业务服务          业务服务          业务服务
     (user/auth/      (project/...)      (workflow/...)
      ...)
            │                │                │
            └────────────────┴────────────────┘
                             │
                       ydsz-pmis-gateway
```

## 2. 统一响应

所有 Controller 返回值统一为 `R<T>`：

```java
@GetMapping("/me")
public R<UserVO> me() {
    return R.ok(userService.current());
}

@GetMapping("/list")
public R<PageResult<UserVO>> list(UserQueryDTO query) {
    return R.ok(userService.page(query));
}
```

**响应结构**：

```json
{
  "code": 0,
  "message": "ok",
  "data": { ... },
  "traceId": "a1b2c3d4e5f6g7h8",
  "timestamp": 1751308800000
}
```

| 字段 | 说明 |
|------|------|
| `code` | 0=成功，其他=失败 |
| `message` | 提示信息 |
| `data` | 业务数据 |
| `traceId` | 链路追踪 ID |
| `timestamp` | 服务器时间戳（毫秒） |

## 3. 错误码体系

错误码段位规划（参考 [BizErrorCode.java](../../ydsz-pmis-backend/ydsz-pmis-common/src/main/java/com/njydsz/pmis/common/api/BizErrorCode.java)）：

| 段位 | 类别 | 示例 |
|------|------|------|
| 0 | 成功 | `OK(0)` |
| 1xxxx | 通用错误 | `BAD_REQUEST(10001)`、`RATE_LIMIT(10301)` |
| 2xxxx | 认证授权 | `UNAUTHORIZED(20001)`、`FORBIDDEN(20101)` |
| 3xxxx | 用户/组织/人员 | `USER_NOT_FOUND(30001)` |
| 4xxxx | 项目/合同/商机 | `PROJECT_NOT_FOUND(40001)` |
| 5xxxx | 财务/成本/收入 | `COST_OVERFLOW(50001)` |
| 6xxxx | 资源/工时/调度 | `RESOURCE_CONFLICT(60001)` |
| 7xxxx | 工作流/审批 | `WORKFLOW_NOT_FOUND(70001)` |
| 8xxxx | 报表/驾驶舱 | `REPORT_GENERATE_FAILED(80001)` |
| 9xxxx | 系统 | `UNKNOWN(99999)` |

**抛出业务异常**：

```java
if (user == null) {
    throw new BizException(BizErrorCode.USER_NOT_FOUND);
}

// 自定义消息
throw new BizException(BizErrorCode.NOT_FOUND, "用户 10086 不存在");
```

## 4. 全局异常处理

`GlobalExceptionHandler` 自动捕获并转换以下异常：

| 异常 | 错误码 | HTTP 状态 |
|------|--------|-----------|
| `BizException` | 自定义 | 200 |
| `MethodArgumentNotValidException` | 10002 | 200 |
| `BindException` | 10002 | 200 |
| `ConstraintViolationException` | 10002 | 200 |
| `MissingServletRequestParameterException` | 10003 | 200 |
| `HttpMessageNotReadableException` | 10001 | 200 |
| `HttpRequestMethodNotSupportedException` | 10004 | 405 |
| `NoHandlerFoundException` | 10101 | 404 |
| `IllegalArgumentException` | 10001 | 200 |
| `Exception` (兜底) | 10201 | 500 |

**注意**：业务异常 HTTP 状态统一返回 200，由 `code` 字段表达错误。

## 5. 链路追踪

`TraceIdFilter` 自动从请求头 `X-Trace-Id` 读取或生成 traceId：

- 请求进入 → 写入 MDC（`traceId` 字段）
- 响应返回 → 输出到响应头 `X-Trace-Id`
- 日志格式：`%d{HH:mm:ss.SSS} %-5level [%X{traceId:-}] [%thread] %logger - %msg%n`
- 所有 `R` 响应自动携带 `traceId`

**网关层透传**：

```
client → [X-Trace-Id: t-001] → gateway → [X-Trace-Id: t-001] → service
```

## 6. 认证与 JWT

### 6.1 Token 生成与解析

`JwtTokenProvider` 负责 Token 的签发与验证：

```java
String token = jwtTokenProvider.generateToken(userId, username, 8 * 3600L);
String refreshToken = jwtTokenProvider.generateRefreshToken(userId, 7 * 24 * 3600L);
```

Token 内置 Claims：`userId`、`username`、`deptId`、`deptName`、`levelCode`、`dataScope`、`roles`、`permissions`、`type`。

### 6.2 网关层

`AuthGlobalFilter` 在网关层：
1. 提取 `Authorization: Bearer xxx` 头
2. 跳过白名单（登录、注册、验证码、健康检查等）
3. 将用户信息写入请求头 `X-User-Id` / `X-Username` / `X-User-Dept-Id` 透传给下游服务

### 6.3 业务服务层

`AuthInterceptor` 在每个微服务 Controller 前解析 Token 并构造 `LoginUser` 放入 `SecurityContext`：

- Token 来源：`Authorization: Bearer xxx` > `X-Access-Token` 头 > `access_token` 查询参数
- 请求结束后 `afterCompletion` 自动清理 `SecurityContext`

## 7. 登录用户上下文

业务层通过 `SecurityContext` 获取当前用户：

```java
Long userId = SecurityContext.getUserId();
String username = SecurityContext.getUsername();
Long deptId = SecurityContext.getDeptId();

// 校验权限
SecurityContext.requirePermission("user:create");

// 校验任一权限
SecurityContext.requireAnyPermission("user:list", "user:create");
```

**`LoginUser` 关键方法**：

| 方法 | 说明 |
|------|------|
| `isSuperAdmin()` | 是否超管（含 `*:*:*`） |
| `hasPermission(perm)` | 是否拥有指定权限 |
| `getDataScope()` | 数据权限范围（ALL/DEPT/SELF/CUSTOM） |

## 8. 权限校验 (@PrePermission)

在 Controller 方法上声明权限要求：

```java
@PrePermission("user:create")
@PostMapping
public R<Void> create(@RequestBody UserCreateDTO dto) { ... }

@PrePermission(value = {"user:list", "user:export"}, mode = PrePermission.Mode.OR)
@GetMapping("/export")
public void export(UserQueryDTO q) { ... }

@PrePermission(value = "user:delete", requireLogin = true)
@DeleteMapping("/{id}")
public R<Void> delete(@PathVariable Long id) { ... }
```

| 属性 | 默认 | 说明 |
|------|------|------|
| `value` | - | 所需权限编码数组 |
| `mode` | `AND` | 校验模式（AND/OR） |
| `requireLogin` | `true` | 是否要求登录 |

## 9. 操作日志 (@OperationLog)

自动记录操作日志（异步落库规划中）：

```java
@OperationLog(module = "用户管理", action = "创建用户", bizType = "USER")
@PostMapping
public R<Void> create(@RequestBody UserCreateDTO dto) { ... }
```

| 属性 | 默认 | 说明 |
|------|------|------|
| `module` | `""` | 模块名 |
| `action` | `""` | 操作名 |
| `bizType` | `""` | 业务类型 |
| `saveParams` | `true` | 是否保存请求参数 |
| `saveResult` | `false` | 是否保存响应结果 |
| `excludeFields` | `[password, ...]` | 脱敏字段 |

记录字段：用户、IP、UA、URL、Method、入参、状态、耗时、错误信息。

## 10. 接口限流 (@RateLimit)

基于 Redis 滑动窗口（INCR + EXPIRE）：

```java
@RateLimit(qps = 5, key = "login:")
@PostMapping("/login")
public R<LoginResultVO> login(@RequestBody LoginDTO dto) { ... }

@RateLimit(qps = 10, key = "captcha:", windowSeconds = 60)
@GetMapping("/captcha")
public R<CaptchaVO> captcha() { ... }
```

| 属性 | 默认 | 说明 |
|------|------|------|
| `qps` | 10 | 时间窗口内允许的请求数 |
| `key` | `""` | 限流维度前缀 |
| `windowSeconds` | 1 | 时间窗口（秒） |
| `message` | `"请求频率超限..."` | 提示信息 |

维度：已登录用户按 `userId`，匿名按 IP。

## 11. 加密工具 (CryptoUtil)

```java
// MD5
String md5 = CryptoUtil.md5("hello");

// 密码加盐（返回 [密文, 盐]）
String[] pair = CryptoUtil.encryptPassword("admin123");
String encrypted = pair[0];
String salt = pair[1];

// 校验
boolean ok = CryptoUtil.verifyPassword("admin123", encrypted, salt);

// Base64
String b64 = CryptoUtil.base64Encode(bytes);
byte[] bytes = CryptoUtil.base64Decode(b64);
```

## 12. 日志规范

`logback-spring.xml` 已配置：

- 控制台 + 异步滚动文件（all.log / error.log）
- 日志格式含 traceId + 线程
- 日志归档：all 30 天、error 90 天
- 框架日志级别：Spring INFO、MyBatis-Plus INFO、Nacos WARN

**Logger 命名规范**：

```java
@Slf4j
@RestController
public class UserController {
    // 默认 logger 名为 com.njydsz.pmis.user.controller.UserController
}
```

**统一 Logger 约定**：

| Logger 名 | 用途 |
|-----------|------|
| `com.njydsz.pmis.*` | 业务代码（DEBUG） |
| `OPERATION_LOG` | 操作日志（待异步落库） |

## 13. 公共常量 (CommonConstants)

```java
CommonConstants.HEADER_TRACE_ID    // "X-Trace-Id"
CommonConstants.HEADER_USER_ID     // "X-User-Id"
CommonConstants.HEADER_USERNAME    // "X-Username"
CommonConstants.HEADER_USER_DEPT   // "X-User-Dept-Id"

CommonConstants.STATUS_ENABLED     // "ENABLED"
CommonConstants.STATUS_DISABLED    // "DISABLED"
CommonConstants.STATUS_DRAFT       // "DRAFT"
CommonConstants.STATUS_ACTIVE      // "ACTIVE"
CommonConstants.STATUS_FINISHED    // "FINISHED"

CommonConstants.NOT_DELETED        // 0
CommonConstants.DELETED            // 1
```

## 14. 单元测试

使用 JUnit 5 + Mockito + AssertJ 编写。

**已覆盖**：

- `R/PageResult/BizErrorCode`：响应与错误码
- `BizException/GlobalExceptionHandler`：异常处理
- `CryptoUtil/TraceIdUtil`：工具类
- `LoginUser/SecurityContext`：安全上下文
- `AuthInterceptor`：JWT 鉴权
- `JwtTokenProvider/AuthServiceImpl`：认证服务
- `PermissionAspect/OperationLogAspect/RateLimiterAspect`：AOP 切面
- `TraceIdFilter`：链路追踪过滤器

**运行测试**：

```bash
mvn -pl ydsz-pmis-common test
mvn -pl ydsz-pmis-iam -am test
```

## 15. 开发规范

### 15.1 业务异常

```java
// 推荐：使用枚举
throw new BizException(BizErrorCode.USER_NOT_FOUND);

// 推荐：自定义消息
throw new BizException(BizErrorCode.NOT_FOUND, "用户 10086 不存在");

// 避免：直接抛 RuntimeException
throw new RuntimeException("xxx"); // 错误，会被当作系统异常
```

### 15.2 Controller

```java
@Tag(name = "用户管理")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    @Operation(summary = "创建用户")
    @PrePermission("user:create")
    @OperationLog(module = "用户管理", action = "创建用户", bizType = "USER")
    @PostMapping
    public R<Long> create(@Valid @RequestBody UserCreateDTO dto) {
        return R.ok(userService.create(dto));
    }
}
```

### 15.3 Service

- 接口与实现分离（`UserService` + `UserServiceImpl`）
- 业务校验抛 `BizException`，不返回 `Result` 风格
- 事务方法 `@Transactional(rollbackFor = Exception.class)`

### 15.4 Entity / DO

- 数据库实体类以 `DO` 结尾（如 `UserAccountDO`）
- VO / DTO / Query 严格分层
- 主键策略：`IdType.ASSIGN_ID`（雪花算法）
- 审计字段：`created_by`、`created_at`、`updated_by`、`updated_at`、`deleted`（逻辑删除）

## 16. 附录：模块清单

> 服务合并重构后共 7 个可部署服务 + 2 个库（common / literule 不独立部署），
> 与 `application.yml` / `helm/values*.yaml` / `docker-compose.apps.yml` / `prometheus.yml` 完全一致。

| # | 模块 | 端口 | 职责 |
|---|------|------|------|
| 1 | ydsz-pmis-gateway | 9000 | API 网关（路由、鉴权透传、CORS） |
| 2 | ydsz-pmis-iam | 9002 | 认证授权 + 用户/权限/部门/资源池（含 Bench，user + auth 合并，包名 com.njydsz.pmis.iam） |
| 3 | ydsz-pmis-workflow | 9004 | 自研工作流引擎（审批流、门径评审、SLA） |
| 4 | ydsz-pmis-project | 9005 | 商机/立项/合同/变更/执行/成本/财务/报表/驾驶舱（project + execution 合并，核心域，包名 com.njydsz.pmis.project） |
| 5 | ydsz-pmis-agent | 9007 | AI 智能体（编排、风险预警、利润预测） |
| 6 | ydsz-pmis-system | 9008 | 文件/配置/审计/通知/消息模板（file + config + audit + notification + message 合并，包名 com.njydsz.pmis.system） |
| 7 | ydsz-pmis-scheduler | 9012 | 分布式任务调度（XXL-JOB 客户端） |
| — | ydsz-pmis-common | — | 公共组件库（响应/异常/工具/常量，不独立部署） |
| — | ydsz-pmis-literule | — | 轻量规则引擎库（表达式/规则链，不独立部署） |

> 周边组件端口保持业界默认：xxl-job-admin 9100、seata 8091/7091、sentinel 8719、
> nacos 8848/9848、minio 9000/9001、postgres 5432、redis 6379、rocketmq 9876/10909/10911/10912、elasticsearch 9200。

## 17. 检查清单

- [x] 统一响应 R/PageResult
- [x] 错误码体系（10 段位）
- [x] 全局异常处理
- [x] 链路追踪（TraceIdFilter + MDC）
- [x] JWT 认证（生成/解析/校验）
- [x] 鉴权拦截器（AuthInterceptor）
- [x] 网关全局过滤器（AuthGlobalFilter）
- [x] 登录上下文（SecurityContext/ThreadLocal）
- [x] 权限校验 AOP（@PrePermission）
- [x] 操作日志 AOP（@OperationLog）
- [x] 接口限流 AOP（@RateLimit）
- [x] 加密工具（CryptoUtil）
- [x] 日志规范（logback-spring.xml）
- [x] 公共常量（CommonConstants）
- [x] 单元测试（112+ 用例）
