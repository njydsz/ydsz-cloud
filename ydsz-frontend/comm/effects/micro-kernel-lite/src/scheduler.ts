/**
 * 生命周期调度器 + 保活控制
 *
 * 每个子应用一个 AppInstance 实例：
 * - 加载 → parsed LifecycleExports + metadata
 * - activate → mount
 * - deactivate → 视 keepAlive 决定是否卸载
 * - keepAlive 激活 → container.appendChild(cachedEl) 直接复用，零重新渲染
 *
 * @path comm/effects/micro-kernel-lite/src/scheduler.ts
 * @author ydsz-team
 * @since 3.0.0
 */

import type { LifecycleExports, MicroAppConfig, MountProps, UnmountResult } from '@ydsz/micro-runtime';
import { loadApp, removeStylesheets } from './loader';

export type AppStatus = 'NOT_LOADED' | 'LOADING' | 'LOADED' | 'MOUNTED' | 'UNMOUNTED';

export interface AppInstance {
  config: MicroAppConfig;
  status: AppStatus;
  exports: null | LifecycleExports;
  keepAlive: boolean;
  /** keepAlive 时保存的 DOM 根节点 */
  cachedRoot: null | HTMLElement;
  /** keepAlive 时原始父节点（切回时 appendChild 回此处） */
  cachedParent: null | Node;
  error: null | string;
}

const appInstances = new Map<string, AppInstance>();

export function createAppInstance(config: MicroAppConfig): AppInstance {
  const instance: AppInstance = {
    config,
    status: 'NOT_LOADED',
    exports: null,
    keepAlive: false,
    cachedRoot: null,
    cachedParent: null,
    error: null,
  };
  appInstances.set(config.name, instance);
  return instance;
}

export function getAppInstance(name: string): AppInstance | undefined {
  return appInstances.get(name);
}

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
  signal?: AbortSignal,
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
      const { exports } = await loadApp(config, signal);
      instance.exports = exports;
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

  try {
    await instance.exports.mount(mountProps);
    instance.status = 'MOUNTED';
    instance.error = null;
    console.info(`[LiteKernel] ${config.name} mounted`);
  } catch (err) {
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
    removeStylesheets(config.name);
    instance.exports = null;
    instance.status = 'NOT_LOADED';
    instance.error = null;
    console.info(`[LiteKernel] ${config.name} unmounted`);
    return { name: config.name, success: true };
  } catch (err) {
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
