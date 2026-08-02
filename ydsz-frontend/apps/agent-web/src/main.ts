/**
 * 应用入口文件，创建并挂载 Vue 实例
 *
 * @path apps\agent-web\src\main.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { App as VueApp } from 'vue';

import { createApp } from 'vue';
import { createRouter, createWebHistory } from 'vue-router';

import { registerAccessDirective } from '@ydsz/access';
import { registerLoadingDirective } from '@ydsz/common-ui';
import { setupSharedAuth } from '@ydsz/shared-auth';
import { setupMonitor } from '@ydsz/monitor';
import { initPreferences } from '@ydsz/preferences';
import { resetAllStores, useAccessStore, initStores } from '@ydsz/stores';
import '@ydsz/styles';
import '@ydsz/styles/ele';

import { ElLoading } from 'element-plus';
import {
  qiankunWindow,
  renderWithQiankun,
} from 'vite-plugin-qiankun/dist/helper';

import { initComponentAdapter } from './adapter/component';
import { initSetupYDSZForm } from './adapter/form';
import RootApp from './app.vue';
import { setupI18n } from './locales';
import { overridesPreferences } from './preferences';
import { createRouterGuard, initRoutes } from './router/guard';
import { routes } from './router/routes';

const env = import.meta.env.PROD ? 'prod' : 'dev';
const appVersion = import.meta.env.VITE_APP_VERSION;
// 持久化命名空间：由应用标识 + 版本 + 环境组成，用于隔离本子应用的 preferences / pinia 等存储
const namespace = `${import.meta.env.VITE_APP_NAMESPACE}-${appVersion}-${env}`;

let app: null | VueApp = null;

/**
 * 装配 Vue 应用实例：注册组件/表单适配器、指令、i18n、状态与动画插件。
 *
 * @param vueApp - 待装配的 Vue 应用实例
 */
async function setupApp(vueApp: VueApp) {
  await initComponentAdapter();
  await initSetupYDSZForm();

  vueApp.directive('loading', ElLoading.directive);

  registerLoadingDirective(vueApp, {
    loading: false,
    spinning: 'spinning',
  });

  await setupI18n(vueApp);
  await initStores(vueApp, { namespace });
  registerAccessDirective(vueApp);

  const { initTippy } = await import('@ydsz/common-ui/es/tippy');
  initTippy(vueApp);

  const { MotionPlugin } = await import('@ydsz/plugins/motion');
  vueApp.use(MotionPlugin);
}

/**
 * 创建路由实例。
 *
 * @param basename - 路由基路径，未传入时回退到 '/ydsz-ai'
 * @returns 配置完成的 Vue Router 实例
 */
function createAppRouter(basename?: string) {
  return createRouter({
    history: createWebHistory(basename || '/ydsz-ai'),
    routes,
    scrollBehavior: (to, _from, savedPosition) => {
      if (savedPosition) return savedPosition;
      return to.hash
        ? { behavior: 'smooth', el: to.hash }
        : { left: 0, top: 0 };
    },
  });
}

/**
 * Qiankun 生命周期：bootstrap（子应用初始化，此处仅占位）。
 */
async function bootstrap() {
  console.warn('[agent-web] bootstrap');
}

/**
 * Qiankun 生命周期：mount（子应用挂载）。
 *
 * 在挂载前完成偏好初始化与共享请求客户端装配，确保请求能正确携带 Token。
 *
 * @param props - 由主应用注入的 qiankun props（含挂载容器 container）
 */
async function mount(props: Record<string, unknown>) {
  console.warn('[agent-web] mount', props);

  const { container } = props;

  await initPreferences({
    namespace,
    overrides: overridesPreferences,
  });

  // 初始化共享请求客户端（必须在 app.mount 之前）
  await setupSharedAuth('agent-web');

  app = createApp(RootApp);

  // 安装前端监控（错误捕获 + Web Vitals）
  setupMonitor(app);

  const router = createAppRouter('/ydsz-ai');
  initRoutes(router);
  app.use(router);

  await setupApp(app);

  createRouterGuard(router);

  const mountNode =
    (container as HTMLElement)?.querySelector('#app') ||
    document.querySelector('#app');
  app.mount(mountNode);
}

/**
 * Qiankun 生命周期：unmount（子应用卸载）。
 *
 * 卸载 Vue 实例并清空引用，避免主应用切换时内存泄漏。
 */
async function unmount() {
  console.warn('[agent-web] unmount');
  app?.unmount();
  app = null;
}

/**
 * Qiankun 生命周期：update（主应用下发 props 变更时的回调，此处仅占位）。
 *
 * @param props - 主应用下发的更新后 props
 */
async function update(props: Record<string, unknown>) {
  console.warn('[agent-web] update', props);
}

renderWithQiankun({
  bootstrap,
  mount,
  unmount,
  update,
});

if (!qiankunWindow.__POWERED_BY_QIANKUN__) {
  (async () => {
    await initPreferences({
      namespace,
      overrides: overridesPreferences,
    });

    // 独立运行时也初始化共享请求客户端
    await setupSharedAuth('agent-web');

    app = createApp(RootApp);

    // 独立运行时也安装监控
    setupMonitor(app);

    const router = createAppRouter(import.meta.env.VITE_BASE);
    initRoutes(router);
    app.use(router);

    await setupApp(app);

    createRouterGuard(router);

    app.mount('#app');

    const { unmountGlobalLoading } = await import('@ydsz/utils');
    unmountGlobalLoading();
  })();
}
