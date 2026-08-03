# 系统能力优化 + 自研微应用框架（接口层 + 轻内核）落地方案

> 基于 ydsz-frontend 现状（Qiankun + Vue3 + Vite + 9 子应用 Monorepo）
> 定位：可执行的工程方案，非概念性建议
> 日期：2026-08-03

---

# 第一部分：自研微应用框架

## 1. 核心判断：为什么你们可以跳过最难的两座山

通用微前端框架（qiankun）最难的两个模块是**资源加载器**（import-html-entry：解析任意 HTML、抽取 script、模拟执行）和 **JS 沙箱**（Proxy 快照隔离）。它们存在的理由是"加载不可信的、任意技术栈的第三方应用"。

你们的场景完全不满足这个前提：

| qiankun 假设 | ydsz 现状 | 推论 |
|---|---|---|
| 子应用技术栈任意 | 9 个应用全部 Vue3 + 统一 vite-config | 可约定统一接入规范 |
| 子应用产物是黑盒 HTML | 产物由自己构建，可定制输出格式 | **可直接输出 ESM entry，无需解析 HTML** |
| 子应用不可信，需沙箱隔离 | 同一团队、同一规范、统一评审 | **JS 沙箱非必需，DOM/样式隔离即可** |
| 部署位置任意 | 全部同域 Nginx `/ydsz-*-web/` | 无跨域加载问题 |

**结论：自研轻内核 = ESM 加载器 + 生命周期调度 + 样式隔离 + 通信总线 + 路由协同**，工作量从"3-4 人月（通用框架）"降到 **3-4 人周**。这不是简化版 qiankun，而是比 qiankun 更适合 Vite 世代的架构（micro-app / wujie / Garfish 之后，社区共识就是 ESM 化）。

## 2. 总体架构

```
┌─────────────────────────────────────────────────┐
│ main 主应用 (bootstrap.ts)                       │
│   只 import @ydsz/micro-runtime，不感知内核实现     │
├─────────────────────────────────────────────────┤
│ @ydsz/micro-runtime（自研接口层，新增 comm 包）     │
│   registerApp() / start() / defineGlobalState()  │
│   接口稳定，实现可插拔                              │
├──────────────────┬──────────────────────────────┤
│ qiankun-adapter  │ lite-kernel（自研轻内核）       │
│ （现状平移，兜底） │ （目标实现）                   │
└──────────────────┴──────────────────────────────┘
        ▲ 子应用侧：createSubApp() 工厂不变（改内部实现）
```

### 2.1 包划分（放入 comm/effects）

| 包 | 职责 | 体量预估 |
|---|---|---|
| `@ydsz/micro-runtime` | 接口层：类型定义 + facade API + adapter 注册机制 | ~300 行 |
| `@ydsz/micro-kernel-lite` | 自研轻内核：ESM loader / 生命周期 / 样式隔离 / 通信 / 路由 | ~1200 行 |
| `@ydsz/micro-adapter-qiankun` | 把 qiankun 包装成 runtime 的一个实现（迁移期兜底 + 灰度回退） | ~150 行 |

## 3. 接口层设计（@ydsz/micro-runtime）

接口设计要点：**对齐 qiankun 语义**（迁移成本最低），但**类型化**并剔除 qiankun 的历史包袱（UMD entry、singular 模式等）。

```ts
// comm/effects/micro-runtime/src/types.ts

/** 子应用注册配置（对齐现有 main/src/qiankun/index.ts 的 microApps） */
export interface MicroAppConfig {
  name: string;                    // 'project-web'
  entry: string;                   // prod: '/ydsz-project-web/'  dev: '//localhost:5603'
  container: string;               // '#subapp-container'
  activeRule: string;              // '/ydsz-proj'
  props?: Record<string, unknown>;
}

/** 子应用生命周期导出（轻内核约定子应用 ESM entry 必须导出） */
export interface LifecycleExports {
  bootstrap?: (props: MountProps) => Promise<void>;
  mount: (props: MountProps) => Promise<void>;
  unmount: (props: MountProps) => Promise<void>;
  update?: (props: MountProps) => Promise<void>;
}

export interface MountProps {
  container: HTMLElement;
  basename: string;
  /** 类型化全局状态（替代 qiankun 的 any 广播） */
  globalState: GlobalStateHandle;
  [key: string]: unknown;
}

/** 类型安全的全局状态句柄 */
export interface GlobalStateHandle<T = GlobalState> {
  get(): Readonly<T>;
  set(patch: Partial<T>): void;
  subscribe(listener: (state: T, prev: T) => void): () => void;
}

/** 内核实现必须满足的接口 */
export interface MicroRuntime {
  registerApps(apps: MicroAppConfig[]): void;
  start(options?: StartOptions): void;
  /** 手动卸载（供 tabbar 关闭页签时调用，qiankun 无此能力） */
  unmountApp(name: string): Promise<void>;
  /** 保活控制：切走时不销毁，切回时直接复用 DOM */
  setKeepAlive(name: string, keep: boolean): void;
}

/** adapter 注册：start 时按 name 选择内核 */
export function registerKernel(name: 'lite' | 'qiankun', factory: () => MicroRuntime): void;
export function createRuntime(options: { kernel: 'lite' | 'qiankun' }): MicroRuntime;
```

### 全局状态升级（替代现有 global-state.ts）

现有 `initGlobalState` 是字符串广播 + any，升级为**类型化 + 版本化**：

```ts
// 保留现有 GlobalState 类型（user/theme/locale/notificationCount/tenantId）
const globalState = defineGlobalState<GlobalState>({
  initial: { locale: 'zh-CN', notificationCount: 0, theme: 'auto' },
  version: 1,                       // 结构变更时递增，跨版本不兼容直接报错而非静默丢字段
});

// 子应用侧 Vue 组合式封装（新增）
export function useGlobalState<K extends keyof GlobalState>(key: K) {
  // 返回 Ref<GlobalState[K]>，自动 subscribe/unsubscribe
}
```

## 4. 轻内核设计（@ydsz/micro-kernel-lite）

### 4.1 ESM 加载器 —— 最大简化点

**约定**：所有子应用由统一 vite-config 构建，增加一个共享插件输出 `manifest.json`：

```json
{
  "name": "project-web",
  "entry": "/ydsz-project-web/assets/entry.[hash].js",
  "css": ["/ydsz-project-web/assets/entry.[hash].css"],
  "version": "2.3.1"
}
```

内核加载逻辑：

```ts
async function loadApp(config: MicroAppConfig): Promise<LifecycleExports> {
  const manifest = await fetch(`${config.entry}manifest.json`).then(r => r.json());
  // CSS：注入 <link>，卸载时移除（或随 shadow root 作用域化，见 4.2）
  manifest.css.forEach(href => injectStylesheet(href));
  // ESM entry：原生 dynamic import —— 无 HTML 解析、无 UMD、无 eval
  const module = await import(/* @vite-ignore */ manifest.entry);
  assertLifecycle(module);          // 校验导出 mount/unmount，fail-fast
  return module;
}
```

- **prod**：`import()` 加载构建产物，HTTP 缓存 + 文件 hash 天然解决版本问题；
- **dev**：`entry` 指向 `//localhost:5603/src/main.ts`，Vite dev server 原生服务 ESM——**dev 体验反超 qiankun**（qiankun + vite-plugin-qiankun 的 dev 模式问题频发，这正是该插件存在的意义，也是它停更后的最大风险点）；
- 预加载：现有 hover prefetch 逻辑平移到内核的 `preload()`，`<link rel="modulepreload">` 替代 `rel=prefetch`（对 ESM 更有效）。

### 4.2 样式隔离 —— shadow DOM 为主，scoped 兜底

| 方案 | 适用 | 你们的风险点 |
|---|---|---|
| **shadow DOM**（默认） | 子应用根节点挂到 `container.attachShadow({mode:'open'})` | Element Plus 弹窗/message 默认 teleport 到 `document.body`，会逃逸出 shadow root 且丢失子应用样式变量 |
| scoped 改写（兜底） | 给子应用 CSS 加 `[data-ydsz-app="project-web"]` 属性选择器前缀 | 构建期 PostCSS 插件实现，成本低 |

**落地建议**：先上 scoped 方案（PostCSS 前缀插件 ~100 行，加入共享 vite-config），shadow DOM 作为 v2 选项。弹窗逃逸问题两种方案下处理方式相同：在 `installBasePlugins`（create-sub-app.ts:56）中统一配置 Element Plus 的 `appendTo` 指向子应用容器——这本来就是你们工厂层该收的口子。

### 4.3 生命周期调度 + 保活

```ts
class AppInstance {
  status: 'NOT_LOADED' | 'LOADED' | 'MOUNTED' | 'UNMOUNTED';
  private cachedContainer: HTMLElement | null;   // 保活缓存

  async mount(props: MountProps) {
    if (this.keepAlive && this.cachedContainer) {
      props.container.appendChild(this.cachedContainer);  // 直接挂回，零重新渲染
      return;
    }
    await this.exports.mount(props);
  }

  async unmount() {
    if (this.keepAlive) {
      this.cachedContainer = this.rootEl;
      this.rootEl.remove();        // 摘下来但不销毁（Vue 组件树状态保留）
    } else {
      await this.exports.unmount();
    }
  }
}
```

**与 tabbar 打通**（这是 qiankun 做不好的差异化点）：`comm/stores/modules/tabbar.ts` 已管理多页签，内核暴露 `setKeepAlive(name, keep)`，页签打开即保活、关闭即真卸载——子应用切换从"白屏重载"变为"瞬时恢复"。

### 4.4 路由协同

现状是主应用 catch-all（`subapps.ts` 的 `:path(.*)*`）+ 子应用独立 history。轻内核沿用该模型（已验证可行），只做两点加固：

1. **activeRule 统一由内核解释**：主应用 router 与内核共用同一份 `microApps` 注册表（现状注册表在 `main/src/qiankun/index.ts`，迁到 runtime 包，消除主应用路由配置与 qiankun 注册的双份维护）；
2. **子应用内 `router.push` 跨应用跳转**：提供 `navigateTo('/ydsz-proj/list')` API 替代散写的 `router.push`/`location.href`，由内核决定走主应用路由还是整页跳转。

### 4.5 通信总线（替代 globalState 广播）

保留 globalState（共享状态）之外，增加**事件总线**（一次性消息，如"消息已读，刷新角标"）：

```ts
// 类型化事件注册表，集中在 comm/constants，禁止散写字符串
export const MicroEvents = {
  NotificationRead: 'notification:read',
  TenantSwitched: 'tenant:switched',
} as const;

bus.emit(MicroEvents.TenantSwitched, { tenantId });
bus.on(MicroEvents.TenantSwitched, handler);   // 返回取消订阅函数
```

### 4.6 错误边界与兜底

- `loadApp` 失败 → 渲染现有 `_core/fallback/internal-error` 组件到容器，附带重试按钮；
- 子应用 `mount` 抛错 → 自动卸载 + 上报 monitor（`@ydsz/monitor` 已有），并标记该应用本次会话降级为整页跳转模式；
- 全局 `unhandledrejection` 按应用名归因（内核在执行生命周期时记录当前活动应用）。

## 5. 子应用侧改造（create-sub-app.ts）

改动极小，正是防腐层的价值：

```ts
// 现状：renderWithQiankun({ bootstrap, mount, unmount, update })
// 改为（qiankun 与轻内核双兼容，一个工厂两种导出）：
import { defineSubApp } from '@ydsz/micro-runtime/define';

export function createSubApp(config: SubAppConfig) {
  const lifecycle = {
    async bootstrap() {},
    async mount(props: MountProps) { await coreMount(config, props); },
    async unmount() { app?.unmount(); app = null; },
  };

  defineSubApp(config.appName, lifecycle);   // ESM 模式下导出 + qiankun 模式下注册

  if (!isMicroEnv()) { /* 独立运行逻辑不变 */ }
}
```

- `defineSubApp` 在 ESM 环境就是 `export` 生命周期对象；在 qiankun 环境走 `renderWithQiankun`——**同一个子应用产物可同时被两种内核加载**，这是灰度切换的关键。
- `vite-plugin-qiankun` 依赖随之变成可选，灰度完成后即可移除这个停更依赖。

## 6. 里程碑与工作量

| 阶段 | 内容 | 工作量 | 验收标准 |
|---|---|---|---|
| M1 接口层 | micro-runtime 包 + qiankun-adapter 平移现有逻辑 + 全局状态类型化 | 1 周 | 主应用 import 全换，行为零变化，qiankun 引用从 4 处收敛到 adapter 内 |
| M2 轻内核 | ESM loader + manifest 插件 + 生命周期调度 + scoped 样式隔离 | 1.5 周 | agent-web（最小应用）在 lite 内核下完整跑通 dev + prod |
| M3 差异化能力 | 保活 + tabbar 打通 + 事件总线 + 错误兜底 | 1 周 | 子应用切换秒开，页签关闭释放内存 |
| M4 灰度推广 | 按 agent → system → userinfo → 其余顺序逐应用切换 | 1 周（观察期为主） | 9 应用全部 lite，qiankun adapter 保留一个版本周期后删除 |
| 合计 | | **约 4-5 周 / 1 人** | |

## 7. 风险与对策

| 风险 | 概率 | 对策 |
|---|---|---|
| Element Plus 弹窗/下拉在 scoped 隔离下样式丢失 | 高 | 工厂层统一 `appendTo` + 弹层类组件样式不进 scoped（element-vendor 样式全局加载，你们已是 manualChunks 独立分包，天然支持） |
| vxe-table 等三方库全局污染 | 中 | 现状已共存 9 个应用，说明风险可控；内核提供 CSS 卸载钩子 |
| ESM 跨子应用共享 vue 单例冲突 | 中 | 现有 importmap 方案（vue/pinia/router 走 CDN 单例）继续沿用，内核不重复解决 |
| dev 模式跨端口 ESM import 的 CORS | 低 | Vite dev server 加 `server.cors: true`（写进共享 vite-config） |
| 切换期间两种内核行为差异 | 中 | M1 接口层先行保证语义对齐；每应用切换后观察 2 天再切下一个 |

---

# 第二部分：系统能力优化（与框架升级并行）

> 承接《FRONTEND_OPTIMIZATION_REVIEW.md》，此处只列与微应用平台直接相关的**平台级能力**，按与框架升级的协同关系排序。

## C1. 监控闭环（与 M3 同期）
- monitor 包接入 Sentry/自建上报，CI 上传 sourcemap；
- 内核错误边界（4.6）+ 接口慢请求（>2s）+ `X-Trace-Id` 串联后端 APM；
- 每个子应用独立的 RUM 指标（LCP/INP 按 app 维度归因）——自研内核后这几乎是免费能力（内核天然知道应用边界）。

## C2. 安全整改（与 M1 同期，互不阻塞）
- token 从 secure-ls 迁 HttpOnly Cookie（需后端配合），globalState 注释里 already 声明"token 不走 globalState"，方向一致；
- `v-safe-html` 指令（DOMPurify）替换 5 处裸 `v-html`；
- Nginx 加 CSP（先 report-only）。

## C3. 性能预算（M2 落地后立即生效）
- 开启 gzip/brotli + 静态资源长缓存；
- manifest.json 天然携带 version，内核检测到子应用发版后提示用户"刷新加载新版本"（替代现在的 PWA 提示，更精准）；
- bundle 预算：单 chunk >500KB 构建告警接 CI。

## C4. 契约与类型（独立推进）
- OpenAPI 生成落地（`gen:api` + CI diff 检查）；
- eslint `no-explicit-any` 从 off → warn，新增代码 CI 拦截；
- 全局状态/事件总线全部类型化（M1 交付物的一部分）。

## C5. 测试补齐（与 M4 灰度互相保障）
- 内核本身必须有测试：loader / 生命周期 / 保活单测（micro-kernel-lite 是纯逻辑包，可测性远好于 qiankun 集成）；
- e2e 从 5 用例扩到"登录 + 9 应用逐个进入并验证列表加载"——这同时就是 M4 的灰度验收自动化；
- 断言去 `if (isVisible)` 化，测试账号走环境变量。

## 组合路线图

```
W1        W2-W3        W4          W5-W6        W7+
├─────────┼────────────┼───────────┼────────────┼──────
│ M1 接口层 │ M2 轻内核   │ M3 保活/总线│ M4 灰度推广 │ 删 qiankun adapter
│ C2 安全   │ C3 性能预算 │ C1 监控闭环 │ C5 e2e 扩充  │
│          │            │           │ C4 契约持续推进 │
```

---

## 附：现状锚点（方案对应的现有代码位置）

| 方案点 | 现状位置 |
|---|---|
| microApps 注册表迁移 | `main/src/qiankun/index.ts:42-97` |
| qiankun 启动与 hover prefetch | `main/src/bootstrap.ts:93-227` |
| globalState 升级 | `main/src/qiankun/global-state.ts` |
| 子应用工厂改造 | `comm/effects/shared-auth/src/create-sub-app.ts:173-198` |
| tabbar 保活打通 | `comm/stores/src/modules/tabbar.ts` |
| 手动分包/vue 单例 | `conf/vite-config/src/config/application.ts:81-85` + `plugins/importmap.ts` |
| 兜底组件复用 | `main/src/views/_core/fallback/` |
