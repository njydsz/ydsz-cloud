# API 接口规范

> 文档版本: V1.0 | 编制日期: 2026-06-30
> 标准: RESTful API + JSON over HTTP

## 1. URL 命名

### 1.1 基础规则

- 资源使用名词复数：`/projects`、`/users`
- 层级不超过 3 层：`/projects/{id}/tasks`
- 路径小写，连字符分隔多词：`/project-templates`
- 路径中只允许出现资源 ID：`/projects/{id}`，禁止 `/projects?name=xxx`
- 过滤条件放在 query：`/projects?status=active&page=1`
- 操作型端点用动词子资源：`/projects/{id}/approve`、`/users/{id}/reset-password`

### 1.2 HTTP 方法

| 方法 | 用途 | 幂等 |
|------|------|------|
| GET | 查询 | 是 |
| POST | 创建/非幂等操作 | 否 |
| PUT | 完整更新 | 是 |
| PATCH | 部分更新 | 否 |
| DELETE | 删除 | 是 |

### 1.3 版本控制

URL 路径版本：`/api/v1/projects`、`/api/v2/projects`
- 重大变更升级 MAJOR
- 兼容性变更不升级版本号
- 旧版本至少保留 6 个月

## 2. 请求规范

### 2.1 通用 Header

| Header | 必填 | 说明 |
|--------|------|------|
| `Authorization` | 是 | `Bearer <token>` |
| `Content-Type` | POST/PUT 必填 | `application/json;charset=UTF-8` |
| `X-Trace-Id` | 否 | 链路追踪 ID（网关自动注入） |
| `X-Tenant-Id` | 否 | 租户 ID（多租户场景） |
| `Accept-Language` | 否 | 国际化 |

### 2.2 分页参数

```
GET /api/v1/projects?page=1&size=20&sort=createdAt,desc
```

| 参数 | 类型 | 默认 | 说明 |
|------|------|------|------|
| `page` | int | 1 | 页码（从 1 开始） |
| `size` | int | 20 | 每页条数（最大 200） |
| `sort` | string | - | 排序字段，逗号分隔 |

### 2.3 响应格式

#### 成功

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
    "list": [ ... ],
    "total": 1024,
    "page": 1,
    "size": 20,
    "pages": 52
  }
}
```

#### 失败

```json
{
  "code": 40001,
  "message": "参数错误：项目名称不能为空",
  "data": null,
  "traceId": "abc123",
  "timestamp": 1719734400000
}
```

## 3. 错误码体系

### 3.1 错误码段位

| 段位 | 含义 |
|------|------|
| 0 | 成功 |
| 1xxxx | 通用错误 |
| 2xxxx | 认证授权 |
| 3xxxx | 用户/组织/人员 |
| 4xxxx | 项目/合同/商机 |
| 5xxxx | 财务/成本/收入/利润 |
| 6xxxx | 资源/工时/人员调度 |
| 7xxxx | 工作流/审批 |
| 8xxxx | 报表/驾驶舱 |
| 9xxxx | 系统/未知 |

### 3.2 通用错误码

| 错误码 | 名称 | 说明 |
|--------|------|------|
| 0 | OK | 成功 |
| 10001 | BAD_REQUEST | 请求参数错误 |
| 10002 | VALIDATION_FAILED | 参数校验失败 |
| 10003 | MISSING_PARAMETER | 缺少参数 |
| 10101 | NOT_FOUND | 资源不存在 |
| 10102 | DUPLICATE_KEY | 资源已存在 |
| 10201 | INTERNAL_ERROR | 系统内部错误 |
| 10202 | SERVICE_UNAVAILABLE | 服务暂不可用 |
| 10203 | REQUEST_TIMEOUT | 请求超时 |
| 10301 | RATE_LIMIT | 请求频率超限 |
| 20001 | UNAUTHORIZED | 未登录 |
| 20002 | TOKEN_EXPIRED | Token 过期 |
| 20003 | TOKEN_INVALID | Token 无效 |
| 20101 | FORBIDDEN | 无权限访问 |
| 20102 | DATA_SCOPE_FORBIDDEN | 数据权限不足 |
| 30001 | USER_NOT_FOUND | 用户不存在 |
| 30002 | PASSWORD_INCORRECT | 密码错误 |
| 30003 | USER_DISABLED | 用户已停用 |
| 40001 | PROJECT_NOT_FOUND | 项目不存在 |
| 40002 | PROJECT_STATUS_INVALID | 项目状态不允许该操作 |
| 50001 | COST_OVERFLOW | 成本超预算 |
| 50002 | INVOICE_EXCEED | 开票金额超限 |
| 60001 | RESOURCE_CONFLICT | 资源冲突 |
| 60002 | BENCH_OVER_LIMIT | Bench 闲置超限 |
| 70001 | WORKFLOW_NOT_FOUND | 流程不存在 |
| 70002 | WORKFLOW_REJECT | 流程被驳回 |
| 90001 | UNKNOWN | 未知错误 |

## 4. 接口幂等性

- 写操作（POST/PUT/DELETE）**必须** 支持幂等性
- POST 创建接口接收 `Idempotency-Key` Header，相同 Key 在 24h 内返回相同结果
- 支付、开票、回款等关键操作必须支持幂等

## 5. 敏感数据

- 合同金额、薪酬、身份证号等敏感字段传输需加密（AES）
- 响应中非授权用户**自动脱敏**（手机号 `138****1234`、身份证 `320***********1234`）
- 日志中**禁止**打印敏感字段（密码、密钥、Token、身份证、银行卡）

## 6. 接口文档

使用 **SpringDoc (Swagger)** 自动生成 OpenAPI 3.0 文档：

- 访问地址：`/swagger-ui.html`
- Controller 必须有 `@Tag` 注解
- 方法必须有 `@Operation`
- 参数必须有 `@Parameter` / `@Schema` 描述
- DTO/VO 必须有 `@Schema` 注解

```java
@Tag(name = "项目管理")
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    @Operation(summary = "创建项目")
    @PostMapping
    public R<Long> create(
        @Parameter(description = "项目创建参数") @Valid @RequestBody ProjectCreateDTO dto
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

## 8. 限流

- 网关层：默认 100 QPS / IP
- 关键接口：登录 5 QPS / 用户、开票 10 QPS / 用户、文件上传 20 QPS / 用户
- 限流策略：令牌桶 + 滑动窗口
- 限流触发：返回 429 + `Retry-After` Header
