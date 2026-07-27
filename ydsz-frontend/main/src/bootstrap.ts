import { createApp, watchEffect } from 'vue';

import { registerAccessDirective } from '@ydsz/access';
import { registerLoadingDirective } from '@ydsz/common-ui/es/loading';
import { preferences } from '@ydsz/preferences';
import { initStores } from '@ydsz/stores';
import '@ydsz/styles';
import '@ydsz/styles/ele';

import { ElLoading } from 'element-plus';
import { useTitle } from '@vueuse/core';
import { registerMicroApps, start } from 'qiankun';

import { $t, setupI18n } from '#/locales';

import { initComponentAdapter } from './adapter/component';
import { initSetupYDSZForm } from './adapter/form';
import App from './app.vue';
import { microApps } from './qiankun';
import { initRouterGuard, router } from './router';

async function bootstrap(namespace: string) {
  await initComponentAdapter();
  await initSetupYDSZForm();

  const app = createApp(App);

  app.directive('loading', ElLoading.directive);

  registerLoadingDirective(app, {
    loading: 'loading',
    spinning: 'spinning',
  });

  await setupI18n(app);
  await initStores(app, { namespace });

  // 在 Pinia 初始化之后才创建路由守卫
  initRouterGuard();

  registerAccessDirective(app);

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

  app.mount('#app');

  // 使用 DOMContentLoaded 确保 DOM 已渲染后注册 qiankun
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', registerQiankun);
  } else {
    registerQiankun();
  }
}

function registerQiankun() {
  registerMicroApps(microApps, {
    beforeLoad: (app) => {
      console.warn(`[qiankun] 子应用 ${app.name} 开始加载...`);
      return Promise.resolve();
    },
    afterMount: (app) => {
      console.warn(`[qiankun] 子应用 ${app.name} 挂载完成`);
      return Promise.resolve();
    },
    afterUnmount: (app) => {
      console.warn(`[qiankun] 子应用 ${app.name} 卸载完成`);
      return Promise.resolve();
    },
  });

  start({
    sandbox: {
      experimentalStyleIsolation: true,
    },
    prefetch: true,
  });
}

export { bootstrap };
