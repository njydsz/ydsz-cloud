# YDSZ 前端微应用框架优化路线图

> 对标行业主流微前端方案（Qiankun / Wujie / Micro-App / Garfish / Module Federation）与互联网大厂研发规范（阿里、字节、腾讯系中后台最佳实践），基于 `ydsz-frontend` 全量源码调研输出。
> 调研日期：2026-08-03 ｜ 适用版本：v1.0.0-SNAPSHOT

---

## 0. 现状评估摘要

### 0.1 已具备的行业级亮点（保持）

| 能力 | 现状 | 行业对标 |
|------|------|----------|
| 微前端内核抽象层 | 自研 `micro-runtime`（接口层）+ `micro-adapter-qiankun` + `micro-kernel-lite` 双内核，同产物双内核兼容 | 优于多数团队的裸用 qiankun，接近字节 Garfish 的内核/适配分层思想 |
| Monorepo 工程化 | pnpm catalog（160+ 条目统一版本）+ Turborepo 缓存编排 + lefthook + commitlint + ESLint 9 flat config | 对齐 vben-admin 5.x / 大厂中后台模板水准 |
| 请求层统一封装 | `createSharedRequestClient` 统一 token 注入、401 刷新、错误提示，子应用零重复接入 | 对齐 Ant Design Pro 的 request 规范 |
| API 契约防漂移 | `gen-api.mjs`（SpringDoc OpenAPI → TS SDK）+ `--check` 契约校验 | 对齐大厂前后端契约治理实践 |
| 权限体系 | 后端菜单驱动动态路由 + v-access 按钮级指令 + AccessControl 组件 | 完整度对齐 vben / RuoYi-Vue-Pro |
| 观测性 | `@ydsz/monitor` 错误捕获 + Web Vitals | 基础具备，待接入微前端专项指标 |

### 0.2 核心短板（本报告重点）

1. **微前端通信层名存实亡**：`useGlobalState`/event-bus 有封装无业务消费，`main/src/qiankun/global-state.ts` 为死代码，token 共享靠「同 namespace 的 SecureLS 持久化」隐式约定。
2. **子应用无保活、无失败降级**：qiankun 内核不支持 keep-alive；未注册 `addGlobalUncaughtErrorHandler`；lite-kernel 已有的 error-boundary 因当前内核是 qiankun 而完全失效。
3. **注册表三处硬编码**：应用清单/端口/前缀分散在 `qiankun/index.ts`、`subapps.ts`、`use-tabbar-micro-sync.ts`，需手工同步，不支持动态/远程注册。
4. **质量门禁断链**：CI 跑 `pnpm run typecheck` 但根脚本叫 `check:type`（lint job 必挂）；lefthook pre-push 引用不存在的 `test:run`；turbo 未编排 lint/test 任务。
5. **测试覆盖断层**：37 个测试文件全部集中在 comm/@core，main/ 与 9 个 apps **零测试**，e2e 仅 1 个 spec。
6. **组件体系三套并存**：Element Plus + shadcn-ui(radix-vue) + vxe-table，包体与心智成本双高。

---

## 1. 架构优化

### 1.1 微前端内核路线收敛（P1，方向性决策）

当前「qiankun + 自研 lite 双内核」在业界无成功先例——字节（Modern.js/Garfish）、阿里（qiankun/ice）、京东（micro-app）均为单内核深耕。双内核导致：适配层大量空实现（`unmountApp`、`setKeepAlive`、`getActiveAppName` 返回不支持）、lite-kernel 的 error-boundary 永不生效、维护成本翻倍。

**建议三选一：**

| 方案 | 说明 | 适用判断 |
|------|------|----------|
| **A. 收敛 qiankun 单内核（推荐）** | 补齐 qiankun 适配层缺失能力（见 1.2/1.3），下线 lite-kernel 或仅保留接口 | 团队熟悉 qiankun、子应用均为自研 Vue3，改造成本最低 |
| B. 迁移 Wujie | 原生支持保活、iframe 沙箱隔离更强、去中心化通信 | 若保活与强隔离是硬需求且接受重写接入层 |
| C. 迁移 Module Federation 2.0 | 构建时共享依赖天然解决、运行时动态 remote | 若未来子应用数量 >20 且需跨团队独立发布，长期收益最大但短期成本高 |

> 落地动作（方案 A）：删除 `micro-kernel-lite` 包或降级为内部实验；`micro-runtime` 接口裁剪到 qiankun 真实能力集；接口层补 `onError`、`prefetchStrategy` 等声明。

### 1.2 补齐子应用保活能力（P1）

行业现状：wujie/micro-app 原生支持 keep-alive；qiankun 官方不支持，主流实践是 **多例缓存**（`loadMicroApp` 手动管理 + 切换时 `unmount` 挂起而非销毁，或容器层 display:none 缓存多实例）。

**落地建议：**
- 短期（qiankun 方案）：基于 `loadMicroApp` 重写容器组件 `views/_core/subapp/index.vue`，维护 `Map<appName, MicroAppInstance>`，配合现有 tabbar 体系实现「切 tab 不丢状态」；显式设置每应用缓存上限（LRU，建议 ≤3），防止内存膨胀。
- `use-tabbar-micro-sync.ts` 已预留 tab 关闭 → unmount 钩子，补齐实现即可闭环。
- 验收标准：项目列表页输入筛选条件 → 切换到消息中心 → 切回，列表状态与滚动位置保留。

### 1.3 统一异常治理与失败降级（P0）

对齐大厂 SRE 规范「任何子应用故障不得拖垮基座」：

1. 基座注册 `addGlobalUncaughtErrorHandler`：区分加载失败 / 运行时错误，加载失败渲染降级 UI（重试按钮 + 错误上报），运行时错误上报 `@ydsz/monitor` 并携带 appName。
2. 将 lite-kernel 已有的「失败 → 降级 UI → 本会话标记降级 → 整页跳转」逻辑移植到 qiankun 适配层。
3. 子应用 `errorHandler` 当前仅 console，改为上报 monitor（错误边界 + sourcemap 解析）。
4. 增加子应用加载超时熔断（建议 10s），超时走降级 UI。

### 1.4 注册表配置化与动态注册（P1）

现状：应用清单/端口/前缀三处硬编码（`qiankun/index.ts`、`subapps.ts`、PATH_TO_APP），新增子应用需改 3+ 处且无法运行时上下线。

**落地建议：**
- 建立单一事实源：`conf/micro-apps.config.ts` 生成注册表，基座路由、tabbar 映射、vite 端口全部从此消费。
- 中期演进为**远程 manifest**：基座启动时请求 `ydsz-system` 服务（已有「应用注册」能力域）获取应用清单，实现子应用运行时注册/灰度上下线/按权限挂载——这正是阿里 qiankun 生产实践与字节 Garfish 平台化的标准做法。
- 收益：新增子应用零基座改动；支持按租户/角色下发不同应用集。

### 1.5 通信与状态共享机制落地（P1）

现状是「协议齐备、无人使用」：GlobalStateHandle 封装完整但业务零消费；各应用独立 Pinia，靠 SecureLS 落盘键软同步内存态不互通。

**落地建议：**
- 删除死代码 `main/src/qiankun/global-state.ts` 与未消费的 event-bus，或反向——把真实跨应用诉求（用户信息变更广播、字典刷新、消息未读数、主题切换）全部收敛到 `GlobalStateHandle`，并写入开发规范「跨应用状态只允许走 globalState」。
- 内存态共享改为**基座注入**：基座通过 props 将 userStore/preferences 的只读快照注入子应用 mount，子应用变更经 globalState 回传基座统一落盘，消除「9 个应用各自读 SecureLS 赌时序」的隐患。
- `resetAllStores` 当前劫持 `pinia._s.set` 属侵入式 hack，建议改用 Pinia 官方插件机制登记 store。

### 1.6 Token 安全模型升级（P1）

现状自述「伪安全」：SecureLS AES 密钥随 bundle 分发，本地 token 可被逆向；且依赖 namespace 公式隐式约定，无运行时校验。

**落地建议（对齐大厂中后台标准）：**
- 短期：access token 仅存内存（基座持有），刷新走 httpOnly Cookie 的 refreshToken + rotation；子应用经 props/globalState 获取，不落 localStorage。
- 增加 namespace 版本漂移的运行时校验（启动时主子互验，不一致强制重新登录）。
- 结合后端已有 TOTP 2FA 与 API 签名能力，前端补齐敏感操作的二次确认链路。

---

## 2. 功能增强

### 2.1 微前端可观测性专项（P1）

在 `@ydsz/monitor` 基础上补齐微前端维度指标（对齐 SkyWalking/Sentry 前端实践）：

| 指标 | 说明 |
|------|------|
| 子应用加载耗时 | mount 前后打点，按应用分桶统计 P75/P95 |
| 沙箱逃逸检测 | proxy 沙箱异常、全局污染告警 |
| 资源加载失败率 | entry/chunk 404/5xx 按应用聚合 |
| 主子通信量 | globalState 变更频次，防滥用 |

### 2.2 组件体系收敛（P1，先决策后执行）

三套并存（Element Plus 为主 + shadcn-ui/radix-vue + vxe-table）带来包体冗余（element-vendor + radix + vxe 三个 vendor chunk）与交互不一致。大厂中后台通行做法是「**一套主组件库 + 一个表格增强**」。

**建议：**
- 主库锁定 Element Plus（团队熟练度 + 生态）；表格场景统一 vxe-table（大数据量虚拟滚动优势明显）。
- shadcn-ui 仅保留基座布局/偏好设置等存量使用面，新增页面禁止引入，写入 ESLint `no-restricted-imports` 强制约束；存量随迭代逐步替换下线。
- `form-ui`（vee-validate+zod）与 Element Plus 表单二选一，避免表单双范式。

### 2.3 子应用独立交付能力（P2）

- 已具备子应用独立运行（`defineSubApp` 自启动分支），补齐**独立部署 + 独立发版**流水线：每个 apps/* 单独构建产物 + nginx 配置，基座按 manifest 动态加载（依赖 1.4）。
- 引入子应用版本号与灰度：manifest 携带 version/gray 字段，支持按用户灰度切流——对齐阿里/字节微前端平台化能力。

### 2.4 开发者体验增强（P1）

- `pnpm dev` 目前交互式选择应用，增加 `pnpm dev --filter project-web --with-main` 一键「基座 + 指定子应用」组合启动（turbo filter 已支持，封装脚本即可）。
- 提供子应用脚手架：`pnpm gen:app <name>` 基于模板生成 vite.config/路由/locales/shared-auth 接入，新应用接入成本从「复制 9 处配置」降到 1 条命令。
- 修复 `@ydsz-core/popup-ui` 版本 5.2.1 与全线 5.5.9 不一致；`vite-plugin-qiankun` 11 处硬编码版本收敛进 pnpm catalog。

---

## 3. 性能提升

### 3.1 公共依赖外置与共享（P0，收益最大）

现状：9 个子应用各自打包 vue/element-plus 等（虽有 manualChunks vendor 分包，但跨应用仍是独立产物）；`conf/vite-config/plugins/importmap.ts` 已具备 jspm CDN importmap 外置能力却未作为主策略。

**落地建议（qiankun 官方推荐 + 大厂通用实践）：**
- vue / vue-router / pinia / element-plus / axios / echarts 等 6-8 个重依赖走 **importmap + CDN（或自建静态资源域）外置**，主子应用共享同一份 ESM 实例。
- 收益预估：单个子应用首包减少 60-80%，9 应用整体静态资源体积下降一个数量级；同时天然解决「多份 Vue 实例」类隐患。
- 注意：外置清单统一进 `conf/micro-apps.config.ts`（同 1.4 单一事实源），版本与 catalog 对齐；CDN 失败需有本地回退（script onerror fallback）。

### 3.2 预加载策略升级（P1）

- 当前仅对 userinfo/project 两应用 `requestIdleCallback` 插 prefetch link，qiankun `prefetch:false`。
- 建议：登录成功后按「用户角色高频应用」预热（权限数据已有），或开启 qiankun `prefetch: 'all'`（idle 时机）并配合外置后的缓存命中率提升；hover 预加载保留作为交互层补充。
- 与 1.2 保活结合后，二次进入子应用应接近 0 成本。

### 3.3 构建产物优化（P1）

| 项 | 现状 | 建议 |
|----|------|------|
| esbuild target | es2018 | 内部系统可升 **es2022**（Chrome 100+），减包 5-10% |
| PWA | 开启 | 中后台管理端 PWA 价值低且有缓存脏数据风险，**建议关闭**或仅限离线白名单 |
| 产物分析 | visualizer 需手动 `--mode analyze` | CI 增加周期性 bundle 报告 + 包体预算（bundle-budget），单 chunk >500KB 报警 |
| 子应用首包 | 每应用独立 Pinia/preferences/i18n 全量初始化 | i18n 已按需加载，推广到 preferences/styles；首屏非关键插件延迟注册 |

### 3.4 构建与 CI 性能（P1）

- turbo 增补 `lint`、`test:unit` 任务编排，复用 `.turbo` 缓存，CI 阶段化并行（对齐 4.1 修复后落地）。
- `pnpm build` 固定 8G 内存说明构建偏重，外置依赖（3.1）后重测内存基线并下调。

---

## 4. 体验改善

### 4.1 加载与切换体验（P1）

- 统一子应用 loading 体系：当前容器组件用 MutationObserver + 骨架屏，建议将骨架屏升级为「按应用类型定制骨架」（列表型/表单型/仪表盘型），对齐 antd Pro 的 PageLoading 规范。
- 子应用切换增加 150-200ms 淡入过渡（`@ydsz/motion` 已有能力），消除白屏跳变感。
- 配合保活（1.2），tab 切换体验对标浏览器标签页。

### 4.2 主题与视觉一致性（P2）

- 暗色模式已具备（design tokens + preferences），但 `themeToggle` 默认关闭：建议默认开启入口，并对 Element Plus 暗色变量做一轮对照走查（ele/index.css 定制需同步暗色分支）。
- Tailwind 与 Element Plus 混用场景建立规范：「布局与间距用 Tailwind，组件内样式用 BEM/design token」，写入 `.trae/rules` 与 lint 约束。

### 4.3 国际化补齐（P2）

- locales 动态按需加载已具备，但需建立**覆盖率检查**：CI 增加 i18n key 缺失扫描（zh-CN/en-US 双向 diff），硬编码中文文案纳入 cspell/自定义 lint 规则拦截。

### 4.4 可访问性与移动端（P3，低优先）

- 中后台 PC 优先（workflow 明确 PC Only），仅需保证基座与 project-web 在 1280px 窄屏不崩坏；a11y 做键盘导航与 aria 基础项即可，不投入专项。

---

## 5. 工程质量基线（贯穿所有维度）

### 5.1 P0：修复质量门禁断链（当天可修）

| 问题 | 位置 | 修复 |
|------|------|------|
| CI 跑 `pnpm run typecheck`，根脚本实为 `check:type` | `.github/workflows/frontend-ci.yml` | 改脚本名或对齐根 package.json |
| lefthook pre-push 引用不存在的 `pnpm test:run` | `lefthook.yml` | 改为 `pnpm test:unit` |
| lefthook 中 `pnpm tsc --noEmit` 根无 tsconfig 无效 | `lefthook.yml` | 改为 `pnpm check:type`（turbo 编排） |
| turbo 无 lint/test:unit 任务 | `turbo.json` | 补 `lint`、`test:unit` 任务并接缓存 |
| `gen-api.mjs` 依赖 `openapi-typescript` 未声明 | `package.json` / catalog | 入 catalog 锁定版本，契约检查才能真正门禁化 |

### 5.2 P1：测试体系补齐（对齐大厂门禁）

| 层 | 现状 | 目标 |
|----|------|------|
| 单元测试 | 37 个文件全在 comm/@core，阈值 70% | apps/ 核心业务组件（project-web 优先）补至关键路径覆盖；main/ 基座的注册/路由/权限逻辑补测试 |
| 契约测试 | gen-api --check 存在但未门禁 | 接入 CI：契约漂移即失败 |
| e2e | 仅 1 个 spec | 补齐「登录 → 菜单 → 跨应用跳转 → 审批提交」3-5 条黄金路径，接入 CI 夜间任务 |
| 微前端专项 | 无 | 增加主子通信、沙箱隔离、降级 UI 的集成测试 |

### 5.3 P1：规范沉淀

- 将「跨应用状态只走 globalState」「新页面禁用 shadcn-ui」「公共依赖只允许从 catalog 引用」等决策写入 `.trae/rules/` 与 ESLint 规则，让规范可被机器执行而非仅靠 Code Review。
- 依赖治理：taze 已具备，建议接入每月例行升级 + `vue-tsc@2.2.10` 精确锁、`@tailwindcss/nesting@insiders` 等风险点专项评估。

---

## 6. 落地路线图

### P0（1-2 周，止血与高收益）

| # | 事项 | 维度 | 预估工作量 |
|---|------|------|-----------|
| 1 | 修复 CI/lefthook 脚本名失配、turbo 补 lint/test 任务 | 质量 | 0.5 人日 |
| 2 | 公共依赖 importmap 外置 + CDN 回退 | 性能 | 3-5 人日 |
| 3 | 基座注册 `addGlobalUncaughtErrorHandler` + 加载失败降级 UI | 架构/体验 | 2-3 人日 |
| 4 | 清理死代码（global-state.ts / event-bus / adapter 空实现标注） | 架构 | 1 人日 |

### P1（1 个月，架构补强）

| # | 事项 | 维度 |
|---|------|------|
| 1 | 内核路线收敛决策（建议 qiankun 单内核）+ 适配层补齐 | 架构 |
| 2 | 子应用保活（loadMicroApp 多例缓存 + LRU） | 架构/性能/体验 |
| 3 | 注册表单一事实源 `micro-apps.config.ts` | 架构 |
| 4 | globalState 通信落地（用户/字典/未读数）+ props 快照注入 | 架构 |
| 5 | Token 内存化 + httpOnly refreshToken | 架构/安全 |
| 6 | 组件体系收敛决策 + ESLint 约束 | 功能/性能 |
| 7 | 测试补齐：apps 关键路径 + 契约门禁 + 3-5 条 e2e | 质量 |
| 8 | 子应用加载耗时等微前端监控指标 | 功能 |
| 9 | 微应用脚手架 `gen:app` + 组合启动脚本 | 功能/DX |

### P2（季度，平台化演进）

| # | 事项 | 维度 |
|---|------|------|
| 1 | 远程 manifest 动态注册 + 按权限/租户下发应用集 | 架构 |
| 2 | 子应用独立部署 + 版本灰度 | 功能 |
| 3 | 暗色模式默认开启 + 视觉走查 | 体验 |
| 4 | i18n 覆盖率 CI 检查 | 体验/质量 |
| 5 | bundle 预算门禁 + 月度依赖治理 | 性能/质量 |

### P3（按需）

- 移动端适配（除 workflow 外的基础可用性）、a11y 基础项、Module Federation 长期评估（若子应用规模突破 20+）。

---

## 7. 关键决策点（需架构组拍板）

1. **微前端内核路线**：收敛 qiankun（推荐）／迁 Wujie／迁 Module Federation——决定 1.x 系列所有工作的技术基座。
2. **组件库收敛**：shadcn-ui 下线节奏——影响包体优化收益与存量页面改造量。
3. **Token 模型**：是否接受「access token 内存化」带来的刷新即重登改造成本。
4. **保活方案**：qiankun 多例缓存（自研维护）vs 借决策 1 迁内核原生获得。

> 本报告所有 P0 项均不依赖上述决策，可立即启动。
