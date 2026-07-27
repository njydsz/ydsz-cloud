# PMIS API 版本前缀规范

> 版本: 1.1.0 | 更新日期: 2026-07-27

---

## 1. 标准规范

所有 RESTful API 路径必须遵循以下格式：

```
/api/v{version}/{module}/{resource}[/{sub-resource}]
```

| 组成部分 | 说明 | 示例 |
|---------|------|------|
| `/api` | API 根前缀 | 所有外部 API 必须包含 |
| `/v{version}` | API 版本号 | `v1`, `v2` |
| `/{module}` | 模块标识 | `project`, `workflow`, `userinfo` |
| `/{resource}` | 资源路径 | `initiation`, `task`, `user` |

### 内部 API

内部 API（模块间调用）使用 `/api/internal` 前缀，不包含版本号：

```
/api/internal/{module}/{resource}
```

### 特殊端点

- 健康检查：`/actuator/health`
- 指标暴露：`/actuator/prometheus`
- API 文档：`/v3/api-docs`
- Swagger UI：`/swagger-ui.html`

---

## 2. 各模块合规状态

| 模块 | 标准前缀 | 合规状态 | 说明 |
|------|---------|---------|------|
| **project** | `/api/v1/project/` | ✅ 合规 | 全部 34 个 Controller 统一使用 |
| **userinfo** | `/api/v1/` | ✅ 合规 | 全部 10 个 Controller 统一使用 |
| **system** | `/api/v1/` | ✅ 合规 | 全部 7 个 Controller 统一使用 |
| **nextwiki** | `/api/v1/` | ✅ 合规 | 全部 Controller 统一使用 |
| **message** | `/api/v1/` | ✅ 合规 | 全部 Controller 统一使用 |
| **cronjob** | `/api/v1/` | ✅ 合规 | 全部 Controller 统一使用 |
| **literule** | `/api/v1/` | ✅ 合规 | 全部 Controller 统一使用 |
| **workflow** | `/api/v1/workflow/` | ⚠️ 部分合规 | 6 个用 `/api/workflow/`，21 个用 `/workflow/`（缺 `/api/v1`） |
| **agent** | `/api/v1/agent/` | ❌ 不合规 | 全部 8 个 Controller 使用 `/agent/`（缺 `/api/v1`） |

---

## 3. 迁移计划

### 3.1 Workflow 模块迁移

需要将以下路径统一为 `/api/v1/workflow/`：

**已有 `/api/workflow/` 前缀（需加 `v1`）：**
- `/api/workflow/analytics` → `/api/v1/workflow/analytics`
- `/api/workflow/categories` → `/api/v1/workflow/categories`
- `/api/workflow/customButtons` → `/api/v1/workflow/customButtons`
- `/api/workflow/offlineForward` → `/api/v1/workflow/offlineForward`
- `/api/workflow/quickComments` → `/api/v1/workflow/quickComments`
- `/api/workflow/conditionExpr` → `/api/v1/workflow/conditionExpr`

**无 `/api` 前缀（需加 `/api/v1/workflow`）：**
- `/workflow/engine` → `/api/v1/workflow/engine`
- `/workflow/comment` → `/api/v1/workflow/comment`
- `/workflow/dmn` → `/api/v1/workflow/dmn`
- `/workflow/embedded` → `/api/v1/workflow/embedded`
- `/workflow/history` → `/api/v1/workflow/history`
- `/workflow/i18n` → `/api/v1/workflow/i18n`
- `/workflow/template` → `/api/v1/workflow/template`
- `/workflow/thirdParty` → `/api/v1/workflow/thirdParty`
- `/workflow/trigger` → `/api/v1/workflow/trigger`

### 3.2 Agent 模块迁移

需要将以下路径统一为 `/api/v1/agent/`：
- `/agent` → `/api/v1/agent`
- `/agent/dag` → `/api/v1/agent/dag`
- `/agent/debug` → `/api/v1/agent/debug`
- `/agent/approvals` → `/api/v1/agent/approvals`
- `/agent/rag` → `/api/v1/agent/rag`
- `/agent/definitions` → `/api/v1/agent/definitions`

### 3.3 迁移策略

1. **Gateway 路由重写**（短期）：在 Gateway 层配置路径重写规则，旧路径自动转发到新路径
2. **Controller 路径修改**（中期）：在下次发版时统一修改 Controller 的 `@RequestMapping`
3. **前端适配**（同步）：前端 API 调用路径同步更新
4. **旧路径兼容期**：保留 3 个月旧路径兼容（通过 Gateway 重写），之后移除
