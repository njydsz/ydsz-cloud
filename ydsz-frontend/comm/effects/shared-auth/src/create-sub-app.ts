/**
 * 子应用启动工厂 — 消除各子应用 main.ts 中重复的 bootstrap/mount/unmount 样板代码。
 *
 * 封装了 Qiankun 生命周期 + Vue 应用创建 + 路由/状态/I18N/权限/监控初始化全流程。
 * 子应用只需配置 appName / basename / routes / guard / onSetup 即可一行启动。
 *
 * @path comm\effects\shared-auth\src\create-sub-app.ts
 * @author ydsz-team
 * @since 2.0.0
 */
import type { App as VueApp } from 'vue';
import type { RouteRecordRaw, Router } from 'vue-router';

import { createApp } from 'vue';
import { createRouter, createWebHistory } from 'vue-router';

import { registerAccessDirective } from '@ydsz/access';
import { registerLoadingDirective } from '@ydsz/common-ui';
import { setupMonitor } from '@ydsz/monitor';
import { initPreferences } from '@ydsz/preferences';
import { initStores } from '@ydsz/stores';

import { ElLoading } from 'element-plus';
import {
  qiankunWindow,
  renderWithQiankun,
} from 'vite-plugin-qiankun/dist/helper';

import { setupSharedAuth } from './setup-shared-auth';

/** 子应用启动配置 */
export interface SubAppConfig {
  /** 应用唯一标识（如 'project-web'，与 Qiankun 注册名一致） */
  appName: string;
  /** 路由 basename（如 '/ydsz-proj'） */
  basename: string;
  /** 路由表 */
  routes: RouteRecordRaw[];
  /** 路由守卫安装回调（在各子应用内部实现） */
  guard?: (router: Router) => void;
  /** 初始化动态路由回调（在 router 创建后、guard 注册前执行） */
  initRoutes?: (router: Router) => void;
  /** Vue 根组件 */
  rootComponent: Parameters<typeof createApp>[0];
  /** 应用级自定义 setup（在 stores 初始化后执行，用于 I18N/ComponentAdapter 等） */
  onSetup?: (app: VueApp) => Promise<void> | void;
  /** 偏好设置覆盖 */
  preferencesOverrides?: Record<string, unknown>;
  /** 命名空间覆写（默认由 VITE_APP_NAMESPACE + version + env 组成） */
  namespace?: string;
}

let app: null | VueApp = null;

/** 统一安装基础插件与指令 */
async function installBasePlugins(vueApp: VueApp, appName: string) {
  vueApp.directive('loading', ElLoading.directive);

  registerLoadingDirective(vueApp, {
    loading: false,
    spinning: 'spinning',
  });
  registerAccessDirective(vueApp);

  // 动态导入避免顶层静态分析阻塞
  const { initTippy } = await import('@ydsz/common-ui/es/tippy');
  initTippy(vueApp);

  const { MotionPlugin } = await import('@ydsz/plugins/motion');
  vueApp.use(MotionPlugin);

  // 全局错误边界：捕获未处理的组件异常
  vueApp.config.errorHandler = (err, _instance, info) => {
    console.error(`[${appName}] Unhandled error:`, err, info);
  };
  vueApp.config.warnHandler = (msg, _instance, trace) => {
    console.warn(`[${appName}] Vue warning:`, msg, trace);
  };
}

/** 内核 mount 逻辑（Qiankun 子应用 & 独立运行共享） */
async function coreMount(
  config: SubAppConfig,
  props?: Record<string, unknown>,
) {
  const {
    appName,
    basename,
    guard,
    initRoutes,
    namespace: ns,
    onSetup,
    preferencesOverrides = {},
    rootComponent: RootComponent,
    routes,
  } = config;

  const env = import.meta.env.PROD ? 'prod' : 'dev';
  const appVersion = import.meta.env.VITE_APP_VERSION;
  const namespace =
    ns || `${import.meta.env.VITE_APP_NAMESPACE}-${appVersion}-${env}`;

  await initPreferences({
    namespace,
    overrides: preferencesOverrides,
  });

  await setupSharedAuth(appName);

  app = createApp(RootComponent);
  setupMonitor(app);

  const router = createRouter({
    history: createWebHistory(basename),
    routes,
    scrollBehavior: (to, _from, savedPosition) => {
      if (savedPosition) return savedPosition;
      return to.hash
        ? { behavior: 'smooth', el: to.hash }
        : { left: 0, top: 0 };
    },
  });

  app.use(router);

  // 应用级自定义 setup（I18N / ComponentAdapter 等）
  if (onSetup) {
    await onSetup(app);
  }

  await installBasePlugins(app, appName);

  // 初始化动态路由（accessRoutes，在 guard 之前注册）
  if (initRoutes) {
    initRoutes(router);
  }

  // 路由守卫（如有）
  if (guard) {
    guard(router);
  }

  const mountNode =
    (props?.container as HTMLElement)?.querySelector?.('#app') ||
    document.querySelector('#app');
  app.mount(mountNode);

  return router;
}

/**
 * 创建子应用标准启动入口，一行配置即可完成任务注册、生命周期、独立运行支持。
 *
 * @param config - 子应用配置
 *
 * @example
 * ```ts
 * import { createSubApp } from '@ydsz/shared-auth/create-sub-app';
 *
 * createSubApp({
 *   appName: 'project-web',
 *   basename: '/ydsz-proj',
 *   routes,
 *   guard: createRouterGuard,
 *   rootComponent: RootApp,
 *   onSetup: async (app) => {
 *     await initComponentAdapter();
 *     await setupI18n(app);
 *   },
 * });
 * ```
 */
export function createSubApp(config: SubAppConfig) {
  const { appName, basename } = config;

  renderWithQiankun({
    async bootstrap() {},
    async mount(props: Record<string, unknown>) {
      await coreMount(config, props);
    },
    async unmount() {
      app?.unmount();
      app = null;
    },
    async update(_props: Record<string, unknown>) {}
  });

  if (!qiankunWindow.__POWERED_BY_QIANKUN__) {
    (async () => {
      const router = await coreMount(config);
      // 独立运行时需要手动触发首次导航
      await router.push(window.location.pathname.replace(basename, '') || '/');

      const { unmountGlobalLoading } = await import('@ydsz/utils');
      unmountGlobalLoading();
    })();
  }
}
