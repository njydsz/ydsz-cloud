# main-web 主应用

> YDSZ PMIS 微前端宿主应用，承载认证、布局、全局状态与子应用路由分发（对应后端：ydsz-gateway，路由前缀：/）

## 应用定位

| 属性 | 值 |
|---|---|
| **类型** | 微前端主应用（Qiankun Host） |
| **包名** | @ydsz/main-web |
| **对应后端服务** | ydsz-gateway |
| **前端端口** | 5600 |
| **后端端口** | 9000 |
| **路由前缀** | / |
| **版本** | 1.0.0 |

主应用是整个 YDSZ PMIS 前端的入口与宿主，负责登录认证、全局布局、菜单分发、子应用注册与激活、全局状态通信（Qiankun initGlobalState）以及路由守卫。9 个业务子应用通过路径前缀匹配挂载到 `#subapp-container` 容器中。

## 目录结构

```text
main/
├── src/
│   ├── adapter/              # Element Plus / VXE Table 组件适配器
│   ├── api/                  # 核心 API（auth/user/menu/notification/search）
│   │   └── core/             # 认证/菜单/用户/通知/搜索 API
│   ├── components/           # 全局组件（global-search）
│   ├── layouts/              # 布局（basic + auth）
│   ├── locales/              # 国际化（zh-CN/en-US）
│   ├── qiankun/              # Qiankun 子应用注册与全局状态
│   ├── router/               # 路由守卫 + 模块化路由
│   │   └── routes/modules/   # dashboard / subapps / demos
│   ├── store/                # 全局 Store（auth/notification）
│   ├── views/                # 全局页面
│   │   ├── dashboard/        # 仪表盘（analytics/workspace）
│   │   └── _core/            # 认证/异常/子应用容器
│   ├── app.vue
│   ├── bootstrap.ts
│   ├── main.ts
│   └── preferences.ts
├── .env.development
├── index.html
└── vite.config.mts
```

## 关键依赖

主应用作为微前端宿主，复用 monorepo 共享包：

| 依赖包 | 作用 |
|---|---|
| `@ydsz/request` | HTTP 请求客户端（Axios 封装），承载认证拦截 |
| `@ydsz/access` | 权限码与路由访问控制 |
| `@ydsz/layouts` | 布局组件（basic + auth） |
| `@ydsz/common-ui` | 通用 UI 组件 |
| `@ydsz/monitor` | 前端监控（错误捕获 + Web Vitals） |
| `@ydsz/stores` | 全局状态管理 |
| `@ydsz/locales` | 国际化基础包 |
| `qiankun` | 微前端框架（子应用注册/激活/预加载） |
| `element-plus` | UI 组件库 |
| `pinia` | 状态管理 |

## 核心功能模块

| 模块 | 功能描述 | 关键目录/文件 |
|---|---|---|
| 登录认证 | 账号密码/验证码/二维码/注册/忘记密码 | `src/views/_core/authentication/` |
| 仪表盘 | 数据分析（Analytics）与工作台（Workspace） | `src/views/dashboard/` |
| 子应用管理 | Qiankun 子应用注册、激活、预加载 | `src/qiankun/index.ts` |
| 路由守卫 | 登录态校验、权限码控制、子应用 catch-all | `src/router/guard.ts` |
| 布局系统 | 基础布局（basic）与认证布局（auth） | `src/layouts/` |
| 全局搜索 | 跨模块菜单/资源快速检索 | `src/components/global-search.vue` |
| 国际化 | zh-CN / en-US 双语切换 | `src/locales/` |
| 全局状态 | 认证状态、通知状态、Qiankun 全局状态 | `src/store/`、`src/qiankun/global-state.ts` |
| 异常页面 | 404/403/500/离线/敬请期待 | `src/views/_core/fallback/` |

## 启动方式

```bash
# 在 monorepo 根目录（ydsz-frontend/）执行
pnpm dev:main          # 仅启动主应用（端口 5600）

# 启动主应用 + 所有子应用
pnpm dev

# 打包主应用
pnpm build:main
```

主应用独立启动时不会渲染任何业务子应用，需配合对应子应用一起启动才能访问业务页面。

## 环境变量

环境变量位于 `.env.development` / `.env.production` / `.env.analyze`：

| 变量 | 说明 | 默认值 |
|---|---|---|
| `VITE_PORT` | 开发服务器端口 | 5600 |
| `VITE_BASE` | 路由 base 路径 | / |
| `VITE_GLOB_API_URL` | 接口地址前缀（经 Gateway 9000 代理） | /api |
| `VITE_DEVTOOLS` | 是否开启 Vue Devtools | true |
| `VITE_INJECT_APP_LOADING` | 是否注入全局 loading | true |

## 路由配置

主应用路由分为三类模块（`src/router/routes/modules/`）：

| 模块文件 | 路径前缀 | 说明 |
|---|---|---|
| `dashboard.ts` | `/dashboard` | 数据分析、工作台 |
| `subapps.ts` | `/ydsz-user` `/ydsz-sys` `/ydsz-proj` 等 | 9 个子应用 catch-all 激活路由 |
| `demos.ts` | `/demos` | 演示页面 |

子应用激活规则（路径前缀匹配 → 挂载对应子应用）：

| 路径前缀 | 子应用 | 默认重定向 |
|---|---|---|
| `/ydsz-user/*` | userinfo-web | /ydsz-user/users |
| `/ydsz-sys/*` | system-web | /ydsz-sys/configs |
| `/ydsz-proj/*` | project-web | /ydsz-proj/opportunities |
| `/ydsz-msg/*` | message-web | /ydsz-msg/messages |
| `/ydsz-cron/*` | cronjob-web | /ydsz-cron/jobs |
| `/ydsz-flow/*` | workflow-web | /ydsz-flow/templates |
| `/ydsz-wiki/*` | nextwiki-web | /ydsz-wiki/files |
| `/ydsz-rule/*` | literule-web | /ydsz-rule/rules |
| `/ydsz-ai/*` | agent-web | /ydsz-ai/chat |

## 关键页面与组件

| 路径 | 功能 |
|---|---|
| `src/views/dashboard/analytics/index.vue` | 数据分析仪表盘，含趋势/访问量/销售/来源统计组件 |
| `src/views/dashboard/workspace/index.vue` | 个人工作台 |
| `src/views/_core/authentication/login.vue` | 登录页（账号密码 + 验证码） |
| `src/views/_core/subapp/index.vue` | 子应用挂载容器，承载 Qiankun 渲染 |
| `src/components/global-search.vue` | 全局搜索弹窗组件 |
| `src/layouts/basic.vue` | 主框架布局（顶栏 + 侧栏 + 标签页 + 内容区） |

## API 调用

主应用核心 API 集中在 `src/api/core/`，使用 `@ydsz/request` 的 `requestClient`：

```typescript
// 登录（src/api/core/auth.ts）
import { requestClient } from '#/api/request';
export async function loginApi(data: AuthApi.LoginParams) {
  return requestClient.post<AuthApi.LoginResult>('/api/v1/auth/login', data);
}

// 刷新 token
export async function refreshTokenApi(refreshToken: string) {
  return baseRequestClient.post<AuthApi.RefreshTokenResult>(
    '/api/v1/auth/refresh',
    { refreshToken },
  );
}

// 获取权限码
export async function getAccessCodesApi() {
  return requestClient.get<string[]>('/api/v1/auth/codes');
}
```

其他核心 API：`user.ts`（用户信息）、`menu.ts`（菜单树）、`notification.ts`（通知）、`search.ts`（全局搜索）。所有请求经 Vite proxy 统一代理到 Gateway 9000 端口。

## 注意事项

1. **子应用激活依赖路径前缀**：新增子应用时需同步在 `src/qiankun/index.ts`（microApps 配置）和 `src/router/routes/modules/subapps.ts`（catch-all 路由）两处注册，二者 `activeRule` 必须一致。
2. **认证状态共享**：登录后 `accessToken` / `userInfo` 通过 `@ydsz/shared-auth` 的 `requestClient` 拦截器与 Qiankun `initGlobalState` 同步给所有子应用，子应用无需重复登录。
3. **API 代理统一走 Gateway**：开发环境所有 `/api/*` 请求由 Vite proxy 转发到 `http://localhost:9000`（Gateway），不要直接指向子应用后端端口。
4. **路由守卫顺序**：`src/router/guard.ts` 中先校验登录态再校验权限码，子应用 catch-all 路由使用 `hideInMenu` 避免菜单重复渲染。
5. **生产环境子应用地址**：`src/qiankun/index.ts` 中 `prodUrls` 使用相对路径（如 `/ydsz-userinfo-web/`），部署时需保证 Nginx 反向代理到各子应用静态资源目录。

## 变更记录

- **v1.0.0**（2026-08-02）：初始创建，对齐 capability model 第四章
