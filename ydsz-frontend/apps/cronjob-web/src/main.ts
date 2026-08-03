/**
 * 应用入口文件 — 使用 createSubApp 工厂标准化生命周期
 *
 * @path apps\cronjob-web\src\main.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import { createSubApp } from '@ydsz/shared-auth';
import '@ydsz/styles';
import '@ydsz/styles/ele';

import { initComponentAdapter } from './adapter/component';
import { initSetupYDSZForm } from './adapter/form';
import RootApp from './app.vue';
import { setupI18n } from './locales';
import { overridesPreferences } from './preferences';
import { createRouterGuard, initRoutes } from './router/guard';
import { routes } from './router/routes';

export const { bootstrap, mount, unmount, update } = createSubApp({
  appName: 'cronjob-web',
  basename: '/ydsz-cron',
  routes,
  rootComponent: RootApp,
  preferencesOverrides,
  initRoutes,
  guard: createRouterGuard,
  async onSetup(app) {
    await initComponentAdapter();
    await initSetupYDSZForm();
    await setupI18n(app);
  },
});
