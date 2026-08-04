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

import type { LifecycleExports, MicroAppConfig, MountProps, UnmountResult } from '@ydsz/micro-runtime';
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

/** 沙箱类型：snapshot（默认，性能好）| proxy（隔离强，性能略低）| iframe（CSS+DOM 强隔离兜底） */
export type SandboxType = 'snapshot' | 'proxy' | 'iframe';

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
  /** 沙箱类型：snapshot（默认）| proxy | iframe */
  sandboxType: SandboxType;
  /** 最近一次加载的性能指标（为监控提供数据） */
  loadMetrics: null | { duration: number; fromCache: boolean };
  error: null | string;
  /** 最近一次激活的时间戳（用于 LRU 淘汰保活实例） */
  lastActivatedAt: number;
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
    sandboxType: 'snapshot', // 默认使用快照沙箱
    loadMetrics: null,
    error: null,
    lastActivatedAt: 0,
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
 * 激活子应用：加载 → 挂载。
 * 若 keepAlive 且已有缓存 DOM，直接放回容器。
 *
 * @param instance - 子应用实例
 * @param container - 挂载容器
 * @param loadOpts - 加载选项（超时、重试）
 * @param callbacks - 细化阶段回调（v3.3）：
 *   - onLoaded: loadApp 完成、LifecycleExports 就绪后触发（keepAlive 复用路径不触发）
 *   - onBeforeMount: mount() 调用之前、沙箱进入之后触发（keepAlive 复用路径不触发）
 */
export async function activateApp(
  instance: AppInstance,
  container: HTMLElement,
  loadOpts: LoadOptions = {},
  callbacks: {
    onBeforeMount?: (instance: AppInstance, container: HTMLElement) => void;
    onLoaded?: (instance: AppInstance) => void;
  } = {},
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
    logger.debug(`${config.name} entered proxy sandbox`);
  } else if (instance.sandboxType === 'iframe') {
    // iframe 沙箱：在主容器内创建 iframe，子应用挂载到 iframe document
    instance.iframeSandbox = createIframeSandbox(config.name, container);
    instance.iframeSandbox.activate();
    // 将 mountProps 的容器指向 iframe 内的挂载容器
    if (instance.iframeSandbox.container) {
      mountProps.container = instance.iframeSandbox.container;
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
export async function deactivateApp(instance: AppInstance): Promise<UnmountResult> {
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
      // 调用 deactivate 生命周期钩子
      if (instance.exports?.deactivate) {
        try {
          await instance.exports.deactivate();
        } catch (err) {
          logger.error(`${config.name} deactivate hook failed:`, err);
        }
      }
      logger.debug(`${config.name} detached (keepAlive)`);

      // LRU 淘汰：保活实例数超限时卸载最久未访问的子应用
      evictKeepAliveIfNeeded();

      return { name: config.name, success: true };
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

/** 设置保活模式 */
export function setKeepAlive(name: string, keep: boolean): void {
  const instance = appInstances.get(name);
  if (instance) {
    instance.keepAlive = keep;
  }
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
 * 调用时机：deactivateApp keepAlive 摘除 DOM 之后。
 * maxKeepAliveApps 为 0 时禁用淘汰。
 */
async function evictKeepAliveIfNeeded(): Promise<void> {
  if (maxKeepAliveApps <= 0) return;

  const cached: AppInstance[] = [];
  for (const instance of appInstances.values()) {
    if (instance.keepAlive && instance.status === 'UNMOUNTED' && instance.cachedRoot) {
      cached.push(instance);
    }
  }

  while (cached.length > maxKeepAliveApps) {
    // 按 lastActivatedAt 升序排序，取最久未访问的
    cached.sort((a, b) => a.lastActivatedAt - b.lastActivatedAt);
    const victim = cached.shift()!;

    logger.debug(
      `LRU evicting keep-alive app "${victim.config.name}" ` +
        `(cached=${cached.length + 1}, max=${maxKeepAliveApps})`,
    );

    // 完整卸载（非 keepAlive 方式），释放全部资源
    victim.keepAlive = false;
    try {
      if (victim.exports) {
        await victim.exports.unmount({
          container: victim.cachedParent as HTMLElement || document.createElement('div'),
          basename: victim.config.activeRule,
        });
      }
    } catch (err) {
      logger.error(`LRU evict unmount failed for "${victim.config.name}":`, err);
    }

    // 清理沙箱
    if (victim.sandboxType === 'proxy' && victim.proxySandbox) {
      victim.proxySandbox.cleanup();
      victim.proxySandbox = null;
    } else if (victim.sandbox) {
      exitSandbox(victim.sandbox);
      victim.sandbox = null;
    }

    removeStylesheets(victim.config.name);
    victim.cachedRoot = null;
    victim.cachedParent = null;
    victim.exports = null;
    victim.status = 'NOT_LOADED';
    victim.error = null;
  }
}

/**
 * 内存压力检查：当 JS 堆内存超过阈值时强制卸载所有非活跃保活实例。
 *
 * 由外部（如 monitor 模块的定时巡检）调用，当 performance.memory.usedJSHeapSize
 * 超过 thresholdMB 时触发紧急释放。
 *
 * @param thresholdMB - 内存阈值（MB），默认 500
 */
export async function evictAllKeepAliveOnMemoryPressure(thresholdMB = 500): Promise<void> {
  const performance = (window as unknown as { performance?: { memory?: { usedJSHeapSize: number } } }).performance;
  const usedMB = performance?.memory ? performance.memory.usedJSHeapSize / 1024 / 1024 : 0;

  if (usedMB < thresholdMB) return;

  logger.warn(
    `Memory pressure detected (${usedMB.toFixed(0)}MB > ${thresholdMB}MB), evicting all keep-alive instances`,
  );

  for (const instance of appInstances.values()) {
    if (instance.keepAlive && instance.status === 'UNMOUNTED' && instance.cachedRoot) {
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
      removeStylesheets(instance.config.name);
      instance.cachedRoot = null;
      instance.cachedParent = null;
      instance.exports = null;
      instance.status = 'NOT_LOADED';
    }
  }
}
