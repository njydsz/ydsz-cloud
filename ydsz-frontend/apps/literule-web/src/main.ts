import type { App as VueApp } from 'vue';

import { createApp } from 'vue';
import { createRouter, createWebHistory } from 'vue-router';

import { registerAccessDirective } from '@ydsz/access';
import { registerLoadingDirective } from '@ydsz/common-ui';
import { initPreferences } from '@ydsz/preferences';
import { initStores } from '@ydsz/stores';
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
    history: createWebHistory(basename || '/ydsz-rule'),
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
  console.warn('[literule-web] bootstrap');
}

async function mount(props: Record<string, unknown>) {
  console.warn('[literule-web] mount', props);

  const { container } = props;

  await initPreferences({
    namespace,
    overrides: overridesPreferences,
  });

  await import('./api/request');

  app = createApp(RootApp);

  const router = createAppRouter('/ydsz-rule');
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
  console.warn('[literule-web] unmount');
  app?.unmount();
  app = null;
}

async function update(props: Record<string, unknown>) {
  console.warn('[literule-web] update', props);
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

    app = createApp(RootApp);

    await import('./api/request');

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
