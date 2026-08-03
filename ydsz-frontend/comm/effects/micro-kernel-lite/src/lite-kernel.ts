/**
 * 自研轻内核 — 实现 MicroRuntime 接口
 *
 * ESM 原生微前端运行时：适合同一团队、统一构建链的同源子应用集群。
 * 能力覆盖：
 *   ESM loader → 生命周期 → 快照沙箱 → keep-alive → 错误降级 → 路由同步 → 全局通信。
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
  RawGlobalStateAPI,
  StartOptions,
} from '@ydsz/micro-runtime';

import { clearDegraded, isDegraded, markDegraded, renderErrorFallback } from './error-boundary';
import {
  activateApp,
  createAppInstance,
  deactivateApp,
  getAllInstances,
  getAppInstance,
  setKeepAlive,
} from './scheduler';
import { loadApp } from './loader';

/** 内核自定义路由变更事件名，供 history patch + popstate 统一触发 */
const ROUTE_CHANGE_EVENT = 'lite-kernel:route-change';

/** 当前活跃应用名 */
let activeAppName: null | string = null;

/** 切换令牌：递增代次，防止快速连续切换时异步竞态 */
let switchToken = 0;

// ==================== 全局通信 (globalState) ====================

/** 全局状态存储 */
let _globalState: Record<string, unknown> = {};
const _globalStateListeners = new Set<(state: Record<string, unknown>) => void>();

/**
 * lite-kernel 内置的 RawGlobalStateAPI 实现。
 * 不依赖 qiankun initGlobalState，纯内存 pub-sub。
 * 注入子应用 mountProps 后，子应用可通过 {@link createGlobalStateHandle} 消费。
 */
const globalStateAPI: RawGlobalStateAPI = {
  onGlobalStateChange(listener, fireImmediately) {
    _globalStateListeners.add(listener);
    if (fireImmediately) {
      try { listener({ ..._globalState }); } catch { /* 静默 */ }
    }
    // 返回取消订阅函数（由 createGlobalStateHandle 内维护其 own listener set）
  },
  setGlobalState(patch) {
    Object.assign(_globalState, patch);
    const snapshot = { ..._globalState };
    for (const listener of _globalStateListeners) {
      try { listener(snapshot); } catch { /* 静默 */ }
    }
  },
  getGlobalState() {
    return { ..._globalState };
  },
};

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
 * 对 history.pushState / replaceState 打补丁，使其派发自定义路由变更事件。
 * 这样主应用 Vue Router 的 router.push 等操作也能被 lite-kernel 感知，
 * 而不只依赖浏览器 popstate（后者只在前进/后退时触发）。
 *
 * 参照 qiankun、micro-app、Garfish 的通用实践。
 */
function patchHistory(): () => void {
  const originalPushState = history.pushState;
  const originalReplaceState = history.replaceState;

  function dispatchRouteChange(): void {
    window.dispatchEvent(new CustomEvent(ROUTE_CHANGE_EVENT));
  }

  history.pushState = function (...args: Parameters<typeof originalPushState>): void {
    originalPushState.apply(this, args);
    dispatchRouteChange();
  };

  history.replaceState = function (...args: Parameters<typeof originalReplaceState>): void {
    originalReplaceState.apply(this, args);
    dispatchRouteChange();
  };

  return () => {
    history.pushState = originalPushState;
    history.replaceState = originalReplaceState;
  };
}

/**
 * 路由监听：匹配 activeRule → 激活对应子应用。
 * 覆盖 popstate（浏览器前进/后退）+ 自定义 route-change（history pushState/replaceState 补丁）。
 */
function startRouterSync(
  apps: MicroAppConfig[],
  options?: StartOptions,
): () => void {
  const historyPatchCleanup = patchHistory();

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

    // === S2 修复：路径不匹配任何子应用时，卸载当前活跃应用 ===
    if (activeAppName) {
      const current = getAppInstance(activeAppName);
      if (current) {
        void deactivateApp(current);
        activeAppName = null;
        console.info(`[LiteKernel] Deactivated "${current.config.name}" (no activeRule match)`);
      }
    }
  }

  // 首次匹配
  handleRouteChange();

  // 浏览器的前进/后退
  window.addEventListener('popstate', handleRouteChange);
  // history pushState/replaceState 补丁派发的事件
  window.addEventListener(ROUTE_CHANGE_EVENT, handleRouteChange);

  return () => {
    historyPatchCleanup();
    window.removeEventListener('popstate', handleRouteChange);
    window.removeEventListener(ROUTE_CHANGE_EVENT, handleRouteChange);
  };
}

/**
 * === S3 修复：带令牌的并发安全切换 ===
 *
 * 每次拨动 switchToken，异步操作前后校验令牌是否一致。
 * 若不一致说明已有更晚的切换请求发起，当前操作结果直接丢弃，
 * 避免"后到的 deactivateApp 把刚激活的应用卸载"这类竞态。
 */
async function switchToApp(config: MicroAppConfig, options?: StartOptions): Promise<void> {
  const token = ++switchToken;

  if (activeAppName === config.name) return;

  // 卸载当前
  if (activeAppName) {
    const prev = getAppInstance(activeAppName);
    if (prev) {
      await deactivateApp(prev);
      if (token !== switchToken) return;
    }
  }

  // 激活目标
  const instance = getAppInstance(config.name) || createAppInstance(config);

  // === globalState 注入：子应用 mountProps 中注入跨应用通信 API ===
  config.props = {
    ...config.props,
    _globalState: globalStateAPI,
  };

  await runHooks('beforeLoad', config);
  if (token !== switchToken) return;

  const container = document.querySelector(config.container);
  if (!container) {
    console.error(`[LiteKernel] Container "${config.container}" not found for ${config.name}`);
    return;
  }

  try {
    await activateApp(instance, container as HTMLElement);
    if (token !== switchToken) return;
    activeAppName = config.name;
    await runHooks('afterMount', config);
  } catch (err) {
    console.error(`[LiteKernel] Failed to activate ${config.name}:`, err);
    markDegraded(config.name);
    renderErrorFallback(config, config.container);
    if (activeAppName === config.name) {
      activeAppName = null;
    }
  }
}

/**
 * 创建轻内核运行时实例。
 *
 * 返回的实例含内部 `_stop` 方法（非 MicroRuntime 接口暴露）：
 * - 清理路由监听与 history 补丁
 * - 卸载全部子应用
 * - 清空降级标记
 * 用于基座 HMR / 测试环境正常重启。
 */
export function createLiteKernel(): MicroRuntime & { _stop: () => Promise<void> } {
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

      // 启动路由监听（含 history 补丁）
      routerSyncCleanup = startRouterSync(apps, options);

      // === S1 修复：预加载只拉取 ESM 模块与样式，不执行 mount ===
      // loadApp 完成的资源会进入浏览器 HTTP / ESM 缓存，
      // 二次激活时仅差 mount 耗时，且不会篡改 activeAppName
      if (typeof options?.prefetch === 'function') {
        const toPrefetch = apps.filter(options.prefetch);
        requestIdleCallback(() => {
          for (const app of toPrefetch) {
            void loadApp(app).catch(() => {
              // 预加载失败不阻塞，静默跳过
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
      // pushState 补丁会自动派发 ROUTE_CHANGE_EVENT，不再需要手动 dispatch popstate
    },

    addLifecycleHook,

    getActiveAppName() {
      return activeAppName;
    },

    /** 内部停止方法：清理所有注册，用于 HMR / 测试环境重启 */
    async _stop() {
      routerSyncCleanup?.();
      routerSyncCleanup = null;

      for (const instance of getAllInstances()) {
        if (instance.status === 'MOUNTED') {
          await deactivateApp(instance);
        }
      }
      activeAppName = null;
      switchToken = 0;
      clearDegraded();
      started = false;
      console.info('[LiteKernel] Stopped');
    },
  };
}
