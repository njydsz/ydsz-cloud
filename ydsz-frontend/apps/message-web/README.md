# message-web 消息中心

> YDSZ PMIS 消息中心子应用，提供多渠道消息发送、模板管理、批量发送、用户偏好、路由规则与死信队列管理（对应后端：ydsz-message，路由前缀：/ydsz-msg）

## 应用定位

| 属性 | 值 |
|---|---|
| **类型** | 微前端子应用 |
| **包名** | @ydsz/message-web |
| **对应后端服务** | ydsz-message |
| **前端端口** | 5604 |
| **后端端口** | 9004 |
| **路由前缀** | /ydsz-msg |
| **版本** | 1.0.0 |

message-web 是消息中心的前端载体，承接站内/邮件/短信/企微/钉钉/飞书多渠道消息的发送记录查询、模板维护、批量发送、用户偏好配置、路由规则编排与死信队列重试等能力，为全平台提供统一的消息触达管理。

## 目录结构

```text
message-web/
├── src/
│   ├── adapter/                  # Element Plus / VXE Table 适配器
│   ├── api/                      # 业务 API
│   │   └── core/                 # 认证/菜单/用户 API（复用 @ydsz/shared-auth）
│   ├── layouts/                  # 子应用基础布局
│   ├── locales/                  # 国际化（zh-CN/en-US）
│   ├── router/                   # 路由守卫 + 模块化路由（message.ts）
│   ├── store/                    # 子应用 Store
│   ├── views/                    # 业务页面
│   │   ├── message/              # 消息发送记录（index.vue + message-form.vue）
│   │   ├── batch/                # 批量发送
│   │   ├── deadLetter/           # 死信队列
│   │   ├── template/             # 消息模板
│   │   ├── notification/         # 站内通知
│   │   ├── routeRule/            # 路由规则
│   │   ├── preference/           # 用户偏好
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
| 消息发送 | 多渠道消息发送记录查询 | `views/message/` |
| 批量发送 | 批量消息发送任务管理 | `views/batch/` |
| 消息模板 | 模板内容与变量占位维护 | `views/template/` |
| 站内通知 | 站内通知列表与已读管理 | `views/notification/` |
| 路由规则 | 消息渠道路由策略编排 | `views/routeRule/` |
| 用户偏好 | 用户消息接收偏好配置 | `views/preference/` |
| 死信队列 | 发送失败消息重试与丢弃 | `views/deadLetter/` |

## 启动方式

```bash
# 在 monorepo 根目录（ydsz-frontend/）执行
pnpm dev:message       # 启动消息中心子应用（端口 5604）

# 启动主应用 + 所有子应用（含 message-web）
pnpm dev

# 打包
pnpm build:message
```

子应用需配合主应用（main-web，5600）一起访问，主应用通过路径前缀 `/ydsz-msg` 激活本应用。

## 环境变量

环境变量位于 `.env.development`：

| 变量 | 说明 | 默认值 |
|---|---|---|
| `VITE_PORT` | 开发服务器端口 | 5604 |
| `VITE_BASE` | 路由 base 路径 | / |
| `VITE_GLOB_API_URL` | 接口地址前缀（经 Gateway 9000 代理） | /api |
| `VITE_DEVTOOLS` | 是否开启 Vue Devtools | false |
| `VITE_INJECT_APP_LOADING` | 是否注入全局 loading | true |

## 路由配置

主应用通过 `/ydsz-msg` 前缀激活本应用，内部路由表（`src/router/routes/modules/message.ts`）分为五组：

| 路由路径 | 名称 | 说明 |
|---|---|---|
| `/message/list` | 消息列表 | 发送记录 |
| `/message/batch` | 批量发送 | 批量任务 |
| `/message/dead-letter` | 死信队列 | 失败重试 |
| `/template/list` | 消息模板 | 模板维护 |
| `/notification/list` | 站内通知 | 通知管理 |
| `/route/rules` | 路由规则 | 渠道路由 |
| `/preference/list` | 消息偏好 | 用户偏好 |

## 关键页面与组件

| 路径 | 功能 |
|---|---|
| `src/views/message/index.vue` | 消息发送记录列表，支持按渠道与状态筛选 |
| `src/views/template/index.vue` | 消息模板列表，维护变量占位与渠道适配 |
| `src/views/batch/index.vue` | 批量发送任务列表与进度跟踪 |
| `src/views/deadLetter/index.vue` | 死信队列，支持重试与丢弃操作 |
| `src/views/routeRule/index.vue` | 路由规则编排，配置渠道路由策略 |
| `src/layouts/basic.vue` | 子应用基础布局，复用 `@ydsz/layouts` |

每个业务模块均包含 `index.vue`（列表页）与 `*-form.vue`（表单页）两个核心页面。

## API 调用

API 模块位于 `src/api/`，使用 `@ydsz/request` 的 `requestClient`，对应后端 `/api/v1/message/*` 端点：

```typescript
// 消息发送记录（src/api/message.ts）
import { requestClient } from '#/api/request';

export function getMessagePageApi(params: MessageApi.MessagePageQuery) {
  return requestClient.get<{
    total: number; current: number; size: number;
    items: MessageApi.MessageVO[];
  }>(`/api/v1/message/page`, { params });
}

export function createMessageApi(data: MessageApi.MessageDTO) {
  return requestClient.post<string>(`/api/v1/message`, data);
}
```

其他 API 模块：`template.ts`（消息模板）、`batch.ts`（批量发送）、`notification.ts`（站内通知）、`routeRule.ts`（路由规则）、`preference.ts`（用户偏好）、`deadLetter.ts`（死信队列）。认证与菜单 API 复用 `src/api/core/`（`@ydsz/shared-auth`）。

## 注意事项

1. **多渠道适配**：消息渠道包括站内/邮件/短信/企微/钉钉/飞书，不同渠道的模板变量与字段约束不同，表单页需按渠道动态渲染。
2. **死信队列操作**：死信消息支持重试与丢弃，重试前应检查失败原因（如手机号格式、模板变量缺失），避免反复失败。
3. **路由规则优先级**：路由规则按优先级匹配，新增规则时需设置合理的 `priority`，避免高优先级规则吞掉所有消息。
4. **用户偏好覆盖**：用户偏好（`preference`）可覆盖路由规则默认渠道，发送前需校验用户是否禁用该渠道。
5. **认证复用**：本应用依赖 `@ydsz/shared-auth`，不重复实现登录，token 通过 `useTokenStore()` 从 SecureLS 读取，由 `@ydsz/shared-auth` 统一管理。

## 变更记录

- **v1.0.0**（2026-08-02）：初始创建，对齐 capability model 第四章
