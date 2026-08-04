/**
 * 生命周期调度器 + 保活控制 + 轻量沙箱集成
 *
 * 每个子应用一个 AppInstance 实例：
 * - 加载 → parsed LifecycleExports + metadata
 * - activate → enterSandbox → mount
 * - deactivate → unmount → exitSandbox（keepAlive 时只 detach，沙箱不退出）
 * - keepAlive 激活 → container.appendChild(cachedEl) 直接复用，零重新渲染
 *
 * @path comm/effects/micro-kernel/src/scheduler.ts
 * @author ydsz-team
 * @since 3.0.0
 */

import type {
  LifecycleExports,
  MicroAppConfig,
  MountProps,
  SandboxType,
  UnmountResult,
} from '@ydsz/micro-runtime';
import { loadApp, removeStylesheets } from './loader';
import type { LoadOptions, LoadResult, Manifest } from './loader';
import { enterSandbox, exitSandbox } from './sandbox';
import type { SandboxInstance } from './sandbox';
import { createProxySandbox } from './proxy-sandbox';
import type { ProxySandboxInstance } from './proxy-sandbox';
import { createIframeSandbox } from './iframe-sandbox';
import type { IframeSandboxInstance } from './iframe-sandbox';
import { createLogger } from '@ydsz-core/shared/utils';

/** 模块级日志器（生命周期事件默认 debug 级别，避免生产噪音） */
const logger = createLogger('MicroKernel');

/**
 * 解析容器配置：支持 CSS 选择器字符串或 HTMLElement 实例。
 *
 * @param container - 容器配置（string | HTMLElement）
 * @returns 解析后的 HTMLElement，未找到时返回 null
 */
function resolveContainer(container: string | HTMLElement): HTMLElement | null {
  if (typeof container === 'string') {
    return document.querySelector(container) as HTMLElement | null;
  }
  return container;
}

/** 子应用生命周期状态：未加载 / 加载中 / 已加载 / 已挂载 / 已卸载 */
export type AppStatus = 'NOT_LOADED' | 'LOADING' | 'LOADED' | 'MOUNTED' | 'UNMOUNTED';

/**
 * 沙箱类型（re-export 自 micro-runtime，保持单一事实源）。
 *
 * @since 3.6.0 从 micro-runtime 导入，不再在本文件定义
 */
export type { SandboxType } from '@ydsz/micro-runtime';

/** 单个子应用在调度器中的运行时实例，含配置、生命周期导出、状态与保活缓存 */
export interface AppInstance {
  config: MicroAppConfig;
  status: AppStatus;
  exports: null | LifecycleExports;
  keepAlive: boolean;
  /** keepAlive 时保存的 DOM 根节点 */
  cachedRoot: null | HTMLElement;
  /** keepAlive 时原始父节点（切回时 appendChild 回此处） */
  cachedParent: null | Node;
  /** 快照沙箱实例（mount 时创建，unmount 时销毁；keepAlive 时保留） */
  sandbox: null | SandboxInstance;
  /** Proxy 沙箱实例（当 sandboxType 为 'proxy' 时使用） */
  proxySandbox: null | ProxySandboxInstance;
  /** iframe 沙箱实例（当 sandboxType 为 'iframe' 时使用） */
  iframeSandbox: null | IframeSandboxInstance;
  /**
   * iframe 沙箱 globalState 桥接的取消订阅函数（v3.6.0）。
   *
   * 在 activateApp 创建 iframe 沙箱后建立桥接订阅，unmount 时调用以避免内存泄漏。
   */
  iframeGlobalStateUnsub: null | (() => void);
  /** 沙箱类型：snapshot（默认）| proxy | iframe */
  sandboxType: SandboxType;
  /** 最近一次加载的性能指标（为监控提供数据） */
  loadMetrics: null | { duration: number; fromCache: boolean };
  error: null | string;
  /** 最近一次激活的时间戳（用于 LRU 淘汰保活实例） */
  lastActivatedAt: number;
  /** v3.7.0: 本次保活缓存的创建时间（用于 TTL 过期检测） */
  keepAliveSince: number;
  /** v3.7.0: 是否被"固定"（pin 住），固定实例在 LRU 淘汰时被跳过引脚保护 */
  pinned: boolean;
  /**
   * 最近一次加载得到的 manifest（v3.3 新增）。
   *
   * build 模式下含子应用自描述的 routes（骨架屏类型映射），
   * 主应用容器据此细化骨架屏；dev 模式为 null。
   */
  manifest: null | Manifest;
}

/** 保活实例数上限（默认 5，超限按 LRU 淘汰最久未访问的子应用） */
let maxKeepAliveApps = 5;

/**
 * v3.7.0: 保活 TTL（毫秒）。
 *
 * 保活实例在缓存中保留时间未超过 TTL 时不会因为 LRU 策略被淘汰
 *（除非触发内存压力或手动强制卸载）。默认 30 分钟。设置为 0 表示禁用 TTL 保护，
 * 回退到既有 LRU-only 策略。
 */
let keepAliveTTL = 30 * 60 * 1_000;

/** 保活缓存创建时间戳序列（严格递增，用作 keepAliveSince 取值） */
let keepAliveTimestamp = 1;

/**
 * 设置保活实例数上限。
 *
 * 频繁切换 9 个子应用时，keep-alive 会持续累积 DOM + Vue 实例 + Pinia store + ECharts 实例，
 * 可能导致内存涨到 500MB+。设置上限后，超限的保活实例按 LRU 策略完整卸载释放内存。
 *
 * @param max - 最大保活实例数（设为 0 禁用 LRU 淘汰）
 */
export function setMaxKeepAliveApps(max: number): void {
  maxKeepAliveApps = max;
}

const appInstances = new Map<string, AppInstance>();

/** 创建并注册一个新的子应用实例，初始状态为 NOT_LOADED */
export function createAppInstance(config: MicroAppConfig): AppInstance {
  const instance: AppInstance = {
    config,
    status: 'NOT_LOADED',
    exports: null,
    keepAlive: false,
    cachedRoot: null,
    cachedParent: null,
    sandbox: null,
    proxySandbox: null,
    iframeSandbox: null,
    iframeGlobalStateUnsub: null,
    // v3.6.0: 从 MicroAppConfig.sandbox 读取沙箱类型，未配置时默认 'snapshot'
    sandboxType: config.sandbox ?? 'snapshot',
    loadMetrics: null,
    error: null,
    lastActivatedAt: 0,
    // v3.7.0: 保活时间戳与 pin 标记初始化
    keepAliveSince: 0,
    pinned: false,
    manifest: null,
  };
  appInstances.set(config.name, instance);
  return instance;
}

/** 按子应用名称获取已注册的实例，未注册时返回 undefined */
export function getAppInstance(name: string): AppInstance | undefined {
  return appInstances.get(name);
}

/** 获取全部已注册的子应用实例列表，供调试与巡检使用 */
export function getAllInstances(): AppInstance[] {
  return [...appInstances.values()];
}

/**
 * globalState 桥接接口（v3.6.0 新增）。
 *
 * 用于 iframe 沙箱场景下的跨 realm globalState 同步。
 * kernel 持有真实的 globalStateAPI，通过此接口传给 scheduler，
 * scheduler 在创建 iframe 沙箱后建立桥接：
 * - 初始同步：postToChild(getGlobalState())
 * - 子 → 主：onChildMessage(patch) → setGlobalState(patch)
 * - 主 → 子：注入代理 _globalState 到 mountProps，其 setGlobalState 调用 postToChild
 *
 * @since 3.6.0
 */
export interface GlobalStateBridge {
  /** 获取当前 globalState 快照 */
  getGlobalState: () => Record<string, unknown>;
  /** 设置 globalState（广播给所有订阅者） */
  setGlobalState: (patch: Record<string, unknown>) => void;
  /** 订阅 globalState 变化（用于主 → 子同步） */
  onGlobalStateChange: (listener: (state: Record<string, unknown>) => void, fireImmediately?: boolean) => () => void;
}

/**
 * P0-P2: deactivateApp 的返回结果，扩展 UnmountResult 以包含 LRU 淘汰信息。
 *
 * 当 keepAlive 卸载触发 LRU 淘汰时，`evicted` 字段列出被淘汰的应用名，
 * 供调用方（如 Tab 栏联动关闭对应标签）感知。
 *
 * @since 3.6.1
 */
export interface DeactivateResult extends UnmountResult {
  /** P0-P2: 本次卸载过程中被 LRU 淘汰的保活应用名列表（如有） */
  evicted?: string[];
}

/**
 * P0-P2: 派发 before-evict 事件，允许外部监听者阻止淘汰。
 *
 * 事件 `micro-kernel:before-evict` 为 cancelable，调用 `event.preventDefault()`
 * 可阻止该应用的淘汰。返回 `true` 表示允许淘汰，`false` 表示被阻止。
 */
function dispatchBeforeEvict(appName: string): boolean {
  const event = new CustomEvent('micro-kernel:before-evict', {
    detail: { appName },
    cancelable: true,
    bubbles: true,
  });
  // dispatchEvent 返回 false 表示调用了 preventDefault
  return window.dispatchEvent(event);
}

/**
 * P0-P2: 获取动态内存阈值。
 *
 * 基于 `performance.memory.jsHeapSizeLimit`（Chrome 提供）计算：
 * - 取堆大小限制的 80% 作为阈值
 * - 不可用时回退到传入的默认值（默认 500MB）
 */
function getDynamicMemoryThreshold(defaultMB = 500): number {
  const perf = (window as unknown as {
    performance?: {
      memory?: {
        jsHeapSizeLimit?: number;
        usedJSHeapSize?: number;
      };
    };
  }).performance;
  const limit = perf?.memory?.jsHeapSizeLimit;
  if (limit && limit > 0) {
    return (limit / 1024 / 1024) * 0.8;
  }
  return defaultMB;
}

/**
 * 激活子应用：加载 → 挂载。
 * 若 keepAlive 且已有缓存 DOM，直接放回容器。
 *
 * @param instance - 子应用实例
 * @param container - 挂载容器
 * @param loadOpts - 加载选项（超时、重试）
 * @param callbacks - 细化阶段回调（v3.3）：
 *   - onLoaded: loadApp 完成、LifecycleExports 就绪后触发（keepAlive 复用路径不触发）
 *   - onBeforeMount: mount() 调用之前、沙箱进入之后触发（keepAlive 复用路径不触发）
 * @param globalStateBridge - 可选的 globalState 桥接（v3.6.0，iframe 沙箱场景使用）
 */
export async function activateApp(
  instance: AppInstance,
  container: HTMLElement,
  loadOpts: LoadOptions = {},
  callbacks: {
    onBeforeMount?: (instance: AppInstance, container: HTMLElement) => void;
    onLoaded?: (instance: AppInstance) => void;
  } = {},
  globalStateBridge?: GlobalStateBridge,
): Promise<void> {
  const { config } = instance;

  if (instance.status === 'MOUNTED') return;

  // keepAlive 复用
  if (instance.keepAlive && instance.cachedRoot && instance.cachedParent) {
    container.appendChild(instance.cachedRoot);
    instance.cachedParent = null;
    instance.status = 'MOUNTED';
    instance.lastActivatedAt = Date.now();
    // 调用 activate 生命周期钩子
    if (instance.exports?.activate) {
      try {
        await instance.exports.activate();
      } catch (err) {
        logger.error(`${config.name} activate hook failed:`, err);
      }
    }
    logger.debug(`${config.name} reattached (keepAlive)`);
    return;
  }

  // 加载（如未加载）
  if (!instance.exports) {
    instance.status = 'LOADING';
    try {
      const result: LoadResult = await loadApp(config, loadOpts);
      instance.exports = result.exports;
      instance.loadMetrics = { duration: result.duration, fromCache: result.fromCache };
      // v3.3: 记录 manifest 供主应用容器读取 routes（骨架屏细化）
      instance.manifest = result.manifest;
      instance.status = 'LOADED';
      // v3.3: 通知外部"加载完成"阶段（用于进度条推进、骨架屏细化）
      callbacks.onLoaded?.(instance);
    } catch (err) {
      instance.status = 'NOT_LOADED';
      instance.error = String(err);
      throw err;
    }
  }

  // 挂载
  const mountProps: MountProps = {
    container,
    basename: config.activeRule,
    ...config.props,
  };

  // 设置容器属性，与 PostCSS 构建期 CSS scoping 联动
  container.setAttribute('data-micro-app', config.name);

  // 根据沙箱类型进入对应的沙箱环境
  if (instance.sandboxType === 'proxy') {
    // Proxy 沙箱：创建并激活
    instance.proxySandbox = createProxySandbox(config.name);
    instance.proxySandbox.activate();
    // v3.6.0: 注入 fakeWindow 到 mountProps，子应用可通过 mountProps.fakeWindow 读写隔离数据
    mountProps.fakeWindow = instance.proxySandbox.fakeWindow;
    logger.debug(`${config.name} entered proxy sandbox`);
  } else if (instance.sandboxType === 'iframe') {
    // iframe 沙箱：在主容器内创建 iframe，子应用挂载到 iframe document
    instance.iframeSandbox = createIframeSandbox(config.name, container);
    instance.iframeSandbox.activate();
    // 将 mountProps 的容器指向 iframe 内的挂载容器
    if (instance.iframeSandbox.container) {
      mountProps.container = instance.iframeSandbox.container;
    }
    // v3.6.0: 注入 iframeWindow 到 mountProps，子应用可访问 iframe contentWindow
    if (instance.iframeSandbox.contentWindow) {
      mountProps.iframeWindow = instance.iframeSandbox.contentWindow;
    }
    // v3.6.0: 建立 globalState 跨 realm 桥接
    if (globalStateBridge) {
      // 1. 初始同步：把当前 globalState 快照发送给子应用
      instance.iframeSandbox.postToChild(globalStateBridge.getGlobalState());
      // 2. 子 → 主：监听子应用回传的 setGlobalState 调用
      const unsubChild = instance.iframeSandbox.onChildMessage((patch) => {
        if (patch && typeof patch === 'object') {
          globalStateBridge.setGlobalState(patch as Record<string, unknown>);
        }
      });
      // 3. 主 → 子：订阅 globalState 变化，同步给子应用
      const unsubMain = globalStateBridge.onGlobalStateChange((state) => {
        instance.iframeSandbox?.postToChild(state);
      });
      // 4. 注入代理 _globalState 到 mountProps（子应用通过此代理读写，
      //    代理内部走 postMessage，避免跨 realm 引用问题）
      mountProps._globalState = {
        getGlobalState: () => globalStateBridge.getGlobalState(),
        setGlobalState: (patch: Record<string, unknown>) => {
          globalStateBridge.setGlobalState(patch);
          // 同时同步给子应用（setGlobalState 已会触发 onGlobalStateChange 广播，
          // 但为保证子应用即时收到，显式 postToChild 一次）
          instance.iframeSandbox?.postToChild(globalStateBridge.getGlobalState());
        },
        onGlobalStateChange: (listener: (state: Record<string, unknown>) => void, fireImmediately?: boolean) => {
          return globalStateBridge.onGlobalStateChange(listener, fireImmediately);
        },
      };
      // 保存取消订阅函数，unmount 时清理（避免内存泄漏）
      instance.iframeGlobalStateUnsub = () => {
        unsubChild();
        unsubMain();
      };
    }
    logger.debug(`${config.name} entered iframe sandbox`);
  } else {
    // 快照沙箱（默认）：进入快照沙箱
    instance.sandbox = enterSandbox();
    logger.debug(`${config.name} entered snapshot sandbox`);
  }

  // v3.3: 通知外部"挂载之前"阶段（沙箱已进入，mount 即将调用）
  callbacks.onBeforeMount?.(instance, container);

  try {
    await instance.exports.mount(mountProps);
    instance.status = 'MOUNTED';
    instance.error = null;
    instance.lastActivatedAt = Date.now();
    logger.debug(`${config.name} mounted`);
  } catch (err) {
    // 挂载失败：退出对应的沙箱
    if (instance.sandboxType === 'proxy' && instance.proxySandbox) {
      instance.proxySandbox.cleanup();
      instance.proxySandbox = null;
    } else if (instance.sandboxType === 'iframe' && instance.iframeSandbox) {
      // v3.6.0: 清理 globalState 桥接订阅
      instance.iframeGlobalStateUnsub?.();
      instance.iframeGlobalStateUnsub = null;
      instance.iframeSandbox.cleanup();
      instance.iframeSandbox = null;
    } else if (instance.sandbox) {
      exitSandbox(instance.sandbox);
      instance.sandbox = null;
    }
    instance.status = 'LOADED';
    instance.error = String(err);
    throw err;
  }
}

/**
 * 停用子应用。
 * keepAlive 时摘除 DOM（不销毁组件树状态），否则完整卸载。
 */
export async function deactivateApp(instance: AppInstance): Promise<DeactivateResult> {
  const { config } = instance;

  if (instance.status !== 'MOUNTED') {
    return { name: config.name, success: true };
  }

  if (instance.keepAlive) {
    const container = resolveContainer(config.container);
    if (container) {
      instance.cachedRoot = container.firstElementChild as HTMLElement;
      instance.cachedParent = container;
      if (instance.cachedRoot) {
        container.removeChild(instance.cachedRoot);
      }
      instance.status = 'UNMOUNTED';
      // v3.7.0: 记录保活缓存创建时间（用于 TTL 过期检测）
      instance.keepAliveSince = keepAliveTimestamp++;
      // 调用 deactivate 生命周期钩子
      if (instance.exports?.deactivate) {
        try {
          await instance.exports.deactivate();
        } catch (err) {
          logger.error(`${config.name} deactivate hook failed:`, err);
        }
      }
      logger.debug(`${config.name} detached (keepAlive)`);

      // P0-P2: LRU 淘汰：保活实例数超限时卸载最久未访问的子应用
      const evicted = await evictKeepAliveIfNeeded();

      return { name: config.name, success: true, evicted: evicted.length > 0 ? evicted : undefined };
    }
  }

  // 完整卸载
  try {
    await instance.exports!.unmount({
      container: resolveContainer(config.container) || document.createElement('div'),
      basename: config.activeRule,
    });

    // 根据沙箱类型退出对应的沙箱环境
    if (instance.sandboxType === 'proxy') {
      // Proxy 沙箱：清理并释放
      if (instance.proxySandbox) {
        instance.proxySandbox.cleanup();
        instance.proxySandbox = null;
      }
    } else if (instance.sandboxType === 'iframe') {
      // v3.6.0: 清理 globalState 桥接订阅
      instance.iframeGlobalStateUnsub?.();
      instance.iframeGlobalStateUnsub = null;
      // iframe 沙箱：清理 iframe 并释放
      if (instance.iframeSandbox) {
        instance.iframeSandbox.cleanup();
        instance.iframeSandbox = null;
      }
    } else {
      // 快照沙箱：退出并恢复 window
      if (instance.sandbox) {
        exitSandbox(instance.sandbox);
        instance.sandbox = null;
      }
    }

    // 移除容器级 CSS scoping 属性（data-micro-app）
    const containerEl = resolveContainer(config.container);
    if (containerEl) {
      containerEl.removeAttribute('data-micro-app');
    }

    removeStylesheets(config.name);
    instance.exports = null;
    instance.status = 'NOT_LOADED';
    instance.error = null;
    logger.debug(`${config.name} unmounted`);
    return { name: config.name, success: true };
  } catch (err) {
    // unmount 失败仍尝试退出沙箱
    if (instance.sandboxType === 'proxy') {
      if (instance.proxySandbox) {
        instance.proxySandbox.cleanup();
        instance.proxySandbox = null;
      }
    } else if (instance.sandboxType === 'iframe') {
      // v3.6.0: 清理 globalState 桥接订阅
      instance.iframeGlobalStateUnsub?.();
      instance.iframeGlobalStateUnsub = null;
      if (instance.iframeSandbox) {
        instance.iframeSandbox.cleanup();
        instance.iframeSandbox = null;
      }
    } else {
      if (instance.sandbox) {
        exitSandbox(instance.sandbox);
        instance.sandbox = null;
      }
    }
    instance.error = String(err);
    return { name: config.name, success: false, reason: String(err) };
  }
}

/**
 * v3.7.0: 设置指定应用的"固定"（pin）状态。
 *
 * 固定后的保活实例在 LRU 淘汰时不会被移除（即使超过 maxKeepAliveApps），
 * 适用于"常用应用常驻"的業務场景（如消息中心、工作台）或用户显式 pin 住的标签页。
 *
 * @param name - 应用名
 * @param pin - 是否固定
 * @since 3.7.0
 */
export function setPinnedApp(name: string, pin: boolean): void {
  const instance = appInstances.get(name);
  if (instance) {
    instance.pinned = pin;
  }
}

/**
 * v3.7.0: 设置保活 TTL（全局）。
 *
 * @param ttlMs - TTL（毫秒），0 表示禁用 TTL 保护
 * @since 3.7.0
 */
export function setKeepAliveTTL(ttlMs: number): void {
  keepAliveTTL = Math.max(0, ttlMs);
}

/**
 * v3.7.0: 获取当前保活 TTL（毫秒）。
 */
export function getKeepAliveTTL(): number {
  return keepAliveTTL;
}

/**
 * 重置调度器状态：清空全部子应用实例并恢复保活上限默认值。
 *
 * 供 kernel `_stop()` 在 HMR / 测试场景调用，
 * 确保新一轮内核启动时不会残留上一轮的实例（避免旧实例的 stale exports / cachedRoot 污染）。
 *
 * 调用前应确保所有已挂载实例已通过 `deactivateApp` 完整卸载。
 *
 * @since 3.6.1
 */
export function resetScheduler(): void {
  appInstances.clear();
  maxKeepAliveApps = 5;
  keepAliveTTL = 30 * 60 * 1_000;
}

/**
 * 统计当前处于 keep-alive 缓存状态（UNMOUNTED + keepAlive + cachedRoot 存在）的实例数。
 */
export function getKeepAliveCount(): number {
  let count = 0;
  for (const instance of appInstances.values()) {
    if (instance.keepAlive && instance.status === 'UNMOUNTED' && instance.cachedRoot) {
      count++;
    }
  }
  return count;
}

/**
 * LRU 淘汰：当保活实例数超过 maxKeepAliveApps 时，按 lastActivatedAt 升序完整卸载
 * 最久未访问的子应用，释放 DOM + Vue 实例 + Pinia store + ECharts 等资源。
 *
 * P0-P2:
 * - 淘汰前派发 `micro-kernel:before-evict` 事件（cancelable），允许监听者阻止淘汰
 * - 返回被淘汰的应用名列表，供 deactivateApp 传递给调用方
 *
 * v3.7.0: 增加 TTL 过期策略和 pin 保护：
 * - TTL 未过期 + 未超上限：不淘汰（保持保活）
 * - TTL 过期：即使未超上限也要淘汰（保护：pinned 实例跳过）
 * - pinned 实例：除非触发内存压力，否则始终保留
 *
 * 调用时机：deactivateApp keepAlive 摘除 DOM 之后。
 * maxKeepAliveApps 为 0 时禁用淘汰。
 *
 * @returns 被淘汰的应用名列表
 */
async function evictKeepAliveIfNeeded(): Promise<string[]> {
  if (maxKeepAliveApps <= 0) return [];

  const cached: AppInstance[] = [];
  const now = keepAliveTimestamp;
  for (const instance of appInstances.values()) {
    if (instance.keepAlive && instance.status === 'UNMOUNTED' && instance.cachedRoot) {
      cached.push(instance);
    }
  }

  const evicted: string[] = [];

  // v3.7.0: TTL 过期淘汰 — 检查每个缓存实例是否超过 TTL
  // pinned 实例不参与 TTL 淘汰（除非内存压力场景）
  if (keepAliveTTL > 0) {
    for (const instance of cached) {
      if (instance.pinned) continue;
      const age = now - instance.keepAliveSince;
      if (age > keepAliveTTL) {
        // 超过 TTL，强制淘汰
        if (!dispatchBeforeEvict(instance.config.name)) continue;
        await evictSingleInstance(instance);
        evicted.push(instance.config.name);
      }
    }
    // 淘汰完后重新收集剩余缓存
    cached.splice(0, cached.length,
      ...[...appInstances.values()].filter(
        (i) => i.keepAlive && i.status === 'UNMOUNTED' && i.cachedRoot && !evicted.includes(i.config.name),
      ),
    );
  }

  // LRU 淘汰：保活实例数仍超限时
  while (cached.length > maxKeepAliveApps) {
    // 按 lastActivatedAt 升序排序，取最久未访问的
    cached.sort((a, b) => a.lastActivatedAt - b.lastActivatedAt);
    const victim = cached.shift()!;

    // pinned 实例：跳过（不淘汰）
    if (victim.pinned) continue;

    // 派发 before-evict 事件，允许外部阻止淘汰
    if (!dispatchBeforeEvict(victim.config.name)) {
      logger.debug(`LRU eviction of "${victim.config.name}" prevented by before-evict listener`);
      continue;
    }

    logger.debug(
      `LRU evicting keep-alive app "${victim.config.name}" ` +
        `(cached=${cached.length + 1}, max=${maxKeepAliveApps})`,
    );

    await evictSingleInstance(victim);
    evicted.push(victim.config.name);
  }

  return evicted;
}

/**
 * 完整卸载单个保活实例（共享逻辑）。
 */
async function evictSingleInstance(instance: AppInstance): Promise<void> {
  instance.keepAlive = false;
  try {
    if (instance.exports) {
      await instance.exports.unmount({
        container: instance.cachedParent as HTMLElement || document.createElement('div'),
        basename: instance.config.activeRule,
      });
    }
  } catch (err) {
    logger.error(`Evict unmount failed for "${instance.config.name}":`, err);
  }

  // 清理沙箱
  if (instance.sandboxType === 'proxy' && instance.proxySandbox) {
    instance.proxySandbox.cleanup();
    instance.proxySandbox = null;
  } else if (instance.sandboxType === 'iframe' && instance.iframeSandbox) {
    instance.iframeGlobalStateUnsub?.();
    instance.iframeGlobalStateUnsub = null;
    instance.iframeSandbox.cleanup();
    instance.iframeSandbox = null;
  } else if (instance.sandbox) {
    exitSandbox(instance.sandbox);
    instance.sandbox = null;
  }

  removeStylesheets(instance.config.name);
  instance.cachedRoot = null;
  instance.cachedParent = null;
  instance.exports = null;
  instance.status = 'NOT_LOADED';
  instance.error = null;
  instance.keepAliveSince = 0;
  logger.debug(`Evicted keep-alive app "${instance.config.name}"`);
}

/**
 * 内存压力检查：当 JS 堆内存超过阈值时强制卸载所有非活跃保活实例。
 *
 * P0-P2:
 * - 使用动态内存阈值（基于 performance.memory.jsHeapSizeLimit 的 80%），
 *   适配不同设备内存容量，而非固定的 500MB
 * - 淘汰前派发 before-evict 事件，允许监听者保留关键应用
 *
 * 由外部（如 monitor 模块的定时巡检、visibilitychange）调用。
 *
 * @param thresholdMB - 内存阈值（MB），默认使用动态阈值
 */
export async function evictAllKeepAliveOnMemoryPressure(thresholdMB?: number): Promise<void> {
  const effectiveThreshold = thresholdMB ?? getDynamicMemoryThreshold();
  const performance = (window as unknown as { performance?: { memory?: { usedJSHeapSize: number } } }).performance;
  const usedMB = performance?.memory ? performance.memory.usedJSHeapSize / 1024 / 1024 : 0;

  if (usedMB < effectiveThreshold) return;

  logger.warn(
    `Memory pressure detected (${usedMB.toFixed(0)}MB > ${effectiveThreshold.toFixed(0)}MB), evicting all keep-alive instances`,
  );

  for (const instance of appInstances.values()) {
    if (instance.keepAlive && instance.status === 'UNMOUNTED' && instance.cachedRoot) {
      // P0-P2: 派发 before-evict 事件，允许外部保留关键应用
      if (!dispatchBeforeEvict(instance.config.name)) {
        logger.debug(`Memory pressure eviction of "${instance.config.name}" prevented by before-evict listener`);
        continue;
      }

      instance.keepAlive = false;
      try {
        if (instance.exports) {
          await instance.exports.unmount({
            container: instance.cachedParent as HTMLElement || document.createElement('div'),
            basename: instance.config.activeRule,
          });
        }
      } catch {
        // 静默
      }
      if (instance.sandbox) {
        exitSandbox(instance.sandbox);
        instance.sandbox = null;
      }
      if (instance.proxySandbox) {
        instance.proxySandbox.cleanup();
        instance.proxySandbox = null;
      }
      // v3.6.0: 清理 iframe 沙箱及其 globalState 桥接订阅
      if (instance.iframeSandbox) {
        instance.iframeGlobalStateUnsub?.();
        instance.iframeGlobalStateUnsub = null;
        instance.iframeSandbox.cleanup();
        instance.iframeSandbox = null;
      }
      removeStylesheets(instance.config.name);
      instance.cachedRoot = null;
      instance.cachedParent = null;
      instance.exports = null;
      instance.status = 'NOT_LOADED';
    }
  }
}

/**
 * P0-P2: 设置 visibilitychange 自动释放保活实例。
 *
 * 当页面切换到后台（document.hidden）时，触发内存压力检查，
 * 自动释放非活跃的保活实例，减少后台内存占用。
 *
 * 用户切回前台时，被释放的应用会重新加载（牺牲少量加载时间换取内存优化）。
 *
 * @returns 清理函数，移除 visibilitychange 监听
 * @since 3.6.1
 */
export function setupVisibilityAutoRelease(): () => void {
  if (typeof document === 'undefined') return () => {};

  const handler = (): void => {
    if (document.hidden) {
      void evictAllKeepAliveOnMemoryPressure();
    }
  };
  document.addEventListener('visibilitychange', handler);
  return () => {
    document.removeEventListener('visibilitychange', handler);
  };
}
