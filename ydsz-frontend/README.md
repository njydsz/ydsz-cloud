<div align="center">
  <h1>YDSZ PMIS Frontend</h1>
  <p>YDSZ 项目管理信息系统 — 前端微应用架构</p>
</div>

基于 Qiankun 微前端框架，采用 Vue 3 + Element Plus + TypeScript 技术栈，每个后端微服务对应一个独立的前端微应用，实现「独立开发」、「独立测试」、「独立部署」。

## 技术栈

| 类别 | 技术 |
| --- | --- |
| 框架 | Vue 3 + TypeScript |
| 构建工具 | Vite 6 |
| 包管理 | pnpm 10 + Turbo (Monorepo) |
| 微前端 | Qiankun 2.10 |
| UI 组件库 | Element Plus 2.10 |
| 状态管理 | Pinia 3 + pinia-plugin-persistedstate |
| 路由 | Vue Router 4 |
| 样式 | Tailwind CSS 3 + SCSS |
| HTTP 客户端 | Axios |
| 表单验证 | Vee-validate + Zod |
| 图表 | ECharts 5 |
| 表格组件 | VXE Table 4 |
| 国际化 | Vue I18n 11 |
| Mock 服务 | Nitro |
| 代码规范 | ESLint 9 + Prettier + Stylelint + Commitlint |

## 微前端架构

```
┌──────────────────────────────────────────────────────────────┐
│                    main-web（主应用/宿主）                      │
│         端口 5600  │  认证/布局/全局状态/路由分发/Qiankun Host       │
│  ┌──────────┐  ┌───────────┐  ┌──────────────────────────┐    │
│  │ Qiankun  │  │ Vue Router │  │ 全局 Store / 偏好 / i18n  │    │
│  │  Host    │  │ (主路由)   │  │                          │    │
│  └────┬─────┘  └─────┬─────┘  └──────────────────────────┘    │
│  ┌────▼───────────────▼───────────────────────────────────┐   │
│  │            子应用挂载容器 (#subapp-container)              │   │
│  └────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────┘
    │           │          │          │          │          │          │          │          │
 ┌──▼──┐    ┌──▼──┐   ┌──▼───┐   ┌──▼───┐   ┌──▼───┐   ┌──▼───┐   ┌──▼───┐   ┌──▼───┐   ┌──▼───┐
 │user │    │sys  │   │proj  │   │msg   │   │cron  │   │flow  │   │wiki  │   │rule  │   │agent │
 │center│   │admin│   │mgmt  │   │center│   │job   │   │design│   │drive │   │engine│   │assist│
 │5601 │    │5602 │   │5603  │   │5604  │   │5605  │   │5606  │   │5607  │   │5608  │   │5610  │
 └─────┘    └─────┘   └──────┘   └──────┘   └──────┘   └──────┘   └──────┘   └──────┘   └──────┘
    │           │          │          │          │          │          │          │          │
 ┌──▼──┐    ┌──▼──┐   ┌──▼───┐   ┌──▼───┐   ┌──▼───┐   ┌──▼───┐   ┌──▼───┐   ┌──▼───┐   ┌──▼───┐
 │:9001│    │:9002│   │:9003 │   │:9004 │   │:9005 │   │:9006 │   │:9007 │   │:9008 │   │:9010 │
 └─────┘    └─────┘   └──────┘   └──────┘   └──────┘   └──────┘   └──────┘   └──────┘   └──────┘
```

## 应用职责

| 应用 | 前端端口 | 后端端口 | 后端服务 | 路由前缀 | 职责 |
| --- | --- | --- | --- | --- | --- |
| **main-web** | 5600 | 9000 | ydsz-gateway | / | 微前端宿主，认证/布局/全局状态/路由分发 |
| **userinfo-web** | 5601 | 9001 | ydsz-userinfo | /ydsz-user | 用户/部门/角色/菜单/岗位/OAuth2 |
| **system-web** | 5602 | 9002 | ydsz-system | /ydsz-sys | 系统配置/字典/变量/应用注册 |
| **project-web** | 5603 | 9003 | ydsz-project | /ydsz-proj | 商机/合同/预算/执行/EVM/成本/利润 |
| **message-web** | 5604 | 9004 | ydsz-message | /ydsz-msg | 消息/模板/通知/路由/灰度/追踪 |
| **cronjob-web** | 5605 | 9005 | ydsz-cronjob | /ydsz-cron | 任务/DAG/日志/告警/拓扑 |
| **workflow-web** | 5606 | 9006 | ydsz-workflow | /ydsz-flow | 流程模板/设计器/实例/待办/SLA |
| **nextwiki-web** | 5607 | 9007 | ydsz-nextwiki | /ydsz-wiki | 文件/预览/搜索/分享/锁定 |
| **literule-web** | 5608 | 9008 | ydsz-literule | /ydsz-rule | 规则/DSL/变量/CEP/断点 |
| **agent-web** | 5610 | 9010 | ydsz-agent | /ydsz-ai | 对话/Agent/RAG/DAG/审批 |

## 目录结构

```
ydsz-frontend/
├── main/                              # 主应用（微前端宿主）
│   ├── src/
│   │   ├── qiankun/                  # Qiankun 子应用注册配置
│   │   ├── adapter/                  # Element Plus 组件适配器
│   │   ├── api/                      # 核心 API（auth/user/menu）
│   │   ├── layouts/                  # 布局（basic + auth）
│   │   ├── router/                   # 主路由 + 守卫 + 子应用路由
│   │   ├── store/                    # 全局 Store（auth）
│   │   ├── views/                    # 全局页面（dashboard/login/404/subapp 容器）
│   │   ├── locales/                  # 国际化
│   │   └── preferences.ts            # 偏好覆盖
│   └── ...
├── apps/                              # 9 个业务子应用
│   ├── userinfo-web/                # 用户中心 → ydsz-userinfo:9001
│   ├── system-web/                  # 系统管理 → ydsz-system:9002
│   ├── project-web/                 # 项目管理 → ydsz-project:9003
│   ├── message-web/                 # 消息中心 → ydsz-message:9004
│   ├── cronjob-web/                 # 定时任务 → ydsz-cronjob:9005
│   ├── workflow-web/                # 工作流引擎 → ydsz-workflow:9006
│   ├── nextwiki-web/                # 网盘知识库 → ydsz-nextwiki:9007
│   ├── literule-web/                # 规则引擎 → ydsz-literule:9008
│   ├── agent-web/                   # AI 助手 → ydsz-agent:9010
├── comm/                              # 公共共享包
│   ├── @core/                        # 核心 SDK（base/composables/preferences/ui-kit）
│   ├── effects/                      # 副作用（access/common-ui/hooks/layouts/plugins/request）
│   ├── constants/                    # 常量定义
│   ├── icons/                        # 图标配置
│   ├── locales/                      # 国际化基础包
│   ├── stores/                       # 全局状态管理
│   ├── styles/                       # 全局样式
│   ├── types/                        # 类型定义
│   └── utils/                        # 工具函数
├── conf/                              # 工程配置
│   ├── vite-config/                  # Vite 共享构建配置
│   ├── tailwind-config/              # Tailwind CSS 共享配置
│   ├── tsconfig/                     # TypeScript 共享配置
│   ├── lint-configs/                 # ESLint + Prettier + Stylelint + Commitlint
│   └── node-utils/                   # Node.js 工具
├── bash/                              # 脚本工具
│   ├── deploy/                       # Docker 部署
│   ├── turbo-run/                    # Turbo 并行运行
│   └── vsh/                          # 项目运维工具
├── pnpm-workspace.yaml
├── turbo.json
└── package.json
```

## 环境要求

- Node.js >= 20.10.0
- pnpm >= 9.12.0

## 安装使用

```bash
# 全局启用 corepack
npm i -g corepack

# 安装依赖
pnpm install

# 启动所有应用（主应用 + 所有子应用 + Mock API）
pnpm dev

# 启动单个应用
pnpm dev:main       # 主应用（端口 5600）
pnpm dev:userinfo   # 用户中心（端口 5601）
pnpm dev:system     # 系统管理（端口 5602）
pnpm dev:project    # 项目管理（端口 5603）
pnpm dev:message    # 消息中心（端口 5604）
pnpm dev:cronjob    # 定时任务（端口 5605）
pnpm dev:workflow   # 工作流引擎（端口 5606）
pnpm dev:nextwiki   # 网盘知识库（端口 5607）
pnpm dev:literule   # 规则引擎（端口 5608）
pnpm dev:agent      # AI 助手（端口 5610）
pnpm dev:mock       # Mock API（端口 5320）

# 打包
pnpm build                   # 打包所有应用
pnpm build:main              # 主应用
pnpm build:userinfo  # 用户中心
pnpm build:system    # 系统管理
pnpm build:project   # 项目管理
pnpm build:message   # 消息中心
pnpm build:cronjob   # 定时任务
pnpm build:workflow  # 工作流引擎
pnpm build:nextwiki  # 网盘知识库
pnpm build:literule  # 规则引擎
pnpm build:agent     # AI 助手

# 代码检查
pnpm lint                    # ESLint + Stylelint
pnpm format                  # Prettier 格式化
pnpm check                   # 全面检查
pnpm check:type              # TypeScript 类型检查

# 测试
pnpm test:unit               # 单元测试
pnpm test:e2e                # E2E 测试
```

## 路由规则

主应用通过路径前缀匹配激活对应的子应用：

| 路径前缀 | 子应用 |
| --- | --- |
| `/ydsz-user/*` | userinfo-web |
| `/ydsz-sys/*` | system-web |
| `/ydsz-proj/*` | project-web |
| `/ydsz-msg/*` | message-web |
| `/ydsz-cron/*` | cronjob-web |
| `/ydsz-flow/*` | workflow-web |
| `/ydsz-wiki/*` | nextwiki-web |
| `/ydsz-rule/*` | literule-web |
| `/ydsz-ai/*` | agent-web |

## API 代理

开发环境通过 Vite proxy 统一代理到 Gateway 9000 端口：

```typescript
// vite.config.mts
server: {
  proxy: {
    '/api': {
      target: 'http://localhost:9000',  // Gateway
      changeOrigin: true,
      rewrite: (path) => path.replace(/^\/api/, ''),
      ws: true,
    },
  },
}
```

## 浏览器支持

支持现代浏览器，不支持 IE（Chrome 80+）

## Git 规范

- `feat` 增加新功能
- `fix` 修复问题/BUG
- `style` 代码风格相关无影响运行结果的
- `perf` 优化/性能提升
- `refactor` 重构
- `revert` 撤销修改
- `test` 测试相关
- `docs` 文档/注释
- `chore` 依赖更新/脚手架配置修改等
- `ci` 持续集成
