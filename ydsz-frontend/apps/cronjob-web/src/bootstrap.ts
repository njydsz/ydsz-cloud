/**
 * 应用引导程序，初始化全局插件和配置
 *
 * @path apps\cronjob-web\src\bootstrap.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import { createApp, watchEffect } from 'vue';

import { registerAccessDirective } from '@ydsz/access';
import { registerLoadingDirective } from '@ydsz/common-ui';
import { registerWatermarkDirective } from '@ydsz/common-ui/es/watermark';
import { preferences } from '@ydsz/preferences';
import { initStores } from '@ydsz/stores';
import '@ydsz/styles';
import '@ydsz/styles/ele';

import { useTitle } from '@vueuse/core';
import { ElLoading } from 'element-plus';

import { $t, setupI18n } from '#/locales';

import { initComponentAdapter } from './adapter/component';
import { initSetupYDSZForm } from './adapter/form';
import App from './app.vue';
import { router } from './router';

async function bootstrap(namespace: string) {
  // 初始化组件适配器
  await initComponentAdapter();

  // 初始化表单组件
  await initSetupYDSZForm();

  // // 设置弹窗的默认配置
  // setDefaultModalProps({
  //   fullscreenButton: false,
  // });
  // // 设置抽屉的默认配置
  // setDefaultDrawerProps({
  //   zIndex: 2000,
  // });
  const app = createApp(App);

  // 注册Element Plus提供的v-loading指令
  app.directive('loading', ElLoading.directive);

  // 注册YDSZ提供的v-loading和v-spinning指令
  registerLoadingDirective(app, {
    loading: false, // YDSZ提供的v-loading指令和Element Plus提供的v-loading指令二选一即可，此处false表示不注册YDSZ提供的v-loading指令
    spinning: 'spinning',
  });

  // v-watermark — 敏感页面水印指令
  registerWatermarkDirective(app);

  // 国际化 i18n 配置
  await setupI18n(app);

  // 配置 pinia-tore
  await initStores(app, { namespace });

  // 安装权限指令
  registerAccessDirective(app);

  // 初始化 tippy
  const { initTippy } = await import('@ydsz/common-ui/es/tippy');
  initTippy(app);

  // 配置路由及路由守卫
  app.use(router);

  // 配置Motion插件
  const { MotionPlugin } = await import('@ydsz/plugins/motion');
  app.use(MotionPlugin);

  // 动态更新标题
  watchEffect(() => {
    if (preferences.app.dynamicTitle) {
      const routeTitle = router.currentRoute.value.meta?.title;
      const pageTitle =
        (routeTitle ? `${$t(routeTitle)} - ` : '') + preferences.app.name;
      useTitle(pageTitle);
    }
  });

  app.mount('#app');
}

export { bootstrap };
