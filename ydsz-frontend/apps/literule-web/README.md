# literule-web 规则引擎

> YDSZ PMIS 规则引擎子应用，提供规则管理、DSL 管理、变量管理、CEP 复杂事件、断点调试与审计日志（对应后端：ydsz-literule，路由前缀：/ydsz-rule）

## 应用定位

| 属性 | 值 |
|---|---|
| **类型** | 微前端子应用 |
| **包名** | @ydsz/literule-web |
| **对应后端服务** | ydsz-literule |
| **前端端口** | 5608 |
| **后端端口** | 9007 |
| **路由前缀** | /ydsz-rule |
| **版本** | 1.0.0 |

literule-web 是轻量规则引擎的前端载体，承接规则定义（决策表/决策树/评分卡/脚本/复合）、DSL 表达式管理、规则变量、CEP 复杂事件处理、断点调试与审计日志查询，为业务提供可可视化编排与可追溯的规则执行能力。

## 目录结构

```text
literule-web/
├── src/
│   ├── adapter/                  # Element Plus / VXE Table 适配器
│   ├── api/                      # 业务 API
│   │   └── core/                 # 认证/菜单/用户 API（复用 @ydsz/shared-auth）
│   ├── layouts/                  # 子应用基础布局
│   ├── locales/                  # 国际化（zh-CN/en-US）
│   ├── router/                   # 路由守卫 + 模块化路由（literule.ts）
│   ├── store/                    # 子应用 Store
│   ├── views/                    # 业务页面
│   │   ├── rule/                 # 规则管理（index.vue + rule-form.vue）
│   │   ├── dsl/                  # DSL 管理
│   │   ├── variable/             # 规则变量
│   │   ├── cep/                  # CEP 复杂事件
│   │   ├── breakpoint/           # 断点调试
│   │   ├── auditLog/             # 审计日志
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
| 规则管理 | 决策表/决策树/评分卡/脚本规则 CRUD | `views/rule/` |
| DSL 管理 | 规则表达式 DSL 维护 | `views/dsl/` |
| 规则变量 | 规则上下文变量定义 | `views/variable/` |
| CEP 复杂事件 | 复杂事件处理模式配置 | `views/cep/` |
| 断点调试 | 规则执行断点与回放调试 | `views/breakpoint/` |
| 审计日志 | 规则执行轨迹与审计查询 | `views/auditLog/` |

## 启动方式

```bash
# 在 monorepo 根目录（ydsz-frontend/）执行
pnpm dev:literule      # 启动规则引擎子应用（端口 5608）

# 启动主应用 + 所有子应用（含 literule-web）
pnpm dev

# 打包
pnpm build:literule
```

子应用需配合主应用（main-web，5600）一起访问，主应用通过路径前缀 `/ydsz-rule` 激活本应用。

## 环境变量

环境变量位于 `.env.development`：

| 变量 | 说明 | 默认值 |
|---|---|---|
| `VITE_PORT` | 开发服务器端口 | 5608 |
| `VITE_BASE` | 路由 base 路径 | / |
| `VITE_GLOB_API_URL` | 接口地址前缀（经 Gateway 9000 代理） | /api |
| `VITE_DEVTOOLS` | 是否开启 Vue Devtools | false |
| `VITE_INJECT_APP_LOADING` | 是否注入全局 loading | true |

## 路由配置

主应用通过 `/ydsz-rule` 前缀激活本应用，内部路由表（`src/router/routes/modules/literule.ts`）分为三组：

| 路由路径 | 名称 | 说明 |
|---|---|---|
| `/rule/list` | 规则列表 | 规则管理 |
| `/rule/dsl` | DSL 管理 | 表达式维护 |
| `/rule/variable` | 规则变量 | 变量定义 |
| `/advanced/cep` | CEP 复杂事件 | 事件模式 |
| `/advanced/breakpoint` | 断点调试 | 执行回放 |
| `/audit/log` | 审计日志 | 执行轨迹 |

## 关键页面与组件

| 路径 | 功能 |
|---|---|
| `src/views/rule/index.vue` | 规则列表，支持按类型/优先级筛选与版本切换 |
| `src/views/dsl/index.vue` | DSL 表达式编辑与校验 |
| `src/views/cep/index.vue` | CEP 复杂事件模式配置 |
| `src/views/breakpoint/index.vue` | 断点调试，规则执行回放与变量快照 |
| `src/views/auditLog/index.vue` | 审计日志，查询规则执行轨迹与命中结果 |
| `src/layouts/basic.vue` | 子应用基础布局，复用 `@ydsz/layouts` |

每个业务模块均包含 `index.vue`（列表页）与 `*-form.vue`（表单页）两个核心页面。

## API 调用

API 模块位于 `src/api/`，使用 `@ydsz/request` 的 `requestClient`，对应后端 `/api/v1/literule/*` 端点：

```typescript
// 规则管理（src/api/rule.ts）
import { requestClient } from '#/api/request';

export function getRulePageApi(params: RuleApi.RulePageQuery) {
  return requestClient.get<{
    total: number; current: number; size: number;
    items: RuleApi.RuleVO[];
  }>(`/api/v1/literule/rules/page`, { params });
}

export function createRuleApi(data: RuleApi.RuleDTO) {
  return requestClient.post<string>(`/api/v1/literule/rules`, data);
}
```

其他 API 模块：`dsl.ts`（DSL 管理）、`variable.ts`（规则变量）、`cep.ts`（CEP 复杂事件）、`breakpoint.ts`（断点调试）、`auditLog.ts`（审计日志）。认证与菜单 API 复用 `src/api/core/`（`@ydsz/shared-auth`）。

## 注意事项

1. **规则端点路径**：规则 API 路径为 `/api/v1/literule/rules/*`（注意 `rules` 为复数），与后端 Controller `@RequestMapping` 对齐，修改时勿遗漏层级。
2. **规则版本管理**：规则支持多版本，编辑时需注意版本号递增，避免覆盖已上线版本，发布前建议在断点调试中回放验证。
3. **CEP 模式复杂度**：CEP 复杂事件模式配置较复杂，表单页需对事件窗口、匹配条件做前端校验，避免提交不可执行的模式。
4. **审计日志容量**：审计日志数据量随规则执行次数线性增长，列表页需强制分页与时间范围筛选。
5. **认证复用**：本应用依赖 `@ydsz/shared-auth`，不重复实现登录，token 由主应用通过 lite-kernel globalState 注入。

## 变更记录

- **v1.0.0**（2026-08-02）：初始创建，对齐 capability model 第四章
