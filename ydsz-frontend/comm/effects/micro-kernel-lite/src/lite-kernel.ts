/**
 * 自研轻内核 — 实现 MicroRuntime 接口
 *
 * ESM 原生微前端运行时：适合同一团队、统一构建链的同源子应用集群。
 * 不做 JS 沙箱、不做 HTML entry 解析，仅覆盖真正需要的：
 *   ESM loader → 生命周期 → scoped 样式 → keep-alive → 错误降级。
 *
 * 使用方式：
 *   registerKernel('lite', () => createLiteKernel());
 *   createRuntime({ kernel: 'lite' });
 *
 * @path comm/effects/micro-kernel-lite/src/lite-kernel.ts
 * @author ydsz-team
 * @since 3.0.0
 */

import type {
  LifecycleHook,
  MicroAppConfig,
  MicroRuntime,
  StartOptions,
  UnmountResult,
} from '@ydsz/micro-runtime';

import { clearDegraded, isDegraded, markDegraded, renderErrorFallback } from './error-boundary';
import {
  type AppInstance,
  activateApp,
  createAppInstance,
  deactivateApp,
  getAllInstances,
  getAppInstance,
  setKeepAlive,
} from './scheduler';

/** 当前活跃应用名 */
let activeAppName: null | string = null;

const lifecycleHooks = new Map<string, LifecycleHook[]>();

function addLifecycleHook(
  hookName: 'beforeLoad' | 'afterMount' | 'afterUnmount',
  hook: LifecycleHook,
): void {
  if (!lifecycleHooks.has(hookName)) {
    lifecycleHooks.set(hookName, []);
  }
  lifecycleHooks.get(hookName)!.push(hook);
}

async function runHooks(hookName: string, app: MicroAppConfig): Promise<void> {
  for (const hook of lifecycleHooks.get(hookName) || []) {
    await hook(app);
  }
}

/**
 * 路由监听：匹配 activeRule → 激活对应子应用
 */
function startRouterSync(
  apps: MicroAppConfig[],
  options?: StartOptions,
): () => void {
  function handleRouteChange(): void {
    const path = window.location.pathname;

    for (const app of apps) {
      if (path.startsWith(app.activeRule)) {
        if (isDegraded(app.name)) {
          // 降级应用走整页跳转
          if (activeAppName !== app.name) {
            window.location.href = app.activeRule;
          }
          return;
        }

        void switchToApp(app, options);
        return;
      }
    }
  }

  // 首次匹配
  handleRouteChange();

  // 监听 popstate（浏览器前进/后退）
  window.addEventListener('popstate', handleRouteChange);

  return () => {
    window.removeEventListener('popstate', handleRouteChange);
  };
}

async function switchToApp(config: MicroAppConfig, options?: StartOptions): Promise<void> {
  if (activeAppName === config.name) return;

  // 卸载当前
  if (activeAppName) {
    const prev = getAppInstance(activeAppName);
    if (prev) {
      await deactivateApp(prev);
    }
  }

  // 激活目标
  const instance = getAppInstance(config.name) || createAppInstance(config);

  await runHooks('beforeLoad', config);

  const container = document.querySelector(config.container);
  if (!container) {
    console.error(`[LiteKernel] Container "${config.container}" not found for ${config.name}`);
    return;
  }

  // 保活策略：M3 阶段与 tabbar store 打通 setKeepAlive
  // 此处仅做基础框架支持

  try {
    await activateApp(instance, container as HTMLElement);
    activeAppName = config.name;
    await runHooks('afterMount', config);
  } catch (err) {
    console.error(`[LiteKernel] Failed to activate ${config.name}:`, err);
    markDegraded(config.name);
    renderErrorFallback(config, config.container);
    activeAppName = null;
  }
}

/**
 * 创建轻内核运行时实例。
 */
export function createLiteKernel(): MicroRuntime {
  let apps: MicroAppConfig[] = [];
  let started = false;
  let routerSyncCleanup: (() => void) | null = null;

  return {
    registerApps(newApps) {
      apps = [...new Set([...apps, ...newApps])];
      for (const app of newApps) {
        if (!getAppInstance(app.name)) {
          createAppInstance(app);
        }
      }
    },

    getRegisteredApps() {
      return [...getAllInstances().map((i) => i.config)];
    },

    start(options) {
      if (started) {
        console.warn('[LiteKernel] Already started');
        return;
      }
      started = true;

      // 启动路由监听
      routerSyncCleanup = startRouterSync(apps, options);

      // 预加载策略
      if (typeof options?.prefetch === 'function') {
        const toPrefetch = apps.filter(options.prefetch);
        requestIdleCallback(() => {
          for (const app of toPrefetch) {
            void switchToApp(app).catch(() => {
              // 预加载失败不阻塞，静默跳过
            }).then(() => {
              // 预加载后立即切回（不保留 mount）
            });
          }
        });
      }

      console.info(`[LiteKernel] Started with ${apps.length} apps`);
    },

    async unmountApp(name) {
      const instance = getAppInstance(name);
      if (!instance) {
        return { name, success: false, reason: 'App not registered' };
      }

      await runHooks('afterUnmount', instance.config);

      const result = await deactivateApp(instance);
      if (activeAppName === name) activeAppName = null;
      return result;
    },

    setKeepAlive(name, keep) {
      setKeepAlive(name, keep);
    },

    navigateTo(path) {
      window.history.pushState(null, '', path);
      window.dispatchEvent(new PopStateEvent('popstate'));
    },

    addLifecycleHook,

    getActiveAppName() {
      return activeAppName;
    },
  };
}
