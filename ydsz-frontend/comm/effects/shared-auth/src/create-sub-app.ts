/**
 * 子应用启动工厂 — 消除各子应用 main.ts 中重复的 bootstrap/mount/unmount 样板代码。
 *
 * v3.0: 对接 lite-kernel ESM 原生微前端运行时。
 *       - defineSubApp 导出标准 LifecycleExports（ESM entry 规范）
 *       - lite-kernel 通过 dynamic import() 加载并调用 lifecycle 方法
 *       - 独立运行时（非微前端环境）自启动
 *
 * @path comm/effects/shared-auth/src/create-sub-app.ts
 * @author ydsz-team
 * @since 2.0.0
 */
import type { App as VueApp } from 'vue';
import type { RouteRecordRaw, Router } from 'vue-router';

import { createApp } from 'vue';
import { createRouter, createWebHistory } from 'vue-router';

import { registerAccessDirective } from '@ydsz/access';
import { registerLoadingDirective, registerSafeHtmlDirective } from '@ydsz/common-ui';
import { setupMonitor } from '@ydsz/monitor';
import { initPreferences } from '@ydsz/preferences';
import { initStores } from '@ydsz/stores';

import { ElLoading } from 'element-plus';

import { setupSharedAuth } from './setup-shared-auth';

/** 子应用启动配置 */
export interface SubAppConfig {
  /** 应用唯一标识（如 'project-web'，与微应用注册名一致） */
  appName: string;
  /** 路由 basename（如 '/ydsz-proj'） */
  basename: string;
  /** 路由表 */
  routes: RouteRecordRaw[];
  /** 路由守卫安装回调 */
  guard?: (router: Router) => void;
  /** 初始化动态路由回调（在 router 创建后、guard 注册前执行） */
  initRoutes?: (router: Router) => void;
  /** Vue 根组件 */
  rootComponent: Parameters<typeof createApp>[0];
  /** 应用级自定义 setup（用于 I18N/ComponentAdapter 等） */
  onSetup?: (app: VueApp) => Promise<void> | void;
  /** 偏好设置覆盖 */
  preferencesOverrides?: Record<string, unknown>;
  /** 命名空间覆写 */
  namespace?: string;
}

/** 标准化挂载参数（兼容 lite-kernel mountProps） */
interface StandardMountProps {
  container?: HTMLElement;
  [key: string]: unknown;
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
  registerSafeHtmlDirective(vueApp);

  const { initTippy } = await import('@ydsz/common-ui/es/tippy');
  initTippy(vueApp);

  const { MotionPlugin } = await import('@ydsz/plugins/motion');
  vueApp.use(MotionPlugin);

  vueApp.config.errorHandler = (err, _instance, info) => {
    console.error(`[${appName}] Unhandled error:`, err, info);
  };
  vueApp.config.warnHandler = (msg, _instance, trace) => {
    console.warn(`[${appName}] Vue warning:`, msg, trace);
  };
}

/** 内核 mount 逻辑（lite-kernel & 独立运行共享） */
async function coreMount(
  config: SubAppConfig,
  props?: StandardMountProps,
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

  if (onSetup) {
    await onSetup(app);
  }

  await installBasePlugins(app, appName);

  if (initRoutes) {
    initRoutes(router);
  }

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
 * 子应用生命周期对象（ESM entry 标准导出格式）。
 *
 * lite-kernel 通过 dynamic import() 加载子应用入口，
 * 期望子应用 export { bootstrap, mount, unmount, update }。
 */
export function defineSubApp(config: SubAppConfig) {
  return {
    async bootstrap() {},
    async mount(props: StandardMountProps) {
      await coreMount(config, props);
    },
    async unmount() {
      app?.unmount();
      app = null;
    },
    async update(_props: StandardMountProps) {},
  };
}

/**
 * 创建子应用标准启动入口（保留向后兼容的 API）。
 *
 * 内部调用 defineSubApp，并在独立运行时自启动。
 *
 * @param config - 子应用配置
 */
export function createSubApp(config: SubAppConfig) {
  const lifecycle = defineSubApp(config);

  // 独立运行（非微前端环境）时自启动
  if (!import.meta.env.VITE_APP_NAMESPACE) {
    (async () => {
      const router = await coreMount(config);
      await router.push(window.location.pathname.replace(config.basename, '') || '/');

      const { unmountGlobalLoading } = await import('@ydsz/utils');
      unmountGlobalLoading();
    })();
  }

  return lifecycle;
}
