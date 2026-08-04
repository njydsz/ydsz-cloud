/**
 * 微应用运行时类型定义
 *
 * 接口层不绑定任何内核实现（qiankun / wujie / 自研 micro-kernel），
 * 主应用与子应用业务代码仅依赖此接口。
 *
 * @path comm/effects/micro-runtime/src/types.ts
 * @author ydsz-team
 * @since 3.0.0
 */

/** 子应用激活规则类型 */
export type ActiveRule = string | RegExp | ((path: string) => boolean);

/**
 * 沙箱类型。
 *
 * - `snapshot`（默认）：快照沙箱，性能最佳，仅防意外污染 window。
 * - `proxy`：Proxy fakeWindow 数据隔离层，子应用通过 mountProps.fakeWindow 可读写隔离数据。
 *   注意 ESM 路线无法用 with 拦截顶层全局访问，详见 proxy-sandbox.ts 边界声明。
 * - `iframe`：iframe 强隔离（CSS + DOM + window），适用于全局样式冲突的子应用兜底。
 *   跨 realm 通信通过内置 postMessage 桥接 globalState，无需业务侧处理。
 *
 * @since 3.6.0
 */
export type SandboxType = 'snapshot' | 'proxy' | 'iframe';

/** 子应用注册配置（对齐现有 main/src/qiankun/index.ts microApps） */
export interface MicroAppConfig {
  /** 应用唯一标识（如 'project-web'） */
  name: string;
  /** 入口 URL — prod 为子路径，dev 为 localhost 端口 */
  entry: string;
  /**
   * 挂载容器，支持两种模式：
   * - string: CSS 选择器（如 '#subapp-container'）
   * - HTMLElement: 直接传入 DOM 元素（适用于动态创建容器的场景）
   */
  container: string | HTMLElement;
  /**
   * 激活规则，支持三种模式：
   * - string: 路由前缀匹配（如 '/ydsz-proj'）
   * - RegExp: 正则表达式匹配（如 /^\/ydsz-proj\/.*\/detail$/）
   * - function: 自定义匹配函数（如 (path) => path.includes('/special')）
   */
  activeRule: ActiveRule;
  /** 自定义 props（注入子应用 mount 参数） */
  props?: Record<string, unknown>;
  /**
   * 沙箱类型（v3.6.0 对外开放，未配置时默认 'snapshot'）。
   *
   * 业务侧可在 micro-apps.config.ts 注册表中按子应用指定沙箱类型，
   * 例如对全局样式冲突的子应用设置 `sandbox: 'iframe'`。
   *
   * @since 3.6.0
   */
  sandbox?: SandboxType;
}

/** 子应用挂载参数（与 qiankun mountProps 对齐语义） */
export interface MountProps {
  container: HTMLElement;
  basename: string;
  /**
   * Proxy 沙箱注入的 fakeWindow（仅当 sandboxType='proxy' 时存在）。
   *
   * 子应用可通过此对象读写隔离的全局数据，避免直接污染主 window。
   * 未启用 proxy 沙箱时为 undefined，子应用应回退到 window。
   *
   * @since 3.6.0
   */
  fakeWindow?: Record<string, unknown>;
  /**
   * iframe 沙箱注入的 contentWindow（仅当 sandboxType='iframe' 时存在）。
   *
   * 子应用如需直接操作 iframe 内的 document/window，可通过此引用访问。
   * 跨 realm 通信（globalState）已由内核内置 postMessage 桥接，业务侧通常无需直接使用。
   *
   * @since 3.6.0
   */
  iframeWindow?: Window;
  /** 主应用注入的自定义 props */
  [key: string]: unknown;
}

/** 子应用生命周期导出（ESM entry 必须导出 mount/unmount） */
export interface LifecycleExports {
  bootstrap?: (props: MountProps) => Promise<void>;
  mount: (props: MountProps) => Promise<void>;
  unmount: (props: MountProps) => Promise<void>;
  update?: (props: MountProps) => Promise<void>;
  /** keep-alive 激活时调用（可选） */
  activate?: () => Promise<void> | void;
  /** keep-alive 停用时调用（可选） */
  deactivate?: () => Promise<void> | void;
}

/** 内核生命周期钩子 */
export type LifecycleHook = (app: MicroAppConfig) => Promise<void> | void;

/** 内核错误钩子（接收错误对象） */
export type ErrorLifecycleHook = (app: MicroAppConfig, error: unknown) => Promise<void> | void;

/**
 * 内核支持的生命周期钩子名。
 *
 * 细化阶段（v3.3）：
 *   beforeLoad  → 子应用开始加载 ESM 模块（manifest fetch + dynamic import）
 *   afterLoad   → 子应用 ESM 模块加载完成、LifecycleExports 已就绪
 *   beforeMount → 子应用 mount() 调用之前（沙箱已进入）
 *   afterMount  → 子应用 mount() 完成，DOM 已挂载
 *   afterUnmount → 子应用卸载完成
 *   error       → 加载或挂载失败
 *
 * SubAppContainer 等订阅方可基于细化钩子驱动真实进度条推进，
 * 避免仅有 beforeLoad/afterMount 两个粗粒度节点导致进度跳变。
 */
export type LifecycleHookName =
  | 'afterLoad'
  | 'afterMount'
  | 'afterUnmount'
  | 'beforeLoad'
  | 'beforeMount'
  | 'error';

/** 权限检查函数类型 */
export type PermissionChecker = (codes: string[]) => boolean;

/** 内核启动选项 */
export interface StartOptions {
  /** 沙箱策略 */
  sandbox?: {
    /** 启用样式隔离 */
    styleIsolation?: boolean;
  };
  /** 预加载策略：false 不预加载 / true 全部预加载 / 函数按应用名返回 true */
  prefetch?: boolean | ((app: MicroAppConfig) => boolean);
  /** 权限检查器，用于预加载时过滤无权限的应用 */
  permissionChecker?: PermissionChecker;
}

/** 微应用运行时卸载结果 */
export interface UnmountResult {
  name: string;
  success: boolean;
  reason?: string;
}

/**
 * 内核实现必须满足的接口。
 *
 * 内核可以是对应于 qiankun、wujie、自研 micro-kernel 的不同实现。
 * registerKernel / createRuntime 实现内核的可插拔注册与选择。
 */
export interface MicroRuntime {
  /** 注册子应用列表（必须在 start 前调用） */
  registerApps(apps: MicroAppConfig[]): void;

  /** 查询已注册应用 */
  getRegisteredApps(): ReadonlyArray<MicroAppConfig>;

  /** 启动微前端运行时 */
  start(options?: StartOptions): void;

  /**
   * 手动卸载指定子应用（供 tabbar 关闭页签时调用）。
   *
   * 一般内核（qiankun）不暴露此能力，
   * 自研 micro-kernel 可利用此接口实现细粒度页签控制。
   */
  unmountApp(name: string): Promise<UnmountResult>;

  /**
   * 保活控制：切走时不销毁 DOM，切回时直接复用。
   *
   * micro-kernel 原生支持；qiankun adapter 可通过销毁/重建模拟。
   */
  setKeepAlive(name: string, keep: boolean): void;

  /** 路由导航（由内核决定走主应用 router 还是整页跳转） */
  navigateTo(path: string): void;

  /**
   * 手动预加载指定子应用的 ESM 资源（不执行 mount）。
   *
   * 用于 hover 预热等场景：用户悬停菜单链接时提前拉取模块与样式，
   * 后续点击切换时仅差 mount 耗时。已加载的应用会通过浏览器缓存复用，
   * 重复调用安全（幂等）。
   *
   * @returns Promise 在资源加载完成（或失败）时 resolve
   */
  prefetchApp(name: string): Promise<void>;

  /**
   * 添加生命周期钩子。
   *
   * 'error' 钩子签名额外接收错误对象，其它钩子只接收 app。
   * 返回取消订阅函数，组件卸载时调用以避免内存泄漏。
   */
  addLifecycleHook(
    hookName: LifecycleHookName,
    hook: ErrorLifecycleHook | LifecycleHook,
  ): () => void;

  /** 获取当前激活的应用名 */
  getActiveAppName(): null | string;
}
