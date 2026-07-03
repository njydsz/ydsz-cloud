# 规则引擎 API 总览

## 基础信息

| 项目 | 值 |
|------|------|
| 基础路径 | `/api/v1/rules` |
| 鉴权 | Bearer JWT（`Authorization: Bearer {token}`） |
| 网关入口 | `http://localhost:9000`（推荐） |
| 直连地址 | `http://localhost:9003`（project 模块） |
| OpenAPI 文档 | `/v3/api-docs` |
| Swagger UI | `/swagger-ui.html` |

## 端点分类

### 规则 CRUD

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/` | 查询全部规则定义 |
| GET | `/{ruleCode}` | 查询单条规则 |
| POST | `/` | 新增/更新规则 |
| PUT | `/{ruleCode}/toggle` | 切换启停 |
| DELETE | `/{ruleCode}` | 软删除（状态置为 ARCHIVED） |
| GET | `/{ruleCode}/versions` | 查询版本历史 |
| POST | `/{ruleCode}/rollback` | 回滚到指定版本 |
| PUT | `/{ruleCode}/status` | 规则状态变更 |
| POST | `/{ruleCode}/approve` | 审批通过 |
| POST | `/{ruleCode}/reject` | 审批驳回 |

### 仿真与校验

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/dry-run` | Dry-run 仿真 |
| GET | `/validate` | 校验表达式语法 |
| POST | `/validate-expression` | 详细校验（结构化结果） |
| POST | `/validate-batch` | 批量校验 |
| POST | `/{ruleCode}/ab-test` | A/B 测试 |

### 批量操作

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/batch-toggle` | 批量启停 |
| POST | `/batch-priority` | 批量调整优先级 |
| POST | `/batch-category` | 批量调整分类 |

### 模板与函数

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/templates` | 查询全部模板 |
| GET | `/templates/category/{category}` | 按类别查询 |
| GET | `/templates/industry/{industry}` | 按行业查询 |
| POST | `/templates/{templateCode}/import` | 一键导入模板 |
| GET | `/expression-functions` | 表达式函数市场 |

### 冲突检测与依赖

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/conflicts` | 检测规则冲突 |
| POST | `/{ruleCode}/dependencies` | 添加依赖 |
| DELETE | `/{ruleCode}/dependencies/{dependsOnRuleCode}` | 删除依赖 |
| GET | `/{ruleCode}/dependencies` | 查询正向依赖 |
| GET | `/{ruleCode}/dependents` | 查询反向依赖 |
| GET | `/{ruleCode}/cascading-disable` | 级联禁用影响 |

### 画布与目录

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/{ruleCode}/graph` | 查询规则画布 |
| POST | `/{ruleCode}/graph` | 保存画布（含结构校验） |
| DELETE | `/{ruleCode}/graph` | 删除画布 |
| POST | `/{ruleCode}/graph/validate` | 校验画布结构 |
| GET | `/category-tree` | 规则目录树 |
| GET | `/by-category-path` | 按分类路径查询 |
| GET | `/by-owner` | 按 Owner 查询 |
| PUT | `/{ruleCode}/owner` | 设置责任人 |
| PUT | `/{ruleCode}/category-path` | 设置分类路径 |

### 测试用例与追踪

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/test-cases` | 查询测试用例 |
| POST | `/test-cases` | 保存测试用例 |
| DELETE | `/test-cases/{id}` | 删除测试用例 |
| POST | `/test-cases/batch-run` | 批量回归测试 |
| GET | `/traces/{traceId}` | 按 traceId 查询链路 |
| GET | `/traces/rule/{ruleCode}` | 按规则查询链路 |
| POST | `/traces/{traceId}/replay` | 执行回放 |
| GET | `/traces` | 最近执行链路 |

### 决策表

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/decision-tables` | 查询全部决策表 |
| GET | `/decision-tables/{tableCode}` | 查询单条 |
| POST | `/decision-tables` | 保存 |
| DELETE | `/decision-tables/{id}` | 删除 |
| POST | `/decision-tables/{tableCode}/evaluate` | 评估决策表 |

### 导入导出

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/export` | 导出全部规则 JSON |
| POST | `/import` | 导入规则 JSON |

### 统计

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/stats` | 规则引擎执行统计 |

## AI 增强端点

详见 [AI 增强端点](./rules-ai)。

## 分布式执行

详见 [分布式执行](./rules-distributed)。

## 统一响应结构

```json
{
  "code": 0,
  "message": "ok",
  "data": { },
  "traceId": "0a1b2c3d4e5f6789",
  "timestamp": 1735689600000
}
```

- `code=0` 表示成功，非 0 表示业务错误
- `traceId` 用于全链路追踪，贯穿日志、审计、Trace 表
- 分页查询统一返回 `PageResult`：`{ records, total, current, size }`
