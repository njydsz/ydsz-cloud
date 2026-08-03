# system-web 系统管理

> YDSZ PMIS 系统管理子应用，提供系统配置、字典管理、变量管理与应用注册（对应后端：ydsz-system，路由前缀：/ydsz-sys）

## 应用定位

| 属性 | 值 |
|---|---|
| **类型** | 微前端子应用 |
| **包名** | @ydsz/system-web |
| **对应后端服务** | ydsz-system |
| **前端端口** | 5602 |
| **后端端口** | 9001 |
| **路由前缀** | /ydsz-sys |
| **版本** | 1.0.0 |

system-web 是系统级配置管理的前端载体，承接系统参数、字典类型与字典项、系统变量、应用注册等基础运维功能，为其他业务子应用提供统一的字典数据与配置下发能力。

## 目录结构

```text
system-web/
├── src/
│   ├── adapter/                  # Element Plus / VXE Table 适配器
│   ├── api/                      # 业务 API
│   │   └── core/                 # 认证/菜单/用户 API（复用 @ydsz/shared-auth）
│   ├── layouts/                  # 子应用基础布局
│   ├── locales/                  # 国际化（zh-CN/en-US）
│   ├── router/                   # 路由守卫 + 模块化路由（system.ts）
│   ├── store/                    # 子应用 Store
│   ├── views/                    # 业务页面
│   │   ├── config/               # 系统配置（index.vue + config-form.vue）
│   │   ├── dictType/             # 字典类型
│   │   ├── dictItem/             # 字典项
│   │   ├── variable/             # 系统变量
│   │   ├── app/                  # 应用注册
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
| 系统配置 | 系统参数键值对管理，区分公开/私有 | `views/config/` |
| 字典类型 | 字典分类定义与元数据维护 | `views/dictType/` |
| 字典项 | 字典类型下的具体字典项维护 | `views/dictItem/` |
| 系统变量 | 运行时系统变量管理 | `views/variable/` |
| 应用注册 | 微服务/微应用注册与路由配置 | `views/app/` |

## 启动方式

```bash
# 在 monorepo 根目录（ydsz-frontend/）执行
pnpm dev:system        # 启动系统管理子应用（端口 5602）

# 启动主应用 + 所有子应用（含 system-web）
pnpm dev

# 打包
pnpm build:system
```

子应用需配合主应用（main-web，5600）一起访问，主应用通过路径前缀 `/ydsz-sys` 激活本应用。

## 环境变量

环境变量位于 `.env.development`：

| 变量 | 说明 | 默认值 |
|---|---|---|
| `VITE_PORT` | 开发服务器端口 | 5602 |
| `VITE_BASE` | 路由 base 路径 | / |
| `VITE_GLOB_API_URL` | 接口地址前缀（经 Gateway 9000 代理） | /api |
| `VITE_DEVTOOLS` | 是否开启 Vue Devtools | false |
| `VITE_INJECT_APP_LOADING` | 是否注入全局 loading | true |

## 路由配置

主应用通过 `/ydsz-sys` 前缀激活本应用，内部路由表（`src/router/routes/modules/system.ts`）统一归在「系统管理」组下：

| 路由路径 | 名称 | 说明 |
|---|---|---|
| `/system/config` | 系统配置 | 参数键值对管理 |
| `/system/dict-type` | 字典类型 | 字典分类定义 |
| `/system/dict-item` | 字典项 | 字典项维护 |
| `/system/variable` | 系统变量 | 运行时变量 |
| `/system/app` | 应用注册 | 微应用注册 |

## 关键页面与组件

| 路径 | 功能 |
|---|---|
| `src/views/config/index.vue` | 系统配置列表，支持按 configKey/configGroup 筛选 |
| `src/views/dictType/index.vue` | 字典类型列表，维护字典分类元数据 |
| `src/views/dictItem/index.vue` | 字典项列表，按字典类型联动查询字典项 |
| `src/views/variable/index.vue` | 系统变量列表，管理运行时变量 |
| `src/views/app/index.vue` | 应用注册列表，配置微服务路由与元数据 |
| `src/layouts/basic.vue` | 子应用基础布局，复用 `@ydsz/layouts` |

每个业务模块均包含 `index.vue`（列表页）与 `*-form.vue`（表单页）两个核心页面。

## API 调用

API 模块位于 `src/api/`，使用 `@ydsz/request` 的 `requestClient`，对应后端 `/api/v1/*` 端点：

```typescript
// 系统配置（src/api/config.ts）
import { requestClient } from '#/api/request';

export function getConfigPageApi(params: ConfigApi.ConfigPageQuery) {
  return requestClient.get<{
    total: number; current: number; size: number;
    items: ConfigApi.ConfigVO[];
  }>('/api/v1/config/page', { params });
}

export function createConfigApi(data: ConfigApi.ConfigDTO) {
  return requestClient.post<string>('/api/v1/config', data);
}
```

其他 API 模块：`dictType.ts`（字典类型）、`dictItem.ts`（字典项）、`variable.ts`（系统变量）、`app.ts`（应用注册）。认证与菜单 API 复用 `src/api/core/`（`@ydsz/shared-auth`）。

## 注意事项

1. **字典类型与字典项联动**：字典项归属于字典类型，新增字典项前需先确保对应字典类型已存在，列表页通过 `dictType` 字段联动筛选。
2. **系统配置可见性**：`isPublic` 字段控制配置是否对外暴露，私有配置（`isPublic=0`）不应在前端明文展示敏感值，修改时注意脱敏。
3. **应用注册一致性**：应用注册中的路由前缀需与注册表 MICRO_APPS 的 activeRule 保持一致，否则子应用无法正确激活。
4. **认证复用**：本应用依赖 `@ydsz/shared-auth`，不重复实现登录，token 由主应用通过 micro-kernel globalState 注入。
5. **配置缓存**：系统配置与字典数据通常被其他子应用缓存引用，修改后可能需要通知刷新或等待缓存过期。

## 变更记录

- **v1.0.0**（2026-08-02）：初始创建，对齐 capability model 第四章
