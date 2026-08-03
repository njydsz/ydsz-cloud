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

/** 子应用注册配置（对齐现有 main/src/qiankun/index.ts microApps） */
export interface MicroAppConfig {
  /** 应用唯一标识（如 'project-web'） */
  name: string;
  /** 入口 URL — prod 为子路径，dev 为 localhost 端口 */
  entry: string;
  /** 挂载容器选择器（如 '#subapp-container'） */
  container: string;
  /** 激活规则（路由前缀，如 '/ydsz-proj'） */
  activeRule: string;
  /** 自定义 props（注入子应用 mount 参数） */
  props?: Record<string, unknown>;
}

/** 子应用挂载参数（与 qiankun mountProps 对齐语义） */
export interface MountProps {
  container: HTMLElement;
  basename: string;
  /** 主应用注入的自定义 props */
  [key: string]: unknown;
}

/** 子应用生命周期导出（ESM entry 必须导出 mount/unmount） */
export interface LifecycleExports {
  bootstrap?: (props: MountProps) => Promise<void>;
  mount: (props: MountProps) => Promise<void>;
  unmount: (props: MountProps) => Promise<void>;
  update?: (props: MountProps) => Promise<void>;
}

/** 内核生命周期钩子 */
export type LifecycleHook = (app: MicroAppConfig) => Promise<void> | void;

/** 内核启动选项 */
export interface StartOptions {
  /** 沙箱策略 */
  sandbox?: {
    /** 启用样式隔离 */
    styleIsolation?: boolean;
  };
  /** 预加载策略：false 不预加载 / true 全部预加载 / 函数按应用名返回 true */
  prefetch?: boolean | ((app: MicroAppConfig) => boolean);
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

  /** 添加生命周期钩子 */
  addLifecycleHook(hookName: 'beforeLoad' | 'afterMount' | 'afterUnmount', hook: LifecycleHook): void;

  /** 获取当前激活的应用名 */
  getActiveAppName(): null | string;
}
