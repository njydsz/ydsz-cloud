# cronjob-web 定时任务

> YDSZ PMIS 定时任务子应用，提供任务管理、任务分组、DAG 管理、执行日志、告警管理与连接器配置（对应后端：ydsz-cronjob，路由前缀：/ydsz-cron）

## 应用定位

| 属性 | 值 |
|---|---|
| **类型** | 微前端子应用 |
| **包名** | @ydsz/cronjob-web |
| **对应后端服务** | ydsz-cronjob |
| **前端端口** | 5605 |
| **后端端口** | 9006 |
| **路由前缀** | /ydsz-cron |
| **版本** | 1.0.0 |

cronjob-web 是分布式任务调度的前端载体，承接定时任务管理、任务分组、DAG 有向无环图编排、执行日志查询、告警规则配置与连接器（数据源/执行器）管理，为全平台提供统一的任务调度与监控能力。

## 目录结构

```text
cronjob-web/
├── src/
│   ├── adapter/                  # Element Plus / VXE Table 适配器
│   ├── api/                      # 业务 API
│   │   └── core/                 # 认证/菜单/用户 API（复用 @ydsz/shared-auth）
│   ├── layouts/                  # 子应用基础布局
│   ├── locales/                  # 国际化（zh-CN/en-US）
│   ├── router/                   # 路由守卫 + 模块化路由（cronjob.ts）
│   ├── store/                    # 子应用 Store
│   ├── views/                    # 业务页面
│   │   ├── job/                  # 任务管理（index.vue + job-form.vue）
│   │   ├── jobGroup/             # 任务分组
│   │   ├── jobDag/               # DAG 管理
│   │   ├── jobLog/               # 执行日志
│   │   ├── alert/                # 告警管理
│   │   ├── connector/            # 连接器
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
| 任务管理 | Cron 表达式任务 CRUD、启停、手动触发 | `views/job/` |
| 任务分组 | 任务逻辑分组与批量管理 | `views/jobGroup/` |
| DAG 管理 | 任务有向无环图编排与依赖配置 | `views/jobDag/` |
| 执行日志 | 任务执行历史与输出日志查询 | `views/jobLog/` |
| 告警管理 | 任务失败告警规则与通知通道 | `views/alert/` |
| 连接器 | 数据源/执行器连接器配置 | `views/connector/` |

## 启动方式

```bash
# 在 monorepo 根目录（ydsz-frontend/）执行
pnpm dev:cronjob       # 启动定时任务子应用（端口 5605）

# 启动主应用 + 所有子应用（含 cronjob-web）
pnpm dev

# 打包
pnpm build:cronjob
```

子应用需配合主应用（main-web，5600）一起访问，主应用通过路径前缀 `/ydsz-cron` 激活本应用。

## 环境变量

环境变量位于 `.env.development`：

| 变量 | 说明 | 默认值 |
|---|---|---|
| `VITE_PORT` | 开发服务器端口 | 5605 |
| `VITE_BASE` | 路由 base 路径 | / |
| `VITE_GLOB_API_URL` | 接口地址前缀（经 Gateway 9000 代理） | /api |
| `VITE_DEVTOOLS` | 是否开启 Vue Devtools | false |
| `VITE_INJECT_APP_LOADING` | 是否注入全局 loading | true |

## 路由配置

主应用通过 `/ydsz-cron` 前缀激活本应用，内部路由表（`src/router/routes/modules/cronjob.ts`）分为五组：

| 路由路径 | 名称 | 说明 |
|---|---|---|
| `/job/list` | 任务列表 | 定时任务管理 |
| `/job/group` | 任务分组 | 逻辑分组 |
| `/dag/list` | DAG 列表 | 有向无环图编排 |
| `/log/list` | 执行日志 | 历史与输出 |
| `/alert/list` | 告警规则 | 失败告警 |
| `/connector/list` | 连接器管理 | 数据源/执行器 |

## 关键页面与组件

| 路径 | 功能 |
|---|---|
| `src/views/job/index.vue` | 任务列表，支持 Cron 表达式、启停、手动触发 |
| `src/views/jobDag/index.vue` | DAG 编排，配置任务依赖与执行顺序 |
| `src/views/jobLog/index.vue` | 执行日志，查询历史执行结果与输出 |
| `src/views/alert/index.vue` | 告警规则，配置失败通知通道 |
| `src/views/connector/index.vue` | 连接器配置，管理数据源与执行器 |
| `src/layouts/basic.vue` | 子应用基础布局，复用 `@ydsz/layouts` |

每个业务模块均包含 `index.vue`（列表页）与 `*-form.vue`（表单页）两个核心页面。

## API 调用

API 模块位于 `src/api/`，使用 `@ydsz/request` 的 `requestClient`，对应后端 `/api/v1/cronjob/*` 端点：

```typescript
// 任务管理（src/api/job.ts）
import { requestClient } from '#/api/request';

export function getJobPageApi(params: JobApi.JobPageQuery) {
  return requestClient.get<{
    total: number; current: number; size: number;
    items: JobApi.JobVO[];
  }>(`/api/v1/cronjob/page`, { params });
}

export function createJobApi(data: JobApi.JobDTO) {
  return requestClient.post<string>(`/api/v1/cronjob`, data);
}
```

其他 API 模块：`jobGroup.ts`（任务分组）、`jobDag.ts`（DAG 管理）、`jobLog.ts`（执行日志）、`alert.ts`（告警管理）、`connector.ts`（连接器）。认证与菜单 API 复用 `src/api/core/`（`@ydsz/shared-auth`）。

## 注意事项

1. **Cron 表达式校验**：任务表单的 Cron 表达式需在前端做基本格式校验，避免提交非法表达式导致调度器异常。
2. **DAG 环路检测**：DAG 编排需在前端做环路检测，避免配置出循环依赖导致任务无法执行。
3. **任务启停**：任务状态切换（NORMAL/PAUSE）需二次确认，避免误停关键任务，启停操作即时生效。
4. **日志容量**：执行日志数据量较大，列表页需强制分页与时间范围筛选，避免一次性拉取全量日志。
5. **认证复用**：本应用依赖 `@ydsz/shared-auth`，不重复实现登录，token 由主应用通过 micro-kernel globalState 注入。

## 变更记录

- **v1.0.0**（2026-08-02）：初始创建，对齐 capability model 第四章
