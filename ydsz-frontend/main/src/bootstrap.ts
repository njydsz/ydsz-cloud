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
import { preferences } from '@ydsz/preferences';
import { initStores } from '@ydsz/stores';
import '@ydsz/styles';
import '@ydsz/styles/ele';

import { ElLoading } from 'element-plus';
import { useTitle } from '@vueuse/core';

import { $t, setupI18n } from '#/locales';

import { setupMonitor } from '@ydsz/monitor';

import { initComponentAdapter } from './adapter/component';
import { initSetupYDSZForm } from './adapter/form';
import App from './app.vue';
import { router, initRouterGuard } from './router';

import { createKernel } from '@ydsz/micro-kernel';
import { createRuntime, registerKernel } from '@ydsz/micro-runtime';
import { MICRO_APPS } from '@ydsz/vite-config';

/** 单个 micro-runtime 实例（整个主应用生命周期唯一，供其他模块获取） */
export let microRuntime: ReturnType<typeof createRuntime> | null = null;

/**
 * 启动微前端运行时。
 *
 * 注册 micro-kernel 自研内核，从注册表 MICRO_APPS 消费子应用清单。
 * 预加载策略：micro-kernel 内置 requestIdleCallback 预热 userinfo/project 两个高频应用。
 */
function registerMicroRuntime() {
  // 1. 注册 micro-kernel 内核
  registerKernel('micro-kernel', () => createKernel());

  // 2. 创建运行时实例
  microRuntime = createRuntime({ kernel: 'micro-kernel' });
  console.info('[MicroRuntime] Initialized with kernel: micro-kernel');

  // 3. 从注册表注入子应用配置
  microRuntime.registerApps(
    MICRO_APPS.map((app) => ({
      name: app.name,
      entry: import.meta.env.DEV
        ? `//localhost:${app.devPort}`
        : `/ydsz-${app.name.replace('-web', '')}-web/`,
      container: '#subapp-container',
      activeRule: app.activeRule,
    })),
  );

  // 4. 生命周期钩子
  microRuntime.addLifecycleHook('beforeLoad', (app) => {
    console.warn(`[MicroRuntime] 子应用 ${app.name} 开始加载...`);
  });
  microRuntime.addLifecycleHook('afterMount', (app) => {
    console.warn(`[MicroRuntime] 子应用 ${app.name} 挂载完成`);
  });
  microRuntime.addLifecycleHook('afterUnmount', (app) => {
    console.warn(`[MicroRuntime] 子应用 ${app.name} 卸载完成`);
  });

  // 5. 启动：micro-kernel 内建 prefetch 预热高频应用
  microRuntime.start({
    prefetch: (app) => ['userinfo-web', 'project-web'].includes(app.name),
  });
}

/**
 * 应用引导启动。
 */
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

  // v-safe-html — XSS 防护指令
  registerSafeHtmlDirective(app);

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
  setupMonitor(app);

  app.mount('#app');

  // v3.1 修复：app.mount 同步渲染，#subapp-container 已就绪，
  // 直接同步注册微前端运行时，避免此前 readyState 延迟导致的初始路由
  // 匹配与子应用激活时序竞态（直连子应用 URL 时可能出现容器空白闪烁）。
  registerMicroRuntime();
}

export { bootstrap };
