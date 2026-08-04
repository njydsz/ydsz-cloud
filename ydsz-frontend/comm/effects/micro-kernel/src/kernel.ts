/**
 * 自研轻内核 — 实现 MicroRuntime 接口
 *
 * ESM 原生微前端运行时：适合同一团队、统一构建链的同源子应用集群。
 * 能力覆盖：
 *   ESM loader → 生命周期 → 快照沙箱 → keep-alive → 错误降级 → 路由同步 → 全局通信。
 *
 * P0-A2 修复：全部可变状态（_globalState / lifecycleHooks / activeAppName /
 * switchToken）收进 createKernel 闭包，避免多实例 / HMR 场景下状态串扰。
 *
 * 使用方式：
 *   registerKernel('micro-kernel', () => createKernel());
 *   createRuntime({ kernel: 'micro-kernel' });
 *
 * @path comm/effects/micro-kernel/src/kernel.ts
 * @author ydsz-team
 * @since 3.0.0
 */

import type {
  ErrorLifecycleHook,
  LifecycleHook,
  LifecycleHookName,
  MicroAppConfig,
  MicroRuntime,
  RawGlobalStateAPI,
  StartOptions,
} from '@ydsz/micro-runtime';

import { clearDegraded, isDegraded, markDegraded, renderErrorFallback, resetRetryCount } from './error-boundary';
import {
  activateApp,
  createAppInstance,
  deactivateApp,
  getAllInstances,
  getAppInstance,
  resetScheduler,
  setKeepAlive,
  setupVisibilityAutoRelease,
} from './scheduler';
import type { GlobalStateBridge } from './scheduler';
import { clearManifestCache, loadApp } from './loader';
import { getVersionManager } from './version-manager';
import { getPreloadManager } from './preload-strategy';
import { preloadManifest } from './link-hints';
import { createLogger } from '@ydsz-core/shared/utils';

/** 模块级日志器 */
const logger = createLogger('MicroKernel');

/** 内核自定义路由变更事件名，供 history patch + popstate 统一触发 */
const ROUTE_CHANGE_EVENT = 'micro-kernel:route-change';

/**
 * 解析容器配置为 HTMLElement。
 *
 * 支持两种模式：
 * - string: CSS 选择器（如 '#subapp-container'）
 * - HTMLElement: 直接传入 DOM 元素（适用于动态创建容器或嵌套子应用场景）
 *
 * @param container - 容器配置，支持 CSS 选择器字符串或 HTMLElement
 * @returns 解析后的 HTMLElement，未找到时返回 null
 */
function resolveContainer(container: string | HTMLElement): HTMLElement | null {
  if (typeof container === 'string') {
    return document.querySelector(container) as HTMLElement | null;
  }
  return container;
}

/**
 * 对 history.pushState / replaceState 打补丁，使其派发自定义路由变更事件。
 * 这样主应用 Vue Router 的 router.push 等操作也能被 micro-kernel 感知，
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
 * P0-2: 匹配 activeRule 规则
 *
 * 支持三种匹配模式：
 * - string: 路由前缀匹配（向后兼容）
 * - RegExp: 正则表达式匹配 pathname
 * - function: 自定义匹配函数，接收完整 pathname 参数
 */
function matchActiveRule(path: string, activeRule: string | RegExp | ((path: string) => boolean)): boolean {
  if (typeof activeRule === 'string') {
    return path.startsWith(activeRule);
  }
  if (activeRule instanceof RegExp) {
    return activeRule.test(path);
  }
  if (typeof activeRule === 'function') {
    return activeRule(path);
  }
  return false;
}

/**
 * requestIdleCallback 的安全包装。
 *
 * `requestIdleCallback` 在部分环境不可用：
 *   - Safari < 16.4、Firefox < 116、Node/happy-dom 测试环境。
 *
 * 不可用时回退到 `setTimeout(cb, 0)`，保证预加载逻辑在这些环境下仍能执行
 * （仅放弃"空闲时段"调度语义，不影响功能正确性）。
 */
type IdleCallback = () => void;
function scheduleIdle(cb: IdleCallback): void {
  const ric = (globalThis as { requestIdleCallback?: (cb: IdleCallback) => void }).requestIdleCallback;
  if (typeof ric === 'function') {
    ric(cb);
  } else {
    setTimeout(cb, 0);
  }
}

/**
 * P2: 网络条件感知 — 判断是否应跳过预加载。
 *
 * 依据 Network Information API（navigator.connection）：
 *   - effectiveType 为 slow-2g / 2g / 3g 视为慢速网络
 *   - saveData 为 true 表示用户开启省流量模式
 *
 * 任一命中即跳过自动预加载，避免在弱网下抢占主请求带宽。
 * 浏览器不支持 Network Information API 时返回 false（保持默认预加载行为）。
 *
 * 注意：仅用于自动预加载决策；用户主动触发的 prefetchApp（hover 预热）
 * 不调用本函数，因为主动行为意味着用户即将访问，值得拉取。
 */
function shouldSkipPrefetchDueToNetwork(): boolean {
  const nav = navigator as Navigator & {
    connection?: {
      effectiveType?: string;
      saveData?: boolean;
    };
  };
  const conn = nav.connection;
  if (!conn) return false;

  if (conn.saveData === true) return true;

  const effectiveType = conn.effectiveType;
  if (
    effectiveType === 'slow-2g' ||
    effectiveType === '2g' ||
    effectiveType === '3g'
  ) {
    return true;
  }

  return false;
}

/**
 * 创建轻内核运行时实例。
 *
 * P0-A2: 全部可变状态收进此闭包，确保多次调用 `createKernel()` 时
 * 各实例拥有独立状态，互不串扰（HMR / 测试场景关键）。
 *
 * 返回的实例含内部 `_stop` 方法（非 MicroRuntime 接口暴露）：
 * - 清理路由监听与 history 补丁
 * - 卸载全部子应用
 * - 清空降级标记
 * - 重置 scheduler / loader 模块级状态
 * 用于基座 HMR / 测试环境正常重启。
 */
export function createKernel(): MicroRuntime & { _stop: () => Promise<void> } {
  let apps: MicroAppConfig[] = [];
  let started = false;
  let routerSyncCleanup: (() => void) | null = null;
  let visibilityCleanup: (() => void) | null = null;
  const versionManager = getVersionManager();
  const preloadManager = getPreloadManager();

  // ==================== P0-A2: 闭包级状态 ====================

  /** 当前活跃应用名 */
  let activeAppName: null | string = null;

  /** 切换令牌：递增代次，防止快速连续切换时异步竞态 */
  let switchToken = 0;

  // ==================== 全局通信 (globalState) ====================

  /** 全局状态存储 */
  let _globalState: Record<string, unknown> = {};
  const _globalStateListeners = new Set<(state: Record<string, unknown>, prev: Record<string, unknown>) => void>();

  /**
   * micro-kernel 内置的 RawGlobalStateAPI 实现。
   * 不依赖 qiankun initGlobalState，纯内存 pub-sub。
   * 注入子应用 mountProps 后，子应用可通过 {@link createGlobalStateHandle} 消费。
   */
  const globalStateAPI: RawGlobalStateAPI = {
    onGlobalStateChange(listener, fireImmediately) {
      _globalStateListeners.add(listener);
      if (fireImmediately) {
        try { listener({ ..._globalState }, {}); } catch { /* 静默 */ }
      }
      // 返回取消订阅函数，防止内存泄漏
      return () => {
        _globalStateListeners.delete(listener);
      };
    },
    setGlobalState(patch) {
      const prev = { ..._globalState };
      Object.assign(_globalState, patch);
      const snapshot = { ..._globalState };
      for (const listener of _globalStateListeners) {
        try { listener(snapshot, prev); } catch { /* 静默 */ }
      }
    },
    getGlobalState() {
      return { ..._globalState };
    },
  };

  // ==================== 生命周期钩子 ====================

  const lifecycleHooks = new Map<string, Array<LifecycleHook | ErrorLifecycleHook>>();

  function addLifecycleHook(
    hookName: LifecycleHookName,
    hook: ErrorLifecycleHook | LifecycleHook,
  ): () => void {
    if (!lifecycleHooks.has(hookName)) {
      lifecycleHooks.set(hookName, []);
    }
    lifecycleHooks.get(hookName)!.push(hook);

    return () => {
      const list = lifecycleHooks.get(hookName);
      if (!list) return;
      const idx = list.indexOf(hook);
      if (idx >= 0) list.splice(idx, 1);
    };
  }

  async function runHooks(hookName: LifecycleHookName, app: MicroAppConfig): Promise<void> {
    for (const hook of lifecycleHooks.get(hookName) || []) {
      await (hook as LifecycleHook)(app);
    }
  }

  async function runErrorHooks(app: MicroAppConfig, error: unknown): Promise<void> {
    for (const hook of lifecycleHooks.get('error') || []) {
      try {
        await (hook as ErrorLifecycleHook)(app, error);
      } catch {
        /* 错误钩子内部的错误不应影响后续钩子 */
      }
    }
  }

  // ==================== 路由同步 + 应用切换 ====================

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

    // 派发 before-load 事件，触发骨架屏显示
    window.dispatchEvent(new CustomEvent('micro-kernel:before-load', { detail: { appName: config.name } }));

    await runHooks('beforeLoad', config);
    if (token !== switchToken) return;

    const container = resolveContainer(config.container);
    if (!container) {
      logger.error(`Container "${config.container}" not found for ${config.name}`);
      window.dispatchEvent(new CustomEvent('micro-kernel:error', { detail: { appName: config.name, error: 'Container not found' } }));
      return;
    }

    try {
      // v3.6.0: 构造 globalStateBridge，用于 iframe 沙箱跨 realm 通信
      // snapshot/proxy 沙箱不使用此桥接（直接注入 globalStateAPI 引用）
      const globalStateBridge: GlobalStateBridge = {
        getGlobalState: () => globalStateAPI.getGlobalState(),
        setGlobalState: (patch) => globalStateAPI.setGlobalState(patch),
        onGlobalStateChange: (listener, fireImmediately) => globalStateAPI.onGlobalStateChange(listener, fireImmediately),
      };

      await activateApp(instance, container as HTMLElement, {}, {
        // v3.3: 细化生命周期 — 加载完成后触发 afterLoad 钩子与事件
        onLoaded: (inst) => {
          if (token !== switchToken) return;
          void runHooks('afterLoad', inst.config);
          window.dispatchEvent(
            new CustomEvent('micro-kernel:after-load', { detail: { appName: inst.config.name } }),
          );
        },
        // v3.3: 细化生命周期 — mount 之前触发 beforeMount 钩子与事件
        onBeforeMount: (inst) => {
          if (token !== switchToken) return;
          void runHooks('beforeMount', inst.config);
          window.dispatchEvent(
            new CustomEvent('micro-kernel:before-mount', { detail: { appName: inst.config.name } }),
          );
        },
      }, globalStateBridge);
      if (token !== switchToken) return;
      activeAppName = config.name;
      // v3.4: 记录应用访问频率，供 frequency 预加载策略使用
      preloadManager.recordAppVisit(config.name);
      await runHooks('afterMount', config);

      // 派发 after-mount 事件，触发骨架屏隐藏
      window.dispatchEvent(new CustomEvent('micro-kernel:after-mount', { detail: { appName: config.name } }));
    } catch (err) {
      logger.error(`Failed to activate ${config.name}:`, err);
      markDegraded(config.name);
      // v3.1: onRetry 回调使「重试」按钮重新激活子应用而非整页刷新
      renderErrorFallback(config, resolveContainer(config.container), () => switchToApp(config, options));

      // 派发 error 事件，触发骨架屏隐藏
      window.dispatchEvent(new CustomEvent('micro-kernel:error', { detail: { appName: config.name, error: String(err) } }));

      // 触发 error 生命周期钩子（供 SubAppContainer 等订阅方使用）
      await runErrorHooks(config, err);

      if (activeAppName === config.name) {
        activeAppName = null;
      }
    }
  }

  /**
   * 路由监听：匹配 activeRule → 激活对应子应用。
   * 覆盖 popstate（浏览器前进/后退）+ 自定义 route-change（history pushState/replaceState 补丁）。
   */
  function startRouterSync(
    routerApps: MicroAppConfig[],
    options?: StartOptions,
  ): () => void {
    const historyPatchCleanup = patchHistory();

    function handleRouteChange(): void {
      const path = window.location.pathname;

      for (const app of routerApps) {
        if (matchActiveRule(path, app.activeRule)) {
          if (isDegraded(app.name)) {
            // 降级应用走整页跳转
            if (activeAppName !== app.name) {
              // P0-2: 降级跳转需要有效的 URL，字符串类型直接使用，其他类型使用 entry
              const fallbackUrl = typeof app.activeRule === 'string' ? app.activeRule : app.entry;
              window.location.href = fallbackUrl;
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
          logger.debug(`Deactivated "${current.config.name}" (no activeRule match)`);
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

  return {
    registerApps(newApps) {
      apps = [...new Set([...apps, ...newApps])];
      for (const app of newApps) {
        if (!getAppInstance(app.name)) {
          createAppInstance(app);
        }
      }
      // P0-F1: 将子应用入口注入版本管理器，供自动检查 fetch manifest
      versionManager.setAppEntries(
        new Map(apps.map((a) => [a.name, a.entry])),
      );
      // P0-P1: 预加载各子应用 manifest.json，消除首次 loadApp 时的串行等待
      // build 模式下注入 <link rel="preload" as="fetch">，浏览器空闲时提前拉取
      const isBuildMode = !(
        typeof import.meta !== 'undefined' &&
        (import.meta as { env?: Record<string, unknown> }).env?.DEV === true
      );
      if (isBuildMode) {
        for (const app of newApps) {
          preloadManifest(app.entry);
        }
      }
    },

    getRegisteredApps() {
      return [...getAllInstances().map((i) => i.config)];
    },

    start(options) {
      if (started) {
        logger.warn('Already started');
        return;
      }
      started = true;

      // P1-3.2: 设置权限检查器，预加载时会根据用户权限过滤
      if (options?.permissionChecker) {
        preloadManager.setPermissionChecker(options.permissionChecker);
      }

      // 启动路由监听（含 history 补丁）
      routerSyncCleanup = startRouterSync(apps, options);

      // === S1 修复：预加载只拉取 ESM 模块与样式，不执行 mount ===
      // loadApp 完成的资源会进入浏览器 HTTP / ESM 缓存，
      // 二次激活时仅差 mount 耗时，且不会篡改 activeAppName
      if (typeof options?.prefetch === 'function') {
        const toPrefetch = apps.filter(options.prefetch);
        // P2: 网络条件感知 — 慢速网络（2g/3g）或省流量模式下跳过预加载
        if (shouldSkipPrefetchDueToNetwork()) {
          logger.debug('Prefetch skipped due to slow network or saveData');
        } else {
          scheduleIdle(() => {
            // 二次校验：可能在 idle 等待期间网络已变差
            if (shouldSkipPrefetchDueToNetwork()) return;
            for (const app of toPrefetch) {
              void loadApp(app).catch(() => {
                // 预加载失败不阻塞，静默跳过
              });
            }
          });
        }
      }

      // === P2-10: 初始化预加载策略 ===
      // 为每个应用注册默认的 idle 预加载策略
      for (const app of apps) {
        preloadManager.registerStrategy(app.name, {
          strategy: 'idle',
          idleTimeout: 2000,
          onPreload: (appName: string) => {
            const config = apps.find((a) => a.name === appName);
            if (config) {
              void loadApp(config).catch(() => {
                // 预加载失败不阻塞
              });
            }
          },
        });
      }

      logger.info(`Started with ${apps.length} apps`);
      window.dispatchEvent(new CustomEvent('micro-kernel:started'));
    },

    async prefetchApp(name) {
      // 手动预加载：用于 hover 预热等场景。
      // 不检查网络条件（用户主动悬停意味着即将访问，值得拉取）。
      const config = apps.find((a) => a.name === name);
      if (!config) {
        logger.warn(`prefetchApp: app "${name}" not registered`);
        return;
      }
      try {
        const result = await loadApp(config);
        // P2-10: 预加载成功后检查版本更新
        if (result.manifest) {
          void versionManager.checkUpdate(name, result.manifest);
        }
      } catch {
        // 静默 — 预加载失败不影响后续正常激活
      }
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
      // v3.1: 清理重试计数器（需在 resetScheduler 清空 appInstances 之前）
      for (const inst of getAllInstances()) {
        resetRetryCount(inst.config.name);
      }

      // P0-A2: 重置闭包级状态
      activeAppName = null;
      switchToken = 0;
      _globalState = {};
      _globalStateListeners.clear();
      lifecycleHooks.clear();
      apps = [];
      started = false;

      // P0-A2: 重置 scheduler / loader 模块级状态，避免上一轮实例残留
      resetScheduler();
      clearManifestCache();

      clearDegraded();
      logger.info('Stopped');
    },
  };
}
