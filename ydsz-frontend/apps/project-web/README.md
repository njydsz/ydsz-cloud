# project-web 项目管理

> YDSZ PMIS 项目管理子应用，覆盖商机、立项、合同、执行、EVM、财务、人力全链路（对应后端：ydsz-project，路由前缀：/ydsz-proj）

## 应用定位

| 属性 | 值 |
|---|---|
| **类型** | 微前端子应用 |
| **包名** | @ydsz/project-web |
| **对应后端服务** | ydsz-project |
| **前端端口** | 5603 |
| **后端端口** | 9009 |
| **路由前缀** | /ydsz-proj |
| **版本** | 1.0.0 |

project-web 是项目管理业务的前端载体，承接从销售商机到项目交付的完整生命周期管理，包括商机漏斗、立项审批、合同签订、WBS 执行、挣值分析（EVM）、预算/费用/收入/发票/回款财务闭环，以及费率卡人力成本核算。

## 目录结构

```text
project-web/
├── src/
│   ├── adapter/                  # Element Plus / VXE Table 适配器
│   ├── api/                      # 业务 API
│   │   └── core/                 # 认证/菜单/用户 API（复用 @ydsz/shared-auth）
│   ├── layouts/                  # 子应用基础布局
│   ├── locales/                  # 国际化（zh-CN/en-US）
│   ├── router/                   # 路由守卫 + 模块化路由（project.ts）
│   ├── store/                    # 子应用 Store
│   ├── views/                    # 业务页面
│   │   ├── opportunity/          # 商机（index.vue + opportunity-form.vue）
│   │   ├── contract/             # 合同
│   │   ├── initiation/           # 立项
│   │   ├── execution/            # 执行（WBS）
│   │   ├── risk/                 # 风险
│   │   ├── budget/               # 预算
│   │   ├── expense/              # 费用
│   │   ├── revenue/              # 收入
│   │   ├── invoice/              # 发票
│   │   ├── payment/              # 回款
│   │   ├── evm/                  # 挣值分析
│   │   └── rateCard/             # 费率卡
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
| 商机管理 | 销售商机漏斗、客户/产品/预计金额/阶段/胜率 | `views/opportunity/` |
| 立项管理 | 项目立项申请与审批 | `views/initiation/` |
| 合同管理 | 合同台账、收付款条款 | `views/contract/` |
| 执行管理 | WBS 任务分解与进度跟踪 | `views/execution/` |
| 风险管理 | 项目风险识别与应对 | `views/risk/` |
| 预算管理 | 项目预算编制与控制 | `views/budget/` |
| 费用管理 | 项目费用归集与报销 | `views/expense/` |
| 收入管理 | 收入确认与里程碑结算 | `views/revenue/` |
| 发票管理 | 开票申请与发票台账 | `views/invoice/` |
| 回款管理 | 应收账款与回款登记 | `views/payment/` |
| EVM 挣值 | 挣值测量（PV/EV/AC/SPI/CPI）分析 | `views/evm/` |
| 费率卡 | 人力岗位费率与成本核算 | `views/rateCard/` |

## 启动方式

```bash
# 在 monorepo 根目录（ydsz-frontend/）执行
pnpm dev:project       # 启动项目管理子应用（端口 5603）

# 启动主应用 + 所有子应用（含 project-web）
pnpm dev

# 打包
pnpm build:project
```

子应用需配合主应用（main-web，5600）一起访问，主应用通过路径前缀 `/ydsz-proj` 激活本应用。

## 环境变量

环境变量位于 `.env.development`：

| 变量 | 说明 | 默认值 |
|---|---|---|
| `VITE_PORT` | 开发服务器端口 | 5603 |
| `VITE_BASE` | 路由 base 路径 | / |
| `VITE_GLOB_API_URL` | 接口地址前缀（经 Gateway 9000 代理） | /api |
| `VITE_DEVTOOLS` | 是否开启 Vue Devtools | false |
| `VITE_INJECT_APP_LOADING` | 是否注入全局 loading | true |

## 路由配置

主应用通过 `/ydsz-proj` 前缀激活本应用，内部路由表（`src/router/routes/modules/project.ts`）：

| 路由路径 | 名称 | 说明 |
|---|---|---|
| `/opportunity/list` | 商机列表 | 商机管理 |
| `/contract/list` | 合同列表 | 合同管理 |
| `/initiation/list` | 立项列表 | 项目立项 |
| `/execution/wbs` | WBS 任务 | 执行管理 |
| `/execution/risk` | 风险管理 | 项目风险 |
| `/finance/budget` | 预算管理 | 财务管理 |
| `/finance/expense` | 费用管理 | 财务管理 |
| `/finance/revenue` | 收入管理 | 财务管理 |
| `/finance/invoice` | 发票管理 | 财务管理 |
| `/finance/payment` | 回款管理 | 财务管理 |
| `/evm/measure` | 挣值测量 | EVM 分析 |
| `/hr/rate-card` | 费率卡 | 人力管理 |

## 关键页面与组件

| 路径 | 功能 |
|---|---|
| `src/views/opportunity/index.vue` | 商机列表，支持分页查询、阶段筛选、销售漏斗 |
| `src/views/evm/index.vue` | 挣值测量分析，PV/EV/AC 趋势图与 SPI/CPI 指标 |
| `src/views/execution/index.vue` | WBS 任务树形展示与进度跟踪 |
| `src/views/finance/budget/index.vue`（`views/budget/`） | 预算编制与执行对比 |
| `src/layouts/basic.vue` | 子应用基础布局，复用 `@ydsz/layouts` |

每个业务模块均包含 `index.vue`（列表页）与 `*-form.vue`（表单页，新增/编辑）两个核心页面。

## API 调用

API 模块位于 `src/api/`，使用 `@ydsz/request` 的 `requestClient`，对应后端 `/api/v1/project/*` 端点：

```typescript
// 商机管理（src/api/opportunity.ts）
import { requestClient } from '#/api/request';

export function getOpportunityPageApi(params: OpportunityApi.OpportunityPageQuery) {
  return requestClient.get<{
    total: number; current: number; size: number;
    items: OpportunityApi.OpportunityVO[];
  }>(`/api/v1/project/project/opportunity/page`, { params });
}

export function createOpportunityApi(data: OpportunityApi.OpportunityDTO) {
  return requestClient.post<string>(`/api/v1/project/project/opportunity`, data);
}
```

其他 API 模块：`initiation.ts`（立项）、`contract.ts`（合同）、`execution.ts`（执行）、`evm.ts`（挣值）、`budget.ts`（预算）、`expense.ts`（费用）、`revenue.ts`（收入）、`invoice.ts`（发票）、`payment.ts`（回款）、`risk.ts`（风险）、`rateCard.ts`（费率卡）。认证与菜单 API 复用 `src/api/core/`（`@ydsz/shared-auth`）。

## 注意事项

1. **商机端点路径**：`src/api/opportunity.ts` 中路径为 `/api/v1/project/project/opportunity/*`（含双重 project 前缀），与后端 Controller `@RequestMapping` 对齐，修改时勿遗漏层级。
2. **财务模块归组**：预算/费用/收入/发票/回款同属「财务管理」路由组（`/finance/*`），但 API 文件各自独立，新增财务类目时需同步路由与 API 两处。
3. **EVM 数据依赖**：挣值分析依赖 WBS 任务进度与预算数据，需先完成执行与预算录入，否则 SPI/CPI 指标无意义。
4. **表单页复用**：各模块 `*-form.vue` 同时承载新增与编辑，通过路由 query 参数 `id` 区分模式，无 `id` 为新增，有 `id` 为编辑回填。
5. **认证复用**：本应用依赖 `@ydsz/shared-auth`，不重复实现登录，token 由主应用通过 Qiankun 全局状态注入。

## 变更记录

- **v1.0.0**（2026-08-02）：初始创建，对齐 capability model 第四章
