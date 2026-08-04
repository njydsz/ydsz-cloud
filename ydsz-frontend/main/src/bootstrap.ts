/**
 * 应用引导程序，初始化全局插件和配置
 *
 * v3.0: 基于 @ydsz/micro-kernel 自研 ESM 原生微前端运行时，
 *       通过 @ydsz/micro-runtime 接口层完成内核注册与子应用生命周期管理。
 *
 * @path main/src/bootstrap.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import { createApp, watchEffect } from 'vue';

import { registerAccessDirective } from '@ydsz/access';
import { registerLoadingDirective } from '@ydsz/common-ui/es/loading';
import { registerSafeHtmlDirective } from '@ydsz/common-ui/es/safe-html';
import { registerWatermarkDirective } from '@ydsz/common-ui/es/watermark';
import { initLogger } from '@ydsz-core/shared/utils';
import { preferences } from '@ydsz/preferences';
import { initStores, useUserStore } from '@ydsz/stores';
import { startProgress, stopProgress } from '@ydsz/utils';
import '@ydsz/styles';
import '@ydsz/styles/ele';

import { ElLoading } from 'element-plus';
import { useTitle } from '@vueuse/core';

import { $t, setupI18n } from '#/locales';

import { setupMonitor } from '@ydsz/monitor';

import { initComponentAdapter } from './adapter/component';
import { initSetupYDSZForm } from './adapter/form';
import App from './app.vue';
import {
  featureFlagsOptions,
  registerApplicationFlags,
} from './feature-flags';
import { useCrossTabSync } from './hooks/use-cross-tab-sync';
import {
  getSubAppSessionSnapshot,
  getSubAppLastActivePath,
  recordSubAppTabOpened,
} from './hooks/use-tabbar-micro-sync';
import { useSessionExpiryWarning } from './hooks/use-session-expiry-warning';
import { router, initRouterGuard } from './router';

import {
  createKernel,
  createRoutePreloadStrategy,
  getErrorFallbackMessagesByLocale,
  getPreloadManager,
  getVersionManager,
  setErrorFallbackMessages,
} from '@ydsz/micro-kernel';
import { createRuntime, registerKernel, type MicroAppEntry } from '@ydsz/micro-runtime';
import { createLogger } from '@ydsz-core/shared/utils';
import { MICRO_APPS, PATH_TO_APP_MAP, getProdEntry } from '@ydsz/vite-config';
import { resolveRegistry, resolveAppEntry } from '@ydsz/micro-kernel';
import { enableMicroDevTools } from '@ydsz/micro-kernel';
import { enableDevToolsBridge } from './monitoring/devtools-bridge';
import { getCanaryManager } from '@ydsz/micro-kernel';

/** 单个 micro-runtime 实例（整个主应用生命周期唯一，供其他模块获取） */
export let microRuntime: ReturnType<typeof createRuntime> | null = null;

/** bootstrap 内部统一日志器（自动带 [ydsz][Bootstrap] 前缀） */
const logger = createLogger('Bootstrap');
/** 微运行时日志器 */
const runtimeLogger = createLogger('MicroRuntime');
/** 版本管理器日志器 */
const versionLogger = createLogger('VersionManager');

/** 注册表模式：默认 'auto'（优先远程，回退静态），可通过 VITE_MICRO_APPS_REGISTRY_MODE 覆盖 */
function getRegistryMode(): 'static' | 'remote' | 'auto' {
  const mode = import.meta.env.VITE_MICRO_APPS_REGISTRY_MODE;
  if (mode === 'static' || mode === 'remote' || mode === 'auto') return mode;
  // 若未配 VITE_MICRO_APPS_REGISTRY_MODE 但配了 VITE_MICRO_APPS_REGISTRY endpoint，自动启用 remote
  if (import.meta.env.VITE_MICRO_APPS_REGISTRY) return 'remote';
  return 'auto';
}

/**
 * 启动微前端运行时。
 *
 * v3.7.0: 支持远程注册表（registry='auto'/'remote' 时异步拉取注册表）；
 *         registry='static' 保持同步注册以兼容既有流程。
 * v3.3:
 *   - 同步 error-boundary 降级 UI 文案至当前偏好语言，运行时随语言切换更新
 *   - nprogress 联动 micro-kernel 生命周期：beforeLoad 启动、afterMount/error 停止，
 *     使子应用几秒级 ESM 加载 + mount 耗时获得连续的顶部进度条反馈
 */
function registerMicroRuntime() {
  // 1. 注册 micro-kernel 内核
  registerKernel('micro-kernel', () => createKernel());

  // 2. 创建运行时实例
  microRuntime = createRuntime({ kernel: 'micro-kernel' });
  runtimeLogger.info('Initialized with kernel: micro-kernel');

  // 3. 初始化版本管理器
  getVersionManager({
    checkInterval: 5 * 60 * 1000, // 5分钟检查一次
    autoCheck: true,
    onVersionCheck: (result) => {
      if (result.hasUpdate) {
        versionLogger.info(
          `App ${result.appName} updated: ${result.currentVersion} -> ${result.latestVersion}`,
        );
        // 可以在这里触发更新提示或自动刷新逻辑
      }
    },
  });
  versionLogger.info('Initialized');

  // v3.3: 同步 error-boundary 降级 UI 文案至当前偏好语言
  // watchEffect 保证后续语言切换时文案自动更新
  watchEffect(() => {
    const locale = preferences.app.locale;
    setErrorFallbackMessages(getErrorFallbackMessagesByLocale(locale));
  });

  // 4. 注入子应用配置（支持异步注册表）
  const registryMode = getRegistryMode();

  if (registryMode === 'static') {
    // === 静态注册（同步） ===
    microRuntime!.registerApps(
      MICRO_APPS.map((app) => ({
        name: app.name,
        entry: import.meta.env.DEV
          ? `//localhost:${app.devPort}`
          : getProdEntry(app),
        container: '#subapp-container',
        activeRule: app.activeRule,
        sandbox: app.sandbox,
      })),
    );
    finishRuntimeSetup();
  } else {
    // === 远程/自动注册（异步：拉取注册表 → 注册 → 启动） ===
    void (async () => {
      try {
        runtimeLogger.info(`Registry mode: ${registryMode}, fetching ...`);
        const configs = await microRuntime!.registerAppsAsync({
          adapter: registryMode,
        });
        runtimeLogger.info(`Registry resolved: ${configs.length} apps registered`);
        finishRuntimeSetup();
      } catch (err) {
        // 注册表拉取失败：回退到静态配置
        runtimeLogger.warn(`Registry fetch failed (${String(err)}), fallback to static`);
        microRuntime!.registerApps(
          MICRO_APPS.map((app) => ({
            name: app.name,
            entry: import.meta.env.DEV
              ? `//localhost:${app.devPort}`
              : getProdEntry(app),
            container: '#subapp-container',
            activeRule: app.activeRule,
            sandbox: app.sandbox,
          })),
        );
        finishRuntimeSetup();
      }
    })();
  }
}

/**
 * 公共后续流程：生命周期钩子 + 启动 prefetch。
 * 同步 / 异步两条注册路径最终都汇聚于此。
 */
function finishRuntimeSetup() {
  if (!microRuntime) return;

  // 5. 生命周期钩子
  microRuntime.addLifecycleHook('beforeLoad', (app) => {
    runtimeLogger.debug(`子应用 ${app.name} 开始加载...`);
    if (preferences.transition.progress) {
      startProgress();
    }
  });
  microRuntime.addLifecycleHook('afterMount', (app) => {
    runtimeLogger.debug(`子应用 ${app.name} 挂载完成`);
    if (preferences.transition.progress) {
      stopProgress();
    }
  });
  microRuntime.addLifecycleHook('error', (app, err) => {
    runtimeLogger.error(`子应用 ${app.name} 加载/挂载失败:`, err);
    if (preferences.transition.progress) {
      stopProgress();
    }
  });
  microRuntime.addLifecycleHook('afterUnmount', (app) => {
    runtimeLogger.debug(`子应用 ${app.name} 卸载完成`);
  });

  // 6. 启动：micro-kernel 内建 prefetch 预热高频应用
  const prefetchApps = import.meta.env.VITE_PREFETCH_APPS
    ? import.meta.env.VITE_PREFETCH_APPS.split(',').map((s) => s.trim())
    : ['userinfo-web', 'project-web'];

  microRuntime.start({
    prefetch: (app) => prefetchApps.includes(app.name),
  });

  // v4.0 P1-2: 注册路由预测预加载策略
  // 基于马尔可夫链转移概率，在当前应用即将空闲时预测并预加载下一目标
  registerRoutePreloadStrategy();

  // P3-1: 注册 per-app Tab 会话追踪
  // 每次路由跳转后，若目标路由属于某子应用，更新该子应用的会话状态
  // （打开路径集合 / 最后激活路径），驱动 useTabbarMicroSync 的保活/pin 决策
  router.afterEach((to) => {
    if (!to?.fullPath) return;
    const appName = getAppFromPathFromMap(to.fullPath);
    if (appName) {
      recordSubAppTabOpened(to.fullPath, appName);
    }
  });
}

/**
 * 从子应用路由映射中反查应用名（bootstrap 内部复用）。
 * 与 use-tabbar-micro-sync 保持解耦，避免 import cycle。
 */
function getAppFromPathFromMap(path: string): null | string {
  for (const [prefix, appName] of Object.entries(PATH_TO_APP_MAP)) {
    if (path.startsWith(prefix)) {
      return appName;
    }
  }
  return null;
}

/**
 * 注册路由预测预加载策略（v4.0 P1-2）。
 *
 * 基于马尔可夫链模型，在浏览器空闲时预测并预加载用户最可能访问的下一子应用。
 * 仅在非弱网、非省流量模式下运行，避免浪费带宽。
 */
function registerRoutePreloadStrategy(): void {
  try {
    const preloadManager = getPreloadManager();

    // 收集所有已注册应用到路由预测策略
    const apps = (microRuntime as any)?.getApps?.() || [];
    if (!apps.length) return;

    // 使用自动模式（_route_prediction_ 来源为“全局所有应用”，predict 会遍历已知转移对）
    const strategy = createRoutePreloadStrategy(
      apps,
      undefined,
      async (appName: string) => {
        runtimeLogger.debug(`Route prediction preload triggered for ${appName}`);
        // 触发 prefetch：仅下载模块不执行 mount
        try {
          await (microRuntime as any)?.prefetch?.(appName);
        } catch (err) {
          runtimeLogger.warn(`Route prediction prefetch failed for ${appName}: ${String(err)}`);
        }
      },
      { minProbability: 0.15, maxPreloads: 2 },
    );

    preloadManager.registerStrategy('__route_prediction__', strategy);

    // 在浏览器空闲时触发预测预加载
    if ('requestIdleCallback' in window) {
      (window as any).requestIdleCallback(() => {
        void preloadManager.triggerPreload('__route_prediction__');
      }, { timeout: 3000 });
    } else {
      setTimeout(() => {
        void preloadManager.triggerPreload('__route_prediction__');
      }, 3000);
    }
  } catch (err) {
    runtimeLogger.warn(`Route preload strategy registration skipped: ${String(err)}`);
  }
}

/**
 * 应用引导启动。
 */
async function bootstrap(namespace: string) {
  // E6: 初始化日志系统（生产默认 INFO，开发默认 DEBUG）
  // localStorage 'ydsz:debug' 可运行期覆盖调试过滤
  initLogger({ isDev: import.meta.env.DEV });

  await initComponentAdapter();
  await initSetupYDSZForm();

  // 功能开关：在 Pinia 之前注册定义，保证默认值尽早生效；
  // init 不阻塞（远程加载在内部异步进行，失败降级到默认值）
  registerApplicationFlags();
  const { initFeatureFlags } = await import('@ydsz-core/feature-flags');
  await initFeatureFlags(featureFlagsOptions());

  const app = createApp(App);

  app.directive('loading', ElLoading.directive);

  registerLoadingDirective(app, {
    loading: false, // YDSZ提供的v-loading指令和Element Plus提供的v-loading指令二选一即可，此处false表示不注册YDSZ提供的v-loading指令
    spinning: 'spinning',
  });

  await setupI18n(app);
  await initStores(app, { namespace });

  // 在 Pinia 初始化之后才创建路由守卫
  initRouterGuard();

  registerAccessDirective(app);

  // v-safe-html — XSS 防护指令
  registerSafeHtmlDirective(app);

  // v-watermark — 敏感页面水印指令
  registerWatermarkDirective(app);

  const { initTippy } = await import('@ydsz/common-ui/es/tippy');
  initTippy(app);

  app.use(router);

  const { MotionPlugin } = await import('@ydsz/plugins/motion');
  app.use(MotionPlugin);

  watchEffect(() => {
    if (preferences.app.dynamicTitle) {
      const routeTitle = router.currentRoute.value.meta?.title;
      const pageTitle =
        (routeTitle ? `${$t(routeTitle)} - ` : '') + preferences.app.name;
      useTitle(pageTitle);
    }
  });

  // 安装前端监控（错误捕获 + Web Vitals）
  // v3.1: 注入 release 版本（sourcemap 关联）+ getUserId（全链路追踪）+ 生产采样
  // v4.0 P0-3: 可选 Sentry 转发（设置 VITE_SENTRY_DSN 时启用）
  const sentryDsn = import.meta.env.VITE_SENTRY_DSN as string | undefined;
  setupMonitor(app, {
    getUserId: () => {
      try {
        return useUserStore().userInfo?.userId;
      } catch {
        return undefined;
      }
    },
    release: import.meta.env.VITE_APP_RELEASE || import.meta.env.VITE_APP_VERSION,
    // 生产环境高频错误采样 80%，开发环境全量
    sampleRate: import.meta.env.PROD ? 0.8 : 1,
    ...(sentryDsn ? { sentryDsn } : {}),
  });

  app.mount('#app');

  // v4.0 P2-2: 初始化灰度分流管理器（有远端 URL 或本地 fallback 配置时启用）
  // Canary 在首次 prefetch 决策时影响子应用加载哪个版本（stable/canary）
  void (async () => {
    try {
      const remoteUrl = import.meta.env.VITE_CANARY_CONFIG_URL;
      if (remoteUrl || import.meta.env.DEV) {
        await getCanaryManager().init({
          remoteUrl: remoteUrl || undefined,
        });
        runtimeLogger.info('Canary manager initialized');
      }
    } catch (err) {
      runtimeLogger.warn(`Canary init skipped: ${String(err)}`);
    }
  })();

  // v3.1 修复：app.mount 同步渲染，#subapp-container 已就绪，
  // 直接同步注册微前端运行时，避免此前 readyState 延迟导致的初始路由
  // 匹配与子应用激活时序竞态（直连子应用 URL 时可能出现容器空白闪烁）。
  registerMicroRuntime();

  // v3.7.0: 开发态启用微前端 DevTools 面板（Alt+Shift+M 切换）
  if (import.meta.env.DEV) {
    enableMicroDevTools();
  }

  // v4.0 P2-1: 启用微前端运行时 DevTools Bridge —— 桥接生命周期事件到 Chrome Extension
  // 启用条件：开发态自动启用；生产态需 localStorage 'micro-kernel:devtools' = '1'
  // kernel 实例在 registerMicroRuntime() 内 createKernel() 时被挂载到 window.__MICRO_KERNEL__
  if ((window as any).__MICRO_KERNEL__) {
    enableDevToolsBridge({
      kernel: (window as any).__MICRO_KERNEL__,
      microRuntime,
    });
  }

  // E2: 会话超时预警（必须在 initStores 之后、app 挂载之后调用，
  // 此时 Pinia 与 effect scope 均已就绪，组件卸载时定时器自动清理）
  useSessionExpiryWarning();

  // F6: 跨标签页状态同步（登出/会话失效联动）
  useCrossTabSync();
}

export { bootstrap };
