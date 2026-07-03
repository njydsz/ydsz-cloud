# PMIS 后端 API 文档

## 文档结构

```
docs/api/
├── README.md                 # 本文件
├── openapi-summary.json      # 14 模块端点摘要（机器可读）
├── openapi-gateway.json      # API 网关路由表
└── modules/                  # 按模块拆分
    ├── auth.md               # 认证授权（/api/v1/auth/*）
    ├── user.md               # 用户/权限/部门/资源池（/api/v1/user/* + /api/v1/resource/*）
    ├── project.md            # 商机/立项/合同/变更（/api/v1/project/*）
    ├── execution.md          # 执行/成本/财务/报表（/api/v1/execution/*）
    ├── agent.md              # AI 智能体（/api/v1/agent/*）
    ├── notification.md       # 通知中心（/api/v1/notification/*）
    ├── workflow.md           # 工作流（/api/v1/workflow/*）
    ├── file.md               # 文件服务（/api/v1/file/*）
    ├── audit.md              # 审计日志（/api/v1/audit/*）
    ├── message.md            # 消息模板（/api/v1/message/*）
    ├── config.md             # 系统配置（/api/v1/config/*）
    └── scheduler.md          # 任务调度（/api/v1/scheduler/*，保留路径前缀）
```

## 查看方式

### 方式 1：Swagger UI（推荐）

每个微服务在 808X 端口暴露 Swagger UI：

```
http://localhost:9001/swagger-ui.html  # auth
http://localhost:9002/swagger-ui.html  # user
http://localhost:9005/swagger-ui.html  # project
http://localhost:9006/swagger-ui.html  # execution
...
http://localhost:9010/swagger-ui.html  # audit
```

Knife4j 增强版（国内访问更稳定）：

```
http://localhost:9001/doc.html
```

### 方式 2：OpenAPI 3 JSON

每个微服务启动后访问：

```
http://localhost:9001/v3/api-docs
http://localhost:9002/v3/api-docs
...
```

返回的 JSON 可导入 [Apifox](https://www.apifox.cn/)、[Apipost](https://www.apipost.cn/)、[Postman](https://www.postman.com/) 等工具。

### 方式 3：本文档（Markdown）

适合代码评审与版本对比，每模块一个 `.md` 文件，含：
- 端点列表（HTTP 方法 + 路径 + 鉴权码）
- 请求/响应示例
- 业务规则说明
- 错误码索引

## 鉴权约定

### 1. Token 传递

所有 `/api/v1/*` 接口（除登录/公开接口外）需要在请求头携带：

```
Authorization: Bearer <access_token>
```

Access token 有效期 2 小时，refresh token 有效期 7 天。

### 2. 权限码

后端 14 个模块使用统一的 `xxx:yyy:action` 格式权限码，由 `@PrePermission` 注解或 `@DataScope` 注解校验。前端 9 大业务模块权限码与后端完全一致，详见：

- 前端：[`src/constants/permissionCodes.ts`](../../ydsz-pmis-frontend/src/constants/permissionCodes.ts)
- 后端：`ydsz-pmis-common/src/main/java/com/njydsz/pmis/common/permission/PermissionCodeValidator.java`

### 3. 数据权限

`@DataScope` 注解支持 6 种数据隔离模式：ALL / DEPT / DEPT_AND_CHILD / SELF / CUSTOM / PROJECT。
超级管理员（`user.permissions` 包含 `*.*.*`）自动绕过数据权限。

## 错误码

| 错误码区间 | 含义 | 触发场景 |
|------------|------|----------|
| 0 | 成功 | 业务正常返回 |
| 400 | 请求参数错误 | 字段校验失败、必填缺失 |
| 401 | 未登录 | Token 缺失、过期、伪造 |
| 403 | 无权限 | 权限码缺失、数据越权、敏感操作二次认证失败 |
| 404 | 资源不存在 | ID 不存在或已删除 |
| 409 | 业务冲突 | 状态机不允许、唯一键冲突、库存不足 |
| 429 | 限流 | 1 秒内重复请求、IP 限流触发 |
| 500 | 内部错误 | 未捕获异常 |
| 5xxx | 业务异常 | 详见各模块 BizErrorCode 枚举 |

完整错误码定义：[`ydsz-pmis-common/src/main/java/com/njydsz/pmis/common/api/BizErrorCode.java`](../../ydsz-pmis-backend/ydsz-pmis-common/src/main/java/com/njydsz/pmis/common/api/BizErrorCode.java)

## 响应格式

统一使用 `R<T>` 包装：

```json
{
  "code": 0,
  "message": "ok",
  "data": { /* 业务数据 */ },
  "traceId": "0a1b2c3d4e5f6789",
  "timestamp": 1735689600000
}
```

分页使用 `PageResult<T>`：

```json
{
  "code": 0,
  "data": {
    "records": [ /* 数据列表 */ ],
    "total": 100,
    "current": 1,
    "size": 20
  }
}
```

## 链路追踪

所有请求自动注入 `traceId`（16 位雪花），从 gateway 透传到所有下游服务。
日志格式：`%X{traceId} %-5level [%thread] %logger{36} - %msg%n`

手动传入：`X-Trace-Id: <custom-id>`

## 限流规则

- 全局默认：1000 QPS / IP
- 登录接口：5 QPS / IP / 分钟（防爆破）
- 导入接口：1 QPS / 用户
- 报表查询：10 QPS / 用户

详见 [`deploy/sentinel/flow-rules.json`](../../deploy/sentinel/flow-rules.json)

## 自动生成方式

### 启动时实时生成

```bash
# 启动任一服务
mvn -pl ydsz-pmis-project spring-boot:run

# 另开终端导出 OpenAPI JSON
curl http://localhost:9005/v3/api-docs > docs/api/openapi-project.json
```

### Maven 批量生成

```bash
mvn -pl 'ydsz-pmis-*' -am test-compile
mvn -pl 'ydsz-pmis-*' exec:java -Dexec.mainClass="org.springdoc.openapi.GenerateOpenApi" -Dexec.classpathScope=test
```

## 变更记录

| 日期 | 版本 | 变更 |
|------|------|------|
| 2026-07-01 | 1.0.0 | 初始 14 模块端点文档（批次 18） |
| 2026-06-15 | 1.0.0-rc3 | execution 模块增 ImportExportController |
| 2026-06-01 | 1.0.0-rc2 | agent 模块增 AgentOrchestrationController |

## 维护负责人

| 模块 | 负责人 | 联系方式 |
|------|--------|----------|
| 整体 API 规范 | 后端架构组 | api-team@ydsz-pmis.cn |
| 各模块端点 | 模块 Owner | 见 [`docs/standards/code-quality.md`](../standards/code-quality.md) |
