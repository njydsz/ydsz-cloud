# agent-web AI 助手

> YDSZ PMIS AI 助手子应用，提供 Agent 管理、审批配置、DAG 编排、流程定义与 RAG 知识库管理（对应后端：ydsz-agent，路由前缀：/ydsz-ai）

## 应用定位

| 属性 | 值 |
|---|---|
| **类型** | 微前端子应用 |
| **包名** | @ydsz/agent-web |
| **对应后端服务** | ydsz-agent |
| **前端端口** | 5610 |
| **后端端口** | 9008 |
| **路由前缀** | /ydsz-ai |
| **版本** | 1.0.0 |

agent-web 是 AI 智能体管理的前端载体，承接 Agent 定义与配置、人工审批节点编排、Agent DAG 工作流编排、流程定义管理以及 RAG 检索增强生成知识库管理，为业务场景提供可编排的 AI 能力接入。

## 目录结构

```text
agent-web/
├── src/
│   ├── adapter/                  # Element Plus / VXE Table 适配器
│   ├── api/                      # 业务 API
│   │   └── core/                 # 认证/菜单/用户 API（复用 @ydsz/shared-auth）
│   ├── layouts/                  # 子应用基础布局
│   ├── locales/                  # 国际化（zh-CN/en-US）
│   ├── router/                   # 路由守卫 + 模块化路由（agent.ts）
│   ├── store/                    # 子应用 Store
│   ├── views/                    # 业务页面
│   │   ├── agent/                # Agent 管理（index.vue + agent-form.vue）
│   │   ├── definition/           # 流程定义
│   │   ├── dag/                  # DAG 编排
│   │   ├── approval/             # 审批管理
│   │   ├── rag/                  # RAG 知识库
│   │   └── fallback/             # 404 异常页
│   ├── app.vue
│   ├── bootstrap.ts
│   ├── main.ts
│   └── preferences.ts
├── .env.development
├── index.html
└── vite.config.mts
```

## 关键依赖

本应用复用 monorepo 共享包，避免重复造轮子：

| 依赖包 | 作用 |
|---|---|
| `@ydsz/shared-auth` | 统一 RequestClient + Auth API + Auth Store |
| `@ydsz/shared-business` | 业务公共组件与工具 |
| `@ydsz/request` | HTTP 请求客户端（Axios 封装） |
| `@ydsz/access` | 权限码与路由访问控制 |
| `@ydsz/layouts` | 布局组件（basic） |
| `@ydsz/common-ui` | 通用 UI 组件 |
| `@ydsz/monitor` | 前端监控（错误捕获 + Web Vitals） |
| `@ydsz/locales` | 国际化基础包 |
| `element-plus` | UI 组件库 |
| `pinia` | 状态管理 |

## 核心功能模块

| 模块 | 功能描述 | 关键页面 |
|---|---|---|
| Agent 管理 | 智能体定义、模型供应商/提示词/温度配置 | `views/agent/` |
| 流程定义 | Agent 流程定义与发布 | `views/definition/` |
| DAG 编排 | Agent 有向无环图编排与节点配置 | `views/dag/` |
| 审批管理 | 人工审批节点配置与回调 | `views/approval/` |
| RAG 知识库 | 检索增强生成知识库与文档管理 | `views/rag/` |

## 启动方式

```bash
# 在 monorepo 根目录（ydsz-frontend/）执行
pnpm dev:agent        # 启动 AI 助手子应用（端口 5610）

# 启动主应用 + 所有子应用（含 agent-web）
pnpm dev

# 打包
pnpm build:agent
```

子应用需配合主应用（main-web，5600）一起访问，主应用通过路径前缀 `/ydsz-ai` 激活本应用。

## 环境变量

环境变量位于 `.env.development`：

| 变量 | 说明 | 默认值 |
|---|---|---|
| `VITE_PORT` | 开发服务器端口 | 5610 |
| `VITE_BASE` | 路由 base 路径 | / |
| `VITE_GLOB_API_URL` | 接口地址前缀（经 Gateway 9000 代理） | /api |
| `VITE_DEVTOOLS` | 是否开启 Vue Devtools | false |
| `VITE_INJECT_APP_LOADING` | 是否注入全局 loading | true |

## 路由配置

主应用通过 `/ydsz-ai` 前缀激活本应用，内部路由表（`src/router/routes/modules/agent.ts`）分为四组：

| 路由路径 | 名称 | 说明 |
|---|---|---|
| `/agent/list` | Agent 列表 | 智能体管理 |
| `/agent/definition` | Agent 定义 | 流程定义 |
| `/rag/list` | 知识库管理 | RAG 知识库 |
| `/dag/list` | DAG 列表 | DAG 编排 |
| `/approval/list` | 人工审批 | 审批管理 |

## 关键页面与组件

| 路径 | 功能 |
|---|---|
| `src/views/agent/index.vue` | Agent 列表，管理模型供应商/模型名/系统提示词/温度 |
| `src/views/definition/index.vue` | 流程定义，配置 Agent 执行流程与发布 |
| `src/views/dag/index.vue` | DAG 编排，配置节点依赖与执行顺序 |
| `src/views/rag/index.vue` | RAG 知识库，管理文档与检索配置 |
| `src/views/approval/index.vue` | 人工审批配置，设置审批节点与回调 |
| `src/layouts/basic.vue` | 子应用基础布局，复用 `@ydsz/layouts` |

每个业务模块均包含 `index.vue`（列表页）与 `*-form.vue`（表单页）两个核心页面。

## API 调用

API 模块位于 `src/api/`，使用 `@ydsz/request` 的 `requestClient`，对应后端 `/api/v1/agent/*` 端点：

```typescript
// Agent 管理（src/api/agent.ts）
import { requestClient } from '#/api/request';

export function getAgentPageApi(params: AgentApi.AgentPageQuery) {
  return requestClient.get<{
    total: number; current: number; size: number;
    items: AgentApi.AgentVO[];
  }>(`/api/v1/agent/page`, { params });
}

export function createAgentApi(data: AgentApi.AgentDTO) {
  return requestClient.post<string>(`/api/v1/agent`, data);
}
```

其他 API 模块：`definition.ts`（流程定义）、`dag.ts`（DAG 编排）、`approval.ts`（审批管理）、`rag.ts`（RAG 知识库）。认证与菜单 API 复用 `src/api/core/`（`@ydsz/shared-auth`）。

## 注意事项

1. **模型供应商配置**：Agent 表单中的 `modelProvider` / `modelName` 需与后端支持的模型供应商对齐，避免配置不可用模型。
2. **系统提示词安全**：`systemPrompt` 可能包含敏感指令，编辑时避免在前端日志或控制台明文输出完整提示词。
3. **DAG 环路检测**：Agent DAG 编排需在前端做环路检测，避免循环依赖导致 Agent 执行死锁。
4. **RAG 知识库容量**：知识库文档上传需限制单文件大小与总容量，大文件应走分片上传，避免前端内存溢出。
5. **认证复用**：本应用依赖 `@ydsz/shared-auth`，不重复实现登录，token 由主应用通过 micro-kernel globalState 注入。

## 变更记录

- **v1.0.0**（2026-08-02）：初始创建，对齐 capability model 第四章
