# nextwiki-web 网盘知识库

> YDSZ PMIS 网盘知识库子应用，提供文件管理、分享管理、标签管理、配额管理与评论管理（对应后端：ydsz-nextwiki，路由前缀：/ydsz-wiki）

## 应用定位

| 属性 | 值 |
|---|---|
| **类型** | 微前端子应用 |
| **包名** | @ydsz/nextwiki-web |
| **对应后端服务** | ydsz-nextwiki |
| **前端端口** | 5607 |
| **后端端口** | 9003 |
| **路由前缀** | /ydsz-wiki |
| **版本** | 1.0.0 |

nextwiki-web 是网盘与知识库的前端载体，承接文件节点（目录/文件/快捷方式）管理、文件分享、标签体系、存储配额与文件评论等能力，为团队提供统一的文档协作与知识沉淀平台。

## 目录结构

```text
nextwiki-web/
├── src/
│   ├── adapter/                  # Element Plus / VXE Table 适配器
│   ├── api/                      # 业务 API
│   │   └── core/                 # 认证/菜单/用户 API（复用 @ydsz/shared-auth）
│   ├── layouts/                  # 子应用基础布局
│   ├── locales/                  # 国际化（zh-CN/en-US）
│   ├── router/                   # 路由守卫 + 模块化路由（nextwiki.ts）
│   ├── store/                    # 子应用 Store
│   ├── views/                    # 业务页面
│   │   ├── file/                 # 文件管理（index.vue + file-form.vue）
│   │   ├── comment/              # 文件评论
│   │   ├── share/                # 分享管理
│   │   ├── tag/                  # 标签管理
│   │   ├── quota/                # 配额管理
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
| 文件管理 | 目录/文件/快捷方式节点管理与版本控制 | `views/file/` |
| 文件评论 | 文件级评论与讨论 | `views/comment/` |
| 分享管理 | 文件/文件夹分享链接与权限控制 | `views/share/` |
| 标签管理 | 文件标签体系与分类 | `views/tag/` |
| 配额管理 | 用户/租户存储配额管理 | `views/quota/` |

## 启动方式

```bash
# 在 monorepo 根目录（ydsz-frontend/）执行
pnpm dev:nextwiki      # 启动网盘知识库子应用（端口 5607）

# 启动主应用 + 所有子应用（含 nextwiki-web）
pnpm dev

# 打包
pnpm build:nextwiki
```

子应用需配合主应用（main-web，5600）一起访问，主应用通过路径前缀 `/ydsz-wiki` 激活本应用。

## 环境变量

环境变量位于 `.env.development`：

| 变量 | 说明 | 默认值 |
|---|---|---|
| `VITE_PORT` | 开发服务器端口 | 5607 |
| `VITE_BASE` | 路由 base 路径 | / |
| `VITE_GLOB_API_URL` | 接口地址前缀（经 Gateway 9000 代理） | /api |
| `VITE_DEVTOOLS` | 是否开启 Vue Devtools | false |
| `VITE_INJECT_APP_LOADING` | 是否注入全局 loading | true |

## 路由配置

主应用通过 `/ydsz-wiki` 前缀激活本应用，内部路由表（`src/router/routes/modules/nextwiki.ts`）分为四组：

| 路由路径 | 名称 | 说明 |
|---|---|---|
| `/file/list` | 文件列表 | 文件管理 |
| `/file/comment` | 文件评论 | 评论讨论 |
| `/share/list` | 分享列表 | 分享管理 |
| `/tag/list` | 标签列表 | 标签管理 |
| `/storage/quota` | 存储配额 | 配额管理 |

## 关键页面与组件

| 路径 | 功能 |
|---|---|
| `src/views/file/index.vue` | 文件浏览器，目录树导航与文件列表 |
| `src/views/file/file-form.vue` | 文件/目录创建与编辑表单 |
| `src/views/share/index.vue` | 分享列表，管理分享链接与有效期/权限 |
| `src/views/tag/index.vue` | 标签列表，维护标签体系与分类 |
| `src/views/quota/index.vue` | 配额管理，查看与调整用户/租户存储配额 |
| `src/layouts/basic.vue` | 子应用基础布局，复用 `@ydsz/layouts` |

每个业务模块均包含 `index.vue`（列表页）与 `*-form.vue`（表单页）两个核心页面。

## API 调用

API 模块位于 `src/api/`，使用 `@ydsz/request` 的 `requestClient`，对应后端 `/api/v1/nextwiki/*` 端点：

```typescript
// 文件管理（src/api/file.ts）
import { requestClient } from '#/api/request';

export function getFilePageApi(params: FileApi.FilePageQuery) {
  return requestClient.get<{
    total: number; current: number; size: number;
    items: FileApi.FileVO[];
  }>(`/api/v1/nextwiki/files/page`, { params });
}

export function createFileApi(data: FileApi.FileDTO) {
  return requestClient.post<string>(`/api/v1/nextwiki/files`, data);
}
```

其他 API 模块：`comment.ts`（文件评论）、`share.ts`（分享管理）、`tag.ts`（标签管理）、`quota.ts`（配额管理）。认证与菜单 API 复用 `src/api/core/`（`@ydsz/shared-auth`）。

## 注意事项

1. **文件端点路径**：文件 API 路径为 `/api/v1/nextwiki/files/*`（注意 `files` 为复数），与后端 Controller `@RequestMapping` 对齐，修改时勿遗漏层级。
2. **节点类型区分**：文件节点支持目录/文件/快捷方式三种类型，表单页需按节点类型动态渲染字段，目录无文件大小，快捷方式需指向目标节点。
3. **分享权限控制**：分享链接需配置有效期与访问权限（预览/下载/编辑），过期链接应在前端拦截并提示。
4. **配额校验**：上传文件前需校验用户剩余配额，超限应在前端拦截并提示，避免上传后端再拒绝。
5. **认证复用**：本应用依赖 `@ydsz/shared-auth`，不重复实现登录，token 通过 `useTokenStore()` 从 SecureLS 读取，由 `@ydsz/shared-auth` 统一管理。

## 变更记录

- **v1.0.0**（2026-08-02）：初始创建，对齐 capability model 第四章
