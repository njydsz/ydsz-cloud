# userinfo-web 用户中心

> YDSZ PMIS 用户中心子应用，提供用户、角色、菜单、部门、公司、岗位与语言管理（对应后端：ydsz-userinfo，路由前缀：/ydsz-user）

## 应用定位

| 属性 | 值 |
|---|---|
| **类型** | 微前端子应用 |
| **包名** | @ydsz/userinfo-web |
| **对应后端服务** | ydsz-userinfo |
| **前端端口** | 5601 |
| **后端端口** | 9002 |
| **路由前缀** | /ydsz-user |
| **版本** | 1.0.0 |

userinfo-web 是组织与权限管理的前端载体，承接用户账号、部门、角色、岗位、公司、菜单权限树与多语言管理等核心 RBAC 能力，为全平台提供统一的身份与权限数据基础。

## 目录结构

```text
userinfo-web/
├── src/
│   ├── adapter/                  # Element Plus / VXE Table 适配器
│   ├── api/                      # 业务 API
│   │   └── core/                 # 认证/菜单/用户 API（复用 @ydsz/shared-auth）
│   ├── layouts/                  # 子应用基础布局
│   ├── locales/                  # 国际化（zh-CN/en-US）
│   ├── router/                   # 路由守卫 + 模块化路由（system.ts）
│   ├── store/                    # 子应用 Store
│   ├── views/                    # 业务页面
│   │   └── system/               # 组织与权限
│   │       ├── user/             # 用户（index.vue + user-form.vue + role-assign.vue）
│   │       ├── dept/             # 部门
│   │       ├── role/             # 角色
│   │       ├── post/             # 岗位
│   │       ├── company/          # 公司
│   │       ├── menu/             # 菜单
│   │       └── language/         # 语言管理
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
| 用户管理 | 用户账号 CRUD、角色分配、密码重置 | `views/system/user/` |
| 部门管理 | 组织部门树维护 | `views/system/dept/` |
| 角色管理 | 角色 CRUD、权限码与菜单授权 | `views/system/role/` |
| 岗位管理 | 岗位定义与维护 | `views/system/post/` |
| 公司管理 | 公司主体与多租户基础 | `views/system/company/` |
| 菜单管理 | 菜单树与权限标识维护 | `views/system/menu/` |
| 语言管理 | 多语言资源管理 | `views/system/language/` |

## 启动方式

```bash
# 在 monorepo 根目录（ydsz-frontend/）执行
pnpm dev:userinfo       # 启动用户中心子应用（端口 5601）

# 启动主应用 + 所有子应用（含 userinfo-web）
pnpm dev

# 打包
pnpm build:userinfo
```

子应用需配合主应用（main-web，5600）一起访问，主应用通过路径前缀 `/ydsz-user` 激活本应用。

## 环境变量

环境变量位于 `.env.development`：

| 变量 | 说明 | 默认值 |
|---|---|---|
| `VITE_PORT` | 开发服务器端口 | 5601 |
| `VITE_BASE` | 路由 base 路径 | / |
| `VITE_GLOB_API_URL` | 接口地址前缀（经 Gateway 9000 代理） | /api |
| `VITE_DEVTOOLS` | 是否开启 Vue Devtools | false |
| `VITE_INJECT_APP_LOADING` | 是否注入全局 loading | true |

## 路由配置

主应用通过 `/ydsz-user` 前缀激活本应用，内部路由表（`src/router/routes/modules/system.ts`）分为两组：

| 路由路径 | 名称 | 说明 |
|---|---|---|
| `/organization/user` | 用户管理 | 用户账号 |
| `/organization/dept` | 部门管理 | 组织部门 |
| `/organization/role` | 角色管理 | 角色权限 |
| `/organization/post` | 岗位管理 | 岗位定义 |
| `/organization/company` | 公司管理 | 公司主体 |
| `/system-config/menu` | 菜单管理 | 菜单树 |
| `/system-config/language` | 语言管理 | 多语言 |

## 关键页面与组件

| 路径 | 功能 |
|---|---|
| `src/views/system/user/index.vue` | 用户列表，支持按部门/公司筛选与状态切换 |
| `src/views/system/user/role-assign.vue` | 用户角色分配弹窗，调用 `assignUserRolesApi` |
| `src/views/system/role/index.vue` | 角色列表与权限码授权 |
| `src/views/system/menu/index.vue` | 菜单树管理，维护层级与权限标识 |
| `src/views/system/dept/index.vue` | 部门树形展示与维护 |
| `src/layouts/basic.vue` | 子应用基础布局，复用 `@ydsz/layouts` |

每个业务模块均包含 `index.vue`（列表页）与 `*-form.vue`（表单页）两个核心页面，用户模块额外含 `role-assign.vue`（角色分配）。

## API 调用

API 模块位于 `src/api/`，使用 `@ydsz/request` 的 `requestClient`，对应后端 `/api/v1/*` 端点：

```typescript
// 用户管理（src/api/user.ts）
import { requestClient } from '#/api/request';

export function getUserPageApi(params: UserApi.UserAccountPageQuery) {
  return requestClient.get<{
    total: number; current: number; size: number;
    items: UserApi.UserAccountVO[];
  }>('/api/v1/user/page', { params });
}

export function assignUserRolesApi(userId: string, roleIds: string[]) {
  return requestClient.post<boolean>(`/api/v1/user/${userId}/roles`, { roleIds });
}

export function resetPasswordApi(data: UserApi.ResetPasswordDTO) {
  return requestClient.post<boolean>('/api/v1/user/reset-password', data);
}
```

其他 API 模块：`role.ts`（角色）、`menu.ts`（菜单）、`dept.ts`（部门）、`post.ts`（岗位）、`company.ts`（公司）、`language.ts`（语言）。认证与菜单 API 复用 `src/api/core/`（`@ydsz/shared-auth`）。

## 注意事项

1. **用户脱敏**：`UserAccountVO` 中手机号/邮箱已脱敏，列表展示与表单回填均需保持脱敏，不要在前端还原明文。
2. **角色分配接口**：`assignUserRolesApi` 为全量覆盖语义，传入的 `roleIds` 会替换该用户所有角色，调用前需先 `getUserRolesApi` 查询当前角色再合并。
3. **密码安全**：修改密码（`changePasswordApi`）与重置密码（`resetPasswordApi`）走不同端点，重置密码为管理员能力，需配合权限码控制。
4. **菜单树一致性**：菜单管理维护的权限标识需与各子应用路由 `meta` 中的权限码对齐，否则前端权限守卫会误判。
5. **认证复用**：本应用依赖 `@ydsz/shared-auth`，不重复实现登录，token 由主应用通过 micro-kernel globalState 注入。

## 变更记录

- **v1.0.0**（2026-08-02）：初始创建，对齐 capability model 第四章
