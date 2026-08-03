# workflow-web 工作流引擎

> YDSZ PMIS 工作流引擎子应用，提供流程模板、流程实例、审批任务、委托授权与快捷意见管理（对应后端：ydsz-workflow，路由前缀：/ydsz-flow）

## 应用定位

| 属性 | 值 |
|---|---|
| **类型** | 微前端子应用 |
| **包名** | @ydsz/workflow-web |
| **对应后端服务** | ydsz-workflow |
| **前端端口** | 5606 |
| **后端端口** | 9005 |
| **路由前缀** | /ydsz-flow |
| **版本** | 1.0.0 |

workflow-web 是自研工作流引擎 v2（基于 `ydsz_flow_*` 表与 BPMN 2.0）的前端载体，面向 PC Web 端提供流程模板设计、流程实例监控、待办任务处理、审批委派授权与常用审批意见管理。所有 UI 仅适配 PC 浏览器，不适配移动端。

## 目录结构

```text
workflow-web/
├── src/
│   ├── adapter/                  # Element Plus / VXE Table 适配器
│   ├── api/                      # 业务 API
│   │   └── core/                 # 认证/菜单/用户 API（复用 @ydsz/shared-auth）
│   ├── layouts/                  # 子应用基础布局
│   ├── locales/                  # 国际化（zh-CN/en-US）
│   ├── router/                   # 路由守卫 + 模块化路由（workflow.ts）
│   ├── store/                    # 子应用 Store
│   ├── views/                    # 业务页面
│   │   ├── template/             # 流程模板（index.vue + template-form.vue）
│   │   ├── category/             # 流程分类
│   │   ├── instance/             # 流程实例
│   │   ├── task/                 # 审批任务
│   │   ├── delegate/             # 委派授权
│   │   ├── quickComment/         # 快捷评语
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
| 流程模板 | BPMN 2.0 模板设计、版本管理、灰度发布 | `views/template/` |
| 流程分类 | 流程模板分类树管理 | `views/category/` |
| 流程实例 | 运行中/已完成实例监控与干预 | `views/instance/` |
| 审批任务 | 待办/已办任务处理与流转 | `views/task/` |
| 委派授权 | 审批人委派、代理授权配置 | `views/delegate/` |
| 快捷评语 | 常用审批意见库管理 | `views/quickComment/` |

## 启动方式

```bash
# 在 monorepo 根目录（ydsz-frontend/）执行
pnpm dev:workflow      # 启动工作流子应用（端口 5606）

# 启动主应用 + 所有子应用（含 workflow-web）
pnpm dev

# 打包
pnpm build:workflow
```

子应用需配合主应用（main-web，5600）一起访问，主应用通过路径前缀 `/ydsz-flow` 激活本应用。

## 环境变量

环境变量位于 `.env.development`：

| 变量 | 说明 | 默认值 |
|---|---|---|
| `VITE_PORT` | 开发服务器端口 | 5606 |
| `VITE_BASE` | 路由 base 路径 | / |
| `VITE_GLOB_API_URL` | 接口地址前缀（经 Gateway 9000 代理） | /api |
| `VITE_DEVTOOLS` | 是否开启 Vue Devtools | false |
| `VITE_INJECT_APP_LOADING` | 是否注入全局 loading | true |

## 路由配置

主应用通过 `/ydsz-flow` 前缀激活本应用，内部路由表（`src/router/routes/modules/workflow.ts`）分为三组：

| 路由路径 | 名称 | 说明 |
|---|---|---|
| `/template/list` | 模板列表 | 流程模板管理 |
| `/template/category` | 流程分类 | 模板分类树 |
| `/flow/instance` | 流程实例 | 实例监控 |
| `/flow/task` | 待办任务 | 审批待办处理 |
| `/approval/delegate` | 委派管理 | 审批委派授权 |
| `/approval/quick-comment` | 快捷评语 | 常用意见库 |

## 关键页面与组件

| 路径 | 功能 |
|---|---|
| `src/views/template/index.vue` | 流程模板列表，支持按分类筛选与版本切换 |
| `src/views/template/template-form.vue` | 模板设计器表单，承载 BPMN 2.0 流程定义 |
| `src/views/instance/index.vue` | 流程实例监控，查看运行状态与历史轨迹 |
| `src/views/task/index.vue` | 待办任务列表，支持审批通过/驳回/转办 |
| `src/views/delegate/index.vue` | 委派授权配置，设置代理人与时效 |
| `src/layouts/basic.vue` | 子应用基础布局，复用 `@ydsz/layouts` |

每个业务模块均包含 `index.vue`（列表页）与 `*-form.vue`（表单页）两个核心页面。

## API 调用

API 模块位于 `src/api/`，使用 `@ydsz/request` 的 `requestClient`，对应后端 `/api/v1/workflow/*` 端点：

```typescript
// 流程模板（src/api/template.ts）
import { requestClient } from '#/api/request';

export function getTemplatePageApi(params: TemplateApi.TemplatePageQuery) {
  return requestClient.get<{
    total: number; current: number; size: number;
    items: TemplateApi.TemplateVO[];
  }>(`/api/v1/workflow/template/page`, { params });
}

export function createTemplateApi(data: TemplateApi.TemplateDTO) {
  return requestClient.post<string>(`/api/v1/workflow/template`, data);
}
```

其他 API 模块：`category.ts`（流程分类）、`instance.ts`（流程实例）、`task.ts`（审批任务）、`delegate.ts`（委派授权）、`quickComment.ts`（快捷评语）。认证与菜单 API 复用 `src/api/core/`（`@ydsz/shared-auth`）。

## 注意事项

1. **PC-only 约束（强制）**：`ydsz-workflow` 模块**永远不适配移动端 App 或独立 H5 应用**。流程设计器（bpmn-js）、审批中心、流程模板、流程实例监控等所有 UI 仅面向 PC Web 端（Vue 3.5 + Element Plus）。详见 `.trae/rules/workflow-pc-only.md`。禁止在本应用中新增 `Mobile`/`H5`/`App` 字样的 Controller、`/mobile/`/`/h5/` 路径前缀、移动端专属 VO/DTO 或移动端 UA 识别逻辑。如需移动端审批能力，须通过独立的轻审批 H5 或对接企业微信/钉钉/飞书 IM 通道实现，**绝不允许**把工作流代码直接运行在移动端容器。
2. **第三方审批同步例外**：通过 webhook 与钉钉/飞书/企微服务端通信的审批状态同步（`FlowThirdPartySyncService`）属于服务端到服务端通信，不涉及移动端 UI，不在 PC-only 禁止范围内。
3. **模板版本管理**：流程模板支持多版本与灰度发布，列表页按 `templateCode` 聚合，编辑时需注意版本号递增，避免覆盖已上线版本。
4. **委派授权时效**：委派配置需设置起止时间，过期自动失效，代理人仅能在授权范围内处理任务。
5. **认证复用**：本应用依赖 `@ydsz/shared-auth`，不重复实现登录，token 通过 `useTokenStore()` 从 SecureLS 读取，由 `@ydsz/shared-auth` 统一管理。

## 变更记录

- **v1.0.0**（2026-08-02）：初始创建，对齐 capability model 第四章
