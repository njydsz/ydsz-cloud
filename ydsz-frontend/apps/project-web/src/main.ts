import type { App as VueApp } from 'vue';

import { createApp } from 'vue';
import { createRouter, createWebHistory } from 'vue-router';

import { registerAccessDirective } from '@ydsz/access';
import { registerLoadingDirective } from '@ydsz/common-ui';
import { initSharedRequest } from '@ydsz/shared-auth';
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
const namespace = `${import.meta.env.VITE_APP_NAMESPACE}-${appVersion}-${env}`;

let app: null | VueApp = null;

/**
 * 初始化共享请求客户端（注入 reAuthenticate / refreshToken 回调）
 */
async function initSharedAuth() {
  const { preferences } = await import('@ydsz/preferences');
  const { refreshTokenApi } = await import('@ydsz/shared-auth');

  initSharedRequest(
    // doReAuthenticate: token 失效时退出登录
    async () => {
      console.warn('[project-web] Access token expired, re-authenticating...');
      const accessStore = useAccessStore();
      accessStore.setAccessToken(null);
      if (
        preferences.app.loginExpiredMode === 'modal' &&
        accessStore.isAccessChecked
      ) {
        accessStore.setLoginExpired(true);
      } else {
        resetAllStores();
        accessStore.setLoginExpired(false);
        window.location.href = '/';
      }
    },
    // doRefreshToken: 刷新 accessToken
    async () => {
      const accessStore = useAccessStore();
      const refreshToken = (accessStore as any).refreshToken;
      if (!refreshToken) return null;
      try {
        const resp = await refreshTokenApi(refreshToken);
        const newToken = resp.data?.accessToken || (resp.data as unknown as string);
        if (typeof newToken === 'string') {
          accessStore.setAccessToken(newToken);
        }
        return newToken;
      } catch {
        return null;
      }
    },
  );
}

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
    history: createWebHistory(basename || '/ydsz-proj'),
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
  console.warn('[project-web] bootstrap');
}

async function mount(props: Record<string, unknown>) {
  console.warn('[project-web] mount', props);

  const { container } = props;

  await initPreferences({
    namespace,
    overrides: overridesPreferences,
  });

  // 初始化共享请求客户端（必须在 app.mount 之前）
  await initSharedAuth();

  app = createApp(RootApp);

  // 安装前端监控（错误捕获 + Web Vitals）
  setupMonitor(app);

  const router = createAppRouter('/ydsz-proj');
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
  console.warn('[project-web] unmount');
  app?.unmount();
  app = null;
}

async function update(props: Record<string, unknown>) {
  console.warn('[project-web] update', props);
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
    await initSharedAuth();

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
