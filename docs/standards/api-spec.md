<!--
  ===========================================================================
  文件名: api-spec.md
  路径:   docs/standards/api-spec.md
  作用:   定义 PMIS 对外 REST API 的命名、版本、错误码、幂等、签名等契约
  适用:   所有后端 Controller / OpenAPI 文档
  对标:   Google API Design Guide / 阿里《Java 开发手册》/ 华为云 API 规范
  ===========================================================================
-->

# API 接口规范

> 文档版本: V1.0 | 编制日期: 2026-06-30 | 最近更新: 2026-07-03
> 标准: RESTful API + JSON over HTTP
> 协议基线: HTTPS（生产强制）/ HTTP（仅本地开发）

> 📌 **本规范是 PMIS 后端 14 个微服务接口的强制契约**，所有 Controller / OpenAPI / 前端 API 客户端均需遵守。
> 完整错误码定义见 [`BizErrorCode.java`](file:///d:/Code/ydsz/ydsz-pmis/ydsz-pmis-backend/ydsz-pmis-common/src/main/java/com/njydsz/pmis/common/api/BizErrorCode.java)。

## 1. URL 命名

> 设计原则：URL 表"资源（Resource）"，HTTP 方法表"动作（Action）"。

### 1.1 基础规则

- 资源使用名词复数：`/projects`、`/users`
- 层级不超过 3 层：`/projects/{id}/tasks`
- 路径小写，连字符分隔多词：`/project-templates`
- 路径中只允许出现资源 ID：`/projects/{id}`，禁止 `/projects?name=xxx`
- 过滤条件放在 query：`/projects?status=active&page=1`
- 操作型端点用动词子资源：`/projects/{id}/approve`、`/users/{id}/reset-password`
- 业务前缀统一 `/api/v1/{module}/{resource}`，module 取值见 [backend-infrastructure.md §16](file:///d:/Code/ydsz/ydsz-pmis/docs/standards/backend-infrastructure.md#16-附录模块清单)

### 1.2 HTTP 方法语义

| 方法 | 用途 | 幂等 | 安全 | 请求体 | 响应体 |
|------|------|------|------|--------|--------|
| GET | 查询 | 是 | 是 | ❌ | 数据 |
| POST | 创建/非幂等操作 | 否 | 否 | ✅ | 创建的资源 |
| PUT | 完整更新（覆盖式） | 是 | 否 | ✅ | 更新后的资源 |
| PATCH | 部分更新 | 否 | 否 | ✅ | 更新后的资源 |
| DELETE | 删除 | 是 | 否 | ❌ | 空 / 204 |

> 💡 GET 必须是**安全且幂等**的，禁止在 GET 中改数据（哪怕是更新点击数这种"小动作"）。

### 1.3 版本控制

URL 路径版本：`/api/v1/projects`、`/api/v2/projects`
- 重大变更升级 MAJOR
- 兼容性变更不升级版本号
- 旧版本至少保留 6 个月
- 完整的版本策略见 [`docs/api/api-versioning.md`](file:///d:/Code/ydsz/ydsz-pmis/docs/api/api-versioning.md)

> 📌 详细语义化版本管理（`Sunset` / `Deprecation` Header、双版本并行、迁移指南模板）请阅读 [`api-versioning.md`](file:///d:/Code/ydsz/ydsz-pmis/docs/api/api-versioning.md)。

## 2. 请求规范

### 2.1 通用 Header

| Header | 必填 | 说明 | 示例 |
|--------|------|------|------|
| `Authorization` | 是（除公开接口） | `Bearer <access_token>` | `Bearer eyJhbGc...` |
| `Content-Type` | POST/PUT/PATCH 必填 | `application/json;charset=UTF-8` | - |
| `Accept` | 否 | 期望返回类型 | `application/json` |
| `X-Trace-Id` | 否 | 链路追踪 ID（网关自动注入，外部可覆盖） | `0a1b2c3d4e5f6789` |
| `X-Tenant-Id` | 否 | 租户 ID（多租户场景） | `t-10086` |
| `Accept-Language` | 否 | 国际化 | `zh-CN` |
| `Idempotency-Key` | 关键写操作必填 | 幂等性 Key（UUID，24h 内唯一） | `uuid-xxx` |
| `User-Agent` | 否 | 客户端标识 | `PMIS-Web/1.0.0` |

> 💡 网关层（`AuthGlobalFilter`）会从 `Authorization` 解析用户信息并写入 `X-User-Id` / `X-Username` 透传给下游服务，**业务代码不需要再次解析**。

### 2.2 分页参数

```
GET /api/v1/projects?page=1&size=20&sort=createdAt,desc
```

| 参数 | 类型 | 默认 | 范围 | 说明 |
|------|------|------|------|------|
| `page` | int | 1 | ≥1 | 页码（从 1 开始） |
| `size` | int | 20 | 1~200 | 每页条数（最大 200，超过返回 400） |
| `sort` | string | - | 字段名,asc/desc | 排序字段，逗号分隔，支持多字段 |
| `keyword` | string | - | ≤64 字符 | 模糊查询关键字 |
| `filter.<field>` | any | - | - | 自定义过滤条件 |

> 💡 分页响应统一用 [`PageResult<T>`](file:///d:/Code/ydsz/ydsz-pmis/ydsz-pmis-backend/ydsz-pmis-common/src/main/java/com/njydsz/pmis/common/api/PageResult.java)，前端 [`useTable`](file:///d:/Code/ydsz/ydsz-pmis/ydsz-pmis-frontend/src/composables/useTable.ts) 已内置处理。

### 2.3 响应格式

> 所有响应（成功/失败）均使用 [`Result<T>`](file:///d:/Code/ydsz/ydsz-pmis/ydsz-pmis-backend/ydsz-pmis-common/src/main/java/com/njydsz/pmis/common/api/Result.java) 包装，**禁止** Controller 直接返回实体对象。

#### 成功（业务 HTTP 状态 200，code=0）

```json
{
  "code": 0,
  "message": "ok",
  "data": { ... },
  "traceId": "abc123",
  "timestamp": 1719734400000
}
```

#### 列表分页

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "records": [ ... ],
    "total": 1024,
    "current": 1,
    "size": 20,
    "pages": 52
  }
}
```

> 历史命名：`list/total/page/size/pages` 仍兼容，新接口推荐使用 `records/current/size/pages/total`（与 MyBatis-Plus IPage 对齐）。

#### 失败（业务 HTTP 状态 200，code≠0）

```json
{
  "code": 40001,
  "message": "参数错误：项目名称不能为空",
  "data": null,
  "traceId": "abc123",
  "timestamp": 1719734400000
}
```

#### 响应字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | int | 业务状态码：0=成功，其他=失败（详见 §3） |
| `message` | string | 人类可读提示信息 |
| `data` | T | 业务数据，失败时为 `null` |
| `traceId` | string | 链路追踪 ID，定位日志 |
| `timestamp` | long | 服务器时间戳（毫秒），用于前后端时钟对齐 |

## 3. 错误码体系

> 错误码是**业务语义的唯一标识**，与 HTTP 状态码**正交**。HTTP 状态码 200/4xx/5xx 表示"传输层"，错误码 `code` 表示"业务层"。
> 详细枚举定义见 [`BizErrorCode.java`](file:///d:/Code/ydsz/ydsz-pmis/ydsz-pmis-backend/ydsz-pmis-common/src/main/java/com/njydsz/pmis/common/api/BizErrorCode.java)。

### 3.1 错误码段位（10 段位，5 位整数）

| 段位 | 类别 | 段位含义 | 抛出位置示例 |
|------|------|----------|--------------|
| `0` | 成功 | 业务正常返回 | `Result.ok()` |
| `1xxxx` | 通用错误 | 校验、限流、上传下载等横切关注点 | `@Valid` 失败、限流触发 |
| `2xxxx` | 认证授权 | 登录态、Token、权限码、数据权限 | JWT 过期、缺少权限码 |
| `3xxxx` | 用户/组织/人员 | IAM 模块专属 | 用户不存在、密码错误 |
| `4xxxx` | 项目/合同/商机 | Project 模块专属 | 项目状态不允许操作 |
| `5xxxx` | 财务/成本/收入/利润 | 财务域专属 | 成本超预算 |
| `6xxxx` | 资源/工时/人员调度 | 资源池模块 | 资源冲突、Bench 闲置 |
| `7xxxx` | 工作流/审批 | Workflow 模块 | 流程不存在、被驳回 |
| `8xxxx` | 报表/驾驶舱 | 报表模块 | 报表生成失败 |
| `9xxxx` | 系统/未知 | 兜底错误 | 未捕获异常 |

> 段位预留充足（每个段位 9999 个码点），新业务场景优先复用现有码点，新增码点必须更新 [`BizErrorCode.java`](file:///d:/Code/ydsz/ydsz-pmis/ydsz-pmis-backend/ydsz-pmis-common/src/main/java/com/njydsz/pmis/common/api/BizErrorCode.java) 并在本表登记。

### 3.2 通用错误码速查表

| 错误码 | 名称 | 触发场景 | HTTP 状态 |
|--------|------|----------|-----------|
| 0 | OK | 成功 | 200 |
| 10001 | BAD_REQUEST | 请求参数错误、JSON 解析失败 | 200 |
| 10002 | VALIDATION_FAILED | 参数校验失败（@Valid） | 200 |
| 10003 | MISSING_PARAMETER | 缺少必填参数 | 200 |
| 10004 | METHOD_NOT_ALLOWED | HTTP 方法不被支持 | 405 |
| 10101 | NOT_FOUND | 资源不存在 | 404 |
| 10102 | DUPLICATE_KEY | 资源已存在（唯一键冲突） | 200 |
| 10201 | INTERNAL_ERROR | 系统内部错误（兜底） | 500 |
| 10202 | SERVICE_UNAVAILABLE | 服务暂不可用（熔断/降级） | 503 |
| 10203 | REQUEST_TIMEOUT | 请求超时 | 200 |
| 10301 | RATE_LIMIT | 请求频率超限 | 429 |
| 20001 | UNAUTHORIZED | 未登录 | 401 |
| 20002 | TOKEN_EXPIRED | Token 过期 | 401 |
| 20003 | TOKEN_INVALID | Token 无效（伪造/篡改） | 401 |
| 20101 | FORBIDDEN | 无权限访问 | 403 |
| 20102 | DATA_SCOPE_FORBIDDEN | 数据权限不足 | 403 |
| 30001 | USER_NOT_FOUND | 用户不存在 | 200 |
| 30002 | PASSWORD_INCORRECT | 密码错误 | 200 |
| 30003 | USER_DISABLED | 用户已停用 | 200 |
| 40001 | PROJECT_NOT_FOUND | 项目不存在 | 200 |
| 40002 | PROJECT_STATUS_INVALID | 项目状态不允许该操作 | 200 |
| 50001 | COST_OVERFLOW | 成本超预算 | 200 |
| 50002 | INVOICE_EXCEED | 开票金额超限 | 200 |
| 60001 | RESOURCE_CONFLICT | 资源冲突（人员被占用） | 200 |
| 60002 | BENCH_OVER_LIMIT | Bench 闲置超限 | 200 |
| 70001 | WORKFLOW_NOT_FOUND | 流程不存在 | 200 |
| 70002 | WORKFLOW_REJECT | 流程被驳回 | 200 |
| 90001 | UNKNOWN | 未知错误 | 200 |

### 3.3 错误码抛出约定

```java
// ✅ 推荐：使用枚举
throw new BizException(BizErrorCode.PROJECT_NOT_FOUND);

// ✅ 推荐：自定义消息（覆盖默认 message）
throw new BizException(BizErrorCode.NOT_FOUND, "项目 10086 不存在");

// ❌ 禁止：直接抛 RuntimeException（会被当作 10201 系统异常）
throw new RuntimeException("xxx");
```

## 4. 接口幂等性

> 幂等是分布式系统的**第一道防线**，PMIS 所有写接口必须支持幂等，否则重复点击将导致脏数据。

- 写操作（POST/PUT/DELETE）**必须** 支持幂等性
- POST 创建接口接收 `Idempotency-Key` Header，相同 Key 在 24h 内返回相同结果
- 支付、开票、回款等关键操作必须支持幂等
- 幂等存储使用 Redis：`pmis:idem:{userId}:{key}` → `result`，TTL 24h
- 客户端生成 Key 建议：`UUID v4` 或 `业务前缀:yyyyMMddHHmmss:seq`

```java
// 伪代码：幂等拦截器
String key = request.getHeader("Idempotency-Key");
if (redis.hasKey("pmis:idem:" + userId + ":" + key)) {
    return redis.get("pmis:idem:" + userId + ":" + key);
}
Result<?> result = proceed();
redis.setex("pmis:idem:" + userId + ":" + key, 86400, JSON.toJSONString(result));
return result;
```

## 5. 敏感数据

> 敏感数据需"三防"：**传输加密、存储加密、展示脱敏**。

- 合同金额、薪酬、身份证号等敏感字段传输需加密（AES）
- 响应中非授权用户**自动脱敏**（手机号 `138****1234`、身份证 `320***********1234`）
- 日志中**禁止**打印敏感字段（密码、密钥、Token、身份证、银行卡）
- 落库字段使用 AES-256-GCM 加密（[`CryptoUtil.aesGcmEncrypt`](file:///d:/Code/ydsz/ydsz-pmis/ydsz-pmis-backend/ydsz-pmis-common/src/main/java/com/njydsz/pmis/common/security/CryptoUtil.java)）
- 字段级注解 `@Sensitive(SensitiveType.MOBILE)` 自动脱敏

## 6. 接口文档

使用 **SpringDoc (Swagger)** 自动生成 OpenAPI 3.0 文档：

- 访问地址：`/swagger-ui.html` 或 Knife4j 增强版 `/doc.html`
- Controller 必须有 `@Tag` 注解
- 方法必须有 `@Operation`
- 参数必须有 `@Parameter` / `@Schema` 描述
- DTO/VO 必须有 `@Schema` 注解

```java
@Tag(name = "项目管理", description = "项目立项、变更、结项等核心接口")
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    @Operation(summary = "创建项目", description = "支持从商机快速创建项目")
    @PostMapping
    @PrePermission("project:create")
    public R<Long> create(
        @Parameter(description = "项目创建参数", required = true)
        @Valid @RequestBody ProjectCreateDTO dto
    ) {
        return R.ok(projectService.create(dto));
    }
}

@Schema(description = "项目创建参数")
@Data
public class ProjectCreateDTO {
    @Schema(description = "项目名称", example = "电商平台一期", requiredMode = RequiredMode.REQUIRED)
    @NotBlank(message = "项目名称不能为空")
    private String name;
}
```

## 7. 接口变更与下线

- 任何接口变更**必须**先评审，影响上下游评估
- 接口下线流程：Deprecate Header（保留 6 个月）→ 强制下线
- 所有变更记录在 `CHANGELOG.md`
- 详细规范见 [`docs/api/api-versioning.md`](file:///d:/Code/ydsz/ydsz-pmis/docs/api/api-versioning.md)

## 8. 限流

| 维度 | QPS | 触发限流后返回 | 备注 |
|------|-----|----------------|------|
| 全局默认 | 100 QPS / IP | 429 + `Retry-After` Header | 网关层统一拦截 |
| 登录接口 | 5 QPS / 用户 | 同上 | 防爆破 |
| 开票 | 10 QPS / 用户 | 同上 | 业务级 |
| 文件上传 | 20 QPS / 用户 | 同上 | 大流量保护 |
| 报表查询 | 10 QPS / 用户 | 同上 | 慢查询保护 |

- 限流策略：令牌桶 + 滑动窗口（Redis INCR + EXPIRE）
- 限流触发：返回 429 + `Retry-After` Header + `code=10301`
- 详细规则配置见 [`deploy/sentinel/flow-rules.json`](file:///d:/Code/ydsz/ydsz-pmis/deploy/sentinel/flow-rules.json)
