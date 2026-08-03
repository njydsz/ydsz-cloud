# YDSZ 前端微应用框架优化路线图（lite-kernel 单内核版）

> 技术路线已定：**下线 qiankun，以自研 micro-kernel-lite 为唯一微前端内核，补齐缺失能力**。
> 对标行业主流方案（Qiankun / Wujie / Micro-App / Garfish / Module Federation）与大厂中后台研发规范。
> 基于 `comm/effects/{micro-runtime, micro-kernel-lite, micro-adapter-qiankun}` 及 main/、apps/ 全量源码调研。
> 调研日期：2026-08-03 ｜ 适用版本：v1.0.0-SNAPSHOT

---

## 0. 路线判断与现状评估

### 0.1 为什么 lite-kernel 单内核路线成立

9 个子应用全部为**同一团队、同一 monorepo、统一 Vite 构建链的 Vue 3 应用**——这正是 ESM 原生微前端的理想场景。qiankun 的核心价值（HTML entry 解析、UMD 兼容、异构技术栈沙箱）在本项目中全部是过剩能力：

| 维度 | qiankun 2.10（现状） | lite-kernel（目标） | 行业参照 |
|------|---------------------|--------------------|----------|
| 加载方式 | import-html-entry + eval/UMD | ESM manifest + 原生 dynamic import | 对齐 Module Federation / Vite 生态方向 |
| 包体成本 | qiankun 运行时 + 每应用 vite-plugin-qiankun | 零依赖（自研 ~500 行） | micro-app/wujie 均为重运行时 |
| keep-alive | 不支持（adapter 空实现） | scheduler 已实现 DOM detach/reattach | wujie 保活同级别能力 |
| 错误降级 | 无（adapter 未接错误处理） | error-boundary 已实现（降级 UI + 会话级熔断） | 优于 qiankun 裸用 |
| JS 沙箱 | proxy 沙箱 | **无（最大缺口）** | 见 1.3 补齐方案 |
| 样式隔离 | experimentalStyleIsolation | 仅 link 注入/移除，**无 scoping** | 见 1.4 补齐方案 |
| 通信 | globalState 已注入 props | **未接入** | 见 1.5 |
| 工程耦合 | 11 处 vite-plugin-qiankun 硬编码 | manifest 插件收进共享 vite-config | — |

### 0.2 lite-kernel 源码级问题清单（按严重度）

调研发现 lite-kernel 当前处于「框架成形、关键路径未闭环」状态，以下问题按严重度排列：

**🔴 致命（不修则完全不可用）**

| # | 问题 | 位置 |
|---|------|------|
| F1 | **manifest 文件名不一致**：构建插件产出 `version.json`，loader 却 fetch `manifest.json`，加载必然 404 | `vite-plugin-manifest.ts:65` vs `loader.ts:31` |
| F2 | **路由同步盲区**：仅监听 `popstate`，而主应用 Vue Router 的 `router.push` 不触发 popstate → 基座内跳转子应用路由内核完全不感知（`navigateTo` 手动 dispatch popstate 只是补丁，第三方/基座 router 跳转全部漏接） | `lite-kernel.ts:87` |
| F3 | **manifest 内路径硬编码 `/` 前缀**：entry/css 生成为 `/assets/xxx.js`，与生产 `/ydsz-*-web/` 子路径部署冲突 | `vite-plugin-manifest.ts:52,57` |

**🟠 严重（功能错误）**

| # | 问题 | 位置 |
|---|------|------|
| S1 | **预加载实现错误**：prefetch 直接调 `switchToApp` 会真实 mount 并篡改 `activeAppName`，「预加载后切回」是空 then；应只 `loadApp` 预热模块缓存不挂载 | `lite-kernel.ts:164-175` |
| S2 | **路由不匹配时不卸载**：路径从子应用回到基座页面（不匹配任何 activeRule）时，当前子应用不会被 deactivate | `lite-kernel.ts:64-81` |
| S3 | **切换无并发控制**：快速连续切换时 `switchToApp` 异步竞争，可能出现后到的 mount 覆盖先到的 | `lite-kernel.ts:94` |
| S4 | **keep-alive 未打通**：`setKeepAlive` 存在但与 tabbar 集成是 TODO（代码注释「M3 阶段」），且 detach 缓存分支不触发任何 activated 钩子 | `lite-kernel.ts:116-117`、`scheduler.ts` |

**🟡 缺失能力（对标差距）**

| # | 缺失项 | 说明 |
|---|--------|------|
| M1 | 无 JS 沙箱/全局污染防护 | 注释明示不做；9 应用共存于同一 window，需轻量方案（见 1.3） |
| M2 | 样式无 scoping | 仅注入/移除 link，子应用间 CSS 可互相污染（Element Plus 全量样式尤其危险） |
| M3 | globalState 未注入 mountProps | qiankun adapter 有 `wrapGlobalStateProps`，lite 分支子应用拿不到通信句柄 |
| M4 | dev 模式无 manifest | dev 下 entry 指向 vite dev server，不存在 version.json/manifest.json，需 dev 直引分支 |
| M5 | 无加载超时/性能打点 | loadApp 无 timeout，无 mount 耗时上报（接 `@ydsz/monitor`） |
| M6 | update 生命周期从不调用 | 接口已定义，scheduler 未消费 |
| M7 | start() 无 stop/热重启 | `routerSyncCleanup` 保存但从不调用，基座 HMR 会重复注册监听 |

### 0.3 不变的既有优势（继续沿用）

- pnpm catalog + Turborepo + lefthook + ESLint 9 flat config 工程化基线
- `createSharedRequestClient` 统一请求层、`shared-auth` 子应用一键接入
- `defineSubApp`/`createSubApp` 已同时导出 qiankun `renderWithQiankun` 与标准 `{bootstrap, mount, unmount, update}` —— **子应用侧迁移成本接近零**，lite-kernel 消费的正是这套标准导出
- `gen-api.mjs` 契约防漂移、`@ydsz/monitor` 观测基座、v-access 权限体系

---

## 1. lite-kernel 能力补齐清单（核心工作）

> 以下每项均给出文件级落点，按 P0（上线阻断）→ P1（体验与稳定）→ P2（平台化）排序。

### 1.1 修复致命缺陷（P0，预计 2-3 人日）

1. **统一 manifest 契约**：定为 `manifest.json`（loader 侧不动），`vite-plugin-manifest.ts` 的 `emitFile` 文件名改为 `manifest.json`；同时把插件正式接入 `@ydsz/vite-config` 的 application 预设（从子应用 name 自动注入），删除各应用手工配置的可能。
2. **路径基座化**：manifest 生成时 entry/css 使用相对构建 `base` 的完整前缀（读取 vite `config.base`），loader 侧拼接 `config.entry` 兜底，杜绝 `/` 根路径假设。
3. **路由同步补丁**：在 `startRouterSync` 中 patch `history.pushState`/`replaceState`（包裹后派发自定义事件并恢复），同时保留 popstate 监听——与 qiankun、micro-app 的做法一致；或与基座 `router.afterEach` 显式打通（推荐两者兼有，patch 兜底任何来源的 URL 变更）。
4. **未匹配卸载**：`handleRouteChange` 遍历无匹配时，若 `activeAppName` 非空则 `deactivateApp` 当前应用。

### 1.2 调度器健壮性（P0/P1，预计 2-3 人日）

1. **切换串行化**：`switchToApp` 改为 promise 链队列（或携带切换令牌 token，过期切换直接丢弃），解决快速切换竞态（S3）。
2. **预加载重写**：prefetch 只调 `loadApp`（fetch manifest + dynamic import + 注入 CSS），**不执行 mount**；ESM 模块缓存天然命中，二次激活仅差 mount 耗时（S1）。保留 `requestIdleCallback` 时机与按应用过滤函数。
3. **keep-alive 闭环**（S4）：
   - 打通 tabbar：`use-tabbar-micro-sync.ts` 的 tab 关闭 → `unmountApp`、tab 存在 → `setKeepAlive(name, true)`；
   - 增加 **LRU 上限**（建议 ≤3 个保活应用），超限按最久未用完整卸载，防内存膨胀；
   - 保活 reattach 时向子应用派发 `update(mountProps)` 生命周期（顺带补齐 M6 的消费方），子应用可在 `update` 中刷新路由/数据——这正好用上已定义的空闲接口。
4. **start/stop 对称**：返回 `stop()`（清理路由监听 + 卸载全部实例 + `clearDegraded`），支撑基座 HMR 与测试环境（M7）。

### 1.3 JS 隔离：轻量快照沙箱（P1，预计 3-5 人日）

lite-kernel 不做 proxy 沙箱是合理取舍（同团队同技术栈，性能优先——micro-app 的 with 沙箱与 qiankun proxy 沙箱都有可观运行时开销），但「零防护」不可接受。**对齐 Garfish 的 snapshot 沙箱思路做轻量补齐**：

| 能力 | 方案 |
|------|------|
| window 污染防护 | mount 前快照 `window` 键集合，unmount 时 diff 恢复（新增全局变量删除、被改写属性还原） |
| 事件监听泄漏 | 包裹 `addEventListener/removeEventListener` 记录子应用注册的 window/document 级监听，unmount 统一移除 |
| 定时器泄漏 | 同理记录 `setInterval/setTimeout`（raFID 可选），unmount 清理 |
| 规范兜底 | ESLint `no-restricted-globals` + 自定义规则拦截子应用直接写 `window.xxx`，让沙箱只管「漏网之鱼」 |

> 明确边界：不防恶意代码（同团队无此诉求），只防「意外污染」。文档中写明该边界，避免被当作安全沙箱误用。

### 1.4 样式隔离：构建期 scoped（P1，预计 2-3 人日）

运行时 scoping（qiankun experimentalStyleIsolation）对 Element Plus 弹层（body 下 teleport）会失效。**推荐构建期方案**：

1. 共享 vite-config 中为子应用 CSS 加 PostCSS prefix 插件（`postcss-prefix-selector`，前缀 `[data-lite-app="xxx"]`），与 lite-kernel 挂载容器属性约定一致；
2. Element Plus 弹层类组件通过 `append-to` 配置或全局 `teleport` 容器收敛到子应用根节点内（项目已有 popup-ui，可统一封装）；
3. 卸载时 `removeStylesheets` 已实现，保留；keep-alive detach 不移除样式（现状正确，保持）。

### 1.5 通信接入：globalState 注入 mountProps（P1，预计 1-2 人日）

- lite-kernel 在 `activateApp` 组装 `mountProps` 时注入 `micro-runtime` 已有的 `GlobalStateHandle`（onGlobalStateChange/setGlobalState/globalState），与 qiankun adapter 的 `wrapGlobalStateProps` 语义对齐——子应用 `useGlobalState` composable 无需修改即可消费；
- 借此机会**落地真实通信场景**（当前全项目零消费）：用户信息变更广播、数据字典刷新、消息未读数、主题切换四项写入规范「跨应用状态只允许走 globalState」；
- 删除死代码 `main/src/qiankun/global-state.ts` 与未消费的 event-bus 二选一：保留 GlobalStateHandle 一条路径，其余移除。

### 1.6 工程与可观测配套（P1，预计 2-3 人日）

1. **dev 模式分支**（M4）：loader 检测 `import.meta.env.DEV` 时跳过 manifest fetch，直接 `import(entry)`（指向 vite dev server 的 `/src/main.ts`），CORS 头复用现有子应用 dev server 配置；
2. **加载超时与重试**（M5）：loadApp 包一层 timeout（建议 10s）+ 指数退避重试（2 次），最终失败走 error-boundary 降级（已有）；
3. **监控接入**：`addLifecycleHook('beforeLoad'/'afterMount')` 中打点到 `@ydsz/monitor`——按应用统计加载 P75/P95、失败率、保活命中率；错误边界触发时携带 appName 上报；
4. **共享依赖外置（与 lite-kernel 强绑定，见 3.1）**：ESM 直引模式下，vue/pinia/vue-router 必须经 importmap 外置为单例，否则 9 份 vue 实例会导致依赖注入割裂与包体爆炸——这是 lite 路线的**前置硬需求**，不是可选项。

---

## 2. qiankun 下线迁移计划

> 原则：双内核可切换期 → 全量验证 → 物理删除。`createRuntime({ kernel })` 的注册机制天然支持灰度。

### 阶段一：可切换（1 周）
1. 完成 1.1/1.2 全部 P0 修复，lite-kernel 达到「能跑全量 9 应用」；
2. 基座 `bootstrap.ts` 的 kernel 选择改为环境变量驱动（`VITE_MICRO_KERNEL=lite|qiankun`，默认 lite），保留 qiankun 回退通道；
3. `defineSubApp` 中 `renderWithQiankun` 分支保留，确认 lite 分支（标准生命周期导出）9 应用全部工作。

### 阶段二：验证与能力补齐（2-3 周）
1. 完成 1.3-1.6 补齐项；
2. 双内核 A/B 回归：同一套 e2e 用例（见 5.2）分别在 lite/qiankun 下跑通；
3. 性能对比报告：首屏、切换耗时、内存占用（lite 预期全面优于 qiankun，数据留档）。

### 阶段三：物理删除（1 周）
1. 删除 `micro-adapter-qiankun` 包、根及 11 处 `vite-plugin-qiankun` 依赖、`defineSubApp` 中 qiankun 分支、`main/src/qiankun/` 目录；
2. `registerKernel` 机制保留（接口层价值仍在——未来如需接入 wujie/三方内核不用改业务），但只注册 `lite`；
3. README 与 `.trae/rules` 同步更新，eslint 增加 `no-restricted-imports: qiankun` 防回潮。

---

## 3. 性能提升（与 lite 路线联动）

### 3.1 公共依赖 importmap 外置（P0，lite 路线前置）

- vue / vue-router / pinia / element-plus / axios / echarts / vxe-table 外置为 importmap 单例（`conf/vite-config/plugins/importmap.ts` 已具备 jspm CDN 能力，建议改为**自建静态资源域**，CDN 仅作 fallback 并配 onerror 回退本地）；
- 外置清单收进单一事实源配置（见 4.1），版本与 pnpm catalog 对齐校验；
- 收益：子应用首包减 60-80%，且保证 lite-kernel 下跨应用共享 vue 实例单例（Pinia/provide 语义正确的前提）。

### 3.2 加载性能

| 项 | 动作 |
|----|------|
| 预加载 | 1.2 修正后，按「登录用户角色高频应用」预热（权限数据已有），hover 预加载保留 |
| 保活 | 1.2 闭环后二次进入子应用 ≈ 0 成本（DOM reattach） |
| esbuild target | es2018 → **es2022**（内部系统 Chrome 100+），减包 5-10% |
| PWA | 中后台价值低且有缓存脏数据风险，建议关闭 |
| 包体预算 | CI 增加 bundle 报告 + 单 chunk >500KB 告警 |

### 3.3 构建性能

- turbo 补 `lint`、`test:unit` 任务编排复用缓存；外置依赖后重测构建内存基线（当前固定 8G）。

---

## 4. 架构与功能增强

### 4.1 注册表单一事实源 → 远程 manifest（P1→P2）

- 近期：`conf/micro-apps.config.ts` 统一应用清单/端口/前缀/外置依赖，基座路由、tabbar 映射、vite 配置全部从此消费，消除三处硬编码（`qiankun/index.ts`、`subapps.ts`、PATH_TO_APP）；
- 中期：基座启动时从 `ydsz-system`（已有「应用注册」能力域）拉取远程 manifest，实现子应用运行时注册、按权限/租户下发应用集、版本灰度——`registerApps` 接口天然支持，lite-kernel 无改造成本。

### 4.2 Token 安全模型（P1）

- access token 内存化（基座持有，经 globalState 快照注入子应用），刷新走 httpOnly Cookie + refreshToken rotation，替代当前「SecureLS 密钥随 bundle 分发」的伪安全方案；
- 增加主子 namespace 版本漂移运行时校验；`resetAllStores` 的 `pinia._s.set` 劫持 hack 改官方插件机制。

### 4.3 组件体系收敛（P1）

- 主库锁定 Element Plus + vxe-table；shadcn-ui 冻结新增（ESLint `no-restricted-imports` 强制），存量随迭代下线；form-ui 与 Element 表单二选一——三套并存对包体与样式隔离（1.4）都是负担。

### 4.4 开发者体验（P1）

- `pnpm gen:app <name>` 脚手架：模板化生成 vite.config/路由/locales/shared-auth/manifest 接入，新应用 1 条命令接入；
- `pnpm dev --filter project-web --with-main` 一键「基座+指定子应用」组合启动。

---

## 5. 工程质量基线

### 5.1 P0：质量门禁断链修复（0.5 人日）

| 问题 | 位置 | 修复 |
|------|------|------|
| CI 跑 `pnpm run typecheck`，根脚本实为 `check:type` | `.github/workflows/frontend-ci.yml` | 对齐脚本名 |
| lefthook pre-push 引用不存在的 `pnpm test:run`；`tsc --noEmit` 根无 tsconfig 无效 | `lefthook.yml` | 改 `pnpm test:unit` / `pnpm check:type` |
| turbo 无 lint/test:unit 任务 | `turbo.json` | 补任务并接缓存 |
| `openapi-typescript` 未声明入 catalog | `package.json` | 入 catalog，契约检查门禁化 |

### 5.2 P1：测试体系（为内核切换兜底）

| 层 | 目标 |
|----|------|
| 微内核专项（最高优先） | lite-kernel 的 loader/scheduler/error-boundary/沙箱/keep-alive 单测（当前零测试就换内核风险极高）；双内核 A/B 对照用例 |
| e2e | 「登录 → 菜单 → 跨应用跳转 → 保活切回 → 审批提交」黄金路径 3-5 条，lite/qiankun 双跑 |
| 单元 | apps 关键路径补测（project-web 优先），main 基座注册/路由/权限逻辑补测（当前 apps+main 零测试） |
| 契约 | gen-api --check 接入 CI 门禁 |

### 5.3 P1：规范机器化

跨应用状态只走 globalState、禁用 shadcn-ui 新增、禁引 qiankun、子应用禁写 window 全局——全部落 ESLint 规则与 `.trae/rules`，不靠 Code Review 人肉拦截。

---

## 6. 落地路线图总览

### P0（第 1-2 周）：让 lite-kernel 正确且可切换
| # | 事项 | 预估 |
|---|------|------|
| 1 | 1.1 致命修复（manifest 契约/路径/路由 patch/未匹配卸载） | 2-3 人日 |
| 2 | 1.2 调度器（切换串行化、prefetch 重写、stop） | 2-3 人日 |
| 3 | 3.1 importmap 外置 + vue 单例保障 | 3-5 人日 |
| 4 | 5.1 CI/lefthook/turbo 断链修复 | 0.5 人日 |
| 5 | 阶段一：kernel 环境变量可切换，9 应用 lite 下跑通 | 2 人日 |

### P1（第 3-6 周）：补齐能力 + 全量验证
| # | 事项 |
|---|------|
| 1 | 1.3 快照沙箱 + 1.4 构建期样式 scoped |
| 2 | 1.2.3 keep-alive 闭环（tabbar 打通 + LRU + update 消费） |
| 3 | 1.5 globalState 注入 + 四个真实通信场景落地 + 死代码清理 |
| 4 | 1.6 dev 分支 / 超时重试 / 监控打点 |
| 5 | 5.2 微内核单测 + 双内核 e2e 对照 |
| 6 | 4.1 注册表单一事实源、4.2 token 内存化、4.3 组件收敛决策 |
| 7 | 阶段二验证 + 性能对比报告 → 阶段三物理删除 qiankun |

### P2（季度）：平台化
远程 manifest 动态注册与灰度、子应用独立部署发版、暗色模式默认开启、i18n 覆盖率门禁、bundle 预算门禁、月度依赖治理。

---

## 7. 风险与缓解

| 风险 | 缓解 |
|------|------|
| lite-kernel 无 JS 沙箱，历史全局污染问题暴露 | 1.3 快照沙箱 + ESLint 约束；切换期保留 qiankun 回退通道（环境变量一键切回） |
| ESM 外置后 CDN/静态域故障导致全站不可用 | onerror 本地回退 + 静态域随基座同域部署优先 |
| 样式 scoped 后 Element Plus 弹层样式失效 | 弹层 teleport 容器统一收敛，回归重点覆盖 popup/抽屉/消息提示 |
| keep-alive 内存膨胀 | LRU ≤3 + monitor 内存占用打点 |
| 双内核并行期配置漂移 | 单一事实源（4.1）先行，kernel 差异只允许在 adapter 层 |

> 全程原则：**P0 不依赖任何未决事项，可立即启动**；qiankun 回退通道保留到阶段三验收通过。
