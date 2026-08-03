/**
 * 应用入口文件，创建并挂载 Vue 实例
 *
 * @path apps\nextwiki-web\src\main.ts
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

import { initComponentAdapter } from './adapter/component';
import { initSetupYDSZForm } from './adapter/form';
import RootApp from './app.vue';
import { setupI18n } from './locales';
import { overridesPreferences } from './preferences';
import { createRouterGuard, initRoutes } from './router/guard';
import { routes } from './router/routes';

const env = import.meta.env.PROD ? 'prod' : 'dev';
const appVersion = import.meta.env.VITE_APP_VERSION;
const namespace = `${import.meta.env.VITE_APP_NAMESPACE}-${appVersion}-${env}`;

let app: null | VueApp = null;


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

function createAppRouter(basename?: string) {
  return createRouter({
    history: createWebHistory(basename || '/ydsz-wiki'),
    routes,
    scrollBehavior: (to, _from, savedPosition) => {
      if (savedPosition) return savedPosition;
      return to.hash
        ? { behavior: 'smooth', el: to.hash }
        : { left: 0, top: 0 };
    },
  });
}

async function bootstrap() {
  console.warn('[nextwiki-web] bootstrap');
}

async function mount(props: Record<string, unknown>) {
  console.warn('[nextwiki-web] mount', props);

  const { container } = props;

  await initPreferences({
    namespace,
    overrides: overridesPreferences,
  });

  // 初始化共享请求客户端（必须在 app.mount 之前）
  await setupSharedAuth('nextwiki-web');

  app = createApp(RootApp);

  // 安装前端监控（错误捕获 + Web Vitals）
  setupMonitor(app);

  const router = createAppRouter('/ydsz-wiki');
  initRoutes(router);
  app.use(router);

  await setupApp(app);

  createRouterGuard(router);

  const mountNode =
    (container as HTMLElement)?.querySelector('#app') ||
    document.querySelector('#app');
  app.mount(mountNode);
}

async function unmount() {
  console.warn('[nextwiki-web] unmount');
  app?.unmount();
  app = null;
}

async function update(props: Record<string, unknown>) {
  console.warn('[nextwiki-web] update', props);
}

if (!import.meta.env.VITE_APP_NAMESPACE) {
  (async () => {
    await initPreferences({
      namespace,
      overrides: overridesPreferences,
    });

    // 独立运行时也初始化共享请求客户端
    await setupSharedAuth('nextwiki-web');

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
