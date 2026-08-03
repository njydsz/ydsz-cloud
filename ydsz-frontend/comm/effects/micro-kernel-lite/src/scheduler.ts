/**
 * 生命周期调度器 + 保活控制 + 轻量沙箱集成
 *
 * 每个子应用一个 AppInstance 实例：
 * - 加载 → parsed LifecycleExports + metadata
 * - activate → enterSandbox → mount
 * - deactivate → unmount → exitSandbox（keepAlive 时只 detach，沙箱不退出）
 * - keepAlive 激活 → container.appendChild(cachedEl) 直接复用，零重新渲染
 *
 * @path comm/effects/micro-kernel-lite/src/scheduler.ts
 * @author ydsz-team
 * @since 3.0.0
 */

import type { LifecycleExports, MicroAppConfig, MountProps, UnmountResult } from '@ydsz/micro-runtime';
import { loadApp, removeStylesheets } from './loader';
import type { LoadOptions, LoadResult } from './loader';
import { enterSandbox, exitSandbox } from './sandbox';
import type { SandboxInstance } from './sandbox';

/** 子应用生命周期状态：未加载 / 加载中 / 已加载 / 已挂载 / 已卸载 */
export type AppStatus = 'NOT_LOADED' | 'LOADING' | 'LOADED' | 'MOUNTED' | 'UNMOUNTED';

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
  /** 最近一次加载的性能指标（为监控提供数据） */
  loadMetrics: null | { duration: number; fromCache: boolean };
  error: null | string;
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
    loadMetrics: null,
    error: null,
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
 */
export async function activateApp(
  instance: AppInstance,
  container: HTMLElement,
  loadOpts: LoadOptions = {},
): Promise<void> {
  const { config } = instance;

  if (instance.status === 'MOUNTED') return;

  // keepAlive 复用
  if (instance.keepAlive && instance.cachedRoot && instance.cachedParent) {
    container.appendChild(instance.cachedRoot);
    instance.cachedParent = null;
    instance.status = 'MOUNTED';
    console.info(`[LiteKernel] ${config.name} reattached (keepAlive)`);
    return;
  }

  // 加载（如未加载）
  if (!instance.exports) {
    instance.status = 'LOADING';
    try {
      const result: LoadResult = await loadApp(config, loadOpts);
      instance.exports = result.exports;
      instance.loadMetrics = { duration: result.duration, fromCache: result.fromCache };
      instance.status = 'LOADED';
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

  // 进入快照沙箱（挂载前快照 window，代理副作用 API）
  instance.sandbox = enterSandbox();

  try {
    await instance.exports.mount(mountProps);
    instance.status = 'MOUNTED';
    instance.error = null;
    console.info(`[LiteKernel] ${config.name} mounted`);
  } catch (err) {
    // 挂载失败：退出沙箱，回滚全局状态
    exitSandbox(instance.sandbox);
    instance.sandbox = null;
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
    const container = document.querySelector(config.container);
    if (container) {
      instance.cachedRoot = container.firstElementChild as HTMLElement;
      instance.cachedParent = container;
      if (instance.cachedRoot) {
        container.removeChild(instance.cachedRoot);
      }
      instance.status = 'UNMOUNTED';
      console.info(`[LiteKernel] ${config.name} detached (keepAlive)`);
      return { name: config.name, success: true };
    }
  }

  // 完整卸载
  try {
    await instance.exports!.unmount({
      container: document.querySelector(config.container) || document.createElement('div'),
      basename: config.activeRule,
    });

    // 退出快照沙箱（恢复 window / 清理事件与定时器）
    if (instance.sandbox) {
      exitSandbox(instance.sandbox);
      instance.sandbox = null;
    }

    removeStylesheets(config.name);
    instance.exports = null;
    instance.status = 'NOT_LOADED';
    instance.error = null;
    console.info(`[LiteKernel] ${config.name} unmounted`);
    return { name: config.name, success: true };
  } catch (err) {
    // unmount 失败仍尝试退出沙箱
    if (instance.sandbox) {
      exitSandbox(instance.sandbox);
      instance.sandbox = null;
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
