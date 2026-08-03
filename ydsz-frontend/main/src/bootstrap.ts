/**
 * 应用引导程序，初始化全局插件和配置
 *
 * v3.0: 接入 @ydsz/micro-runtime 接口层，脱离对 qiankun 的直接依赖。
 *       内核实现通过 createRuntime({ kernel: 'qiankun' }) 选择，
 *       后续切换 lite-kernel 只需改一个字面量。
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

import { createQiankunAdapter } from '@ydsz/micro-adapter-qiankun';
import {
  type MicroAppConfig,
  createRuntime,
  provideGlobalState,
  registerKernel,
} from '@ydsz/micro-runtime';
import { microApps } from './qiankun';

/** 单个 micro-runtime 实例（整个主应用生命周期唯一，供其他模块获取） */
export let microRuntime: ReturnType<typeof createRuntime> | null = null;

/**
 * 启动微前端运行时。
 *
 * 注册 qiankun adapter 作为内核，通过接口层 API 完成微应用注册与启动。
 * 保留hover预加载策略（从原 registerQiankun 平移，逻辑不变）。
 */
function registerMicroRuntime() {
  const { setupHoverPrefetch } = import('./qiankun/prefetch');

  // 1. 注册 qiankun 内核（业务零改动，只是包了一层）
  registerKernel('qiankun', () => createQiankunAdapter());

  // 2. 创建运行时实例
  microRuntime = createRuntime({ kernel: 'qiankun' });

  // 3. 注入已有的子应用配置（保留 dev/prod 端口映射——配置已从 qiankun/index.ts 兼容读取）
  microRuntime.registerApps(microApps as MicroAppConfig[]);

  // 4. 生命周期钩子：从现有 bootstrap.ts:104-115 平移
  microRuntime.addLifecycleHook('beforeLoad', (app) => {
    console.warn(`[MicroRuntime] 子应用 ${app.name} 开始加载...`);
  });
  microRuntime.addLifecycleHook('afterMount', (app) => {
    console.warn(`[MicroRuntime] 子应用 ${app.name} 挂载完成`);
  });
  microRuntime.addLifecycleHook('afterUnmount', (app) => {
    console.warn(`[MicroRuntime] 子应用 ${app.name} 卸载完成`);
  });

  // 5. 启动
  microRuntime.start({
    sandbox: { styleIsolation: true },
    prefetch: false,
  });

  // 6. Hover 预加载策略（逻辑不变，从 main/src/qiankun/prefetch.ts 独立模块加载）
  import('./qiankun/prefetch').then(({ setupHoverPrefetch }) => {
    setupHoverPrefetch();
  });
}

/**
 * 应用引导启动。
 *
 * 依次完成组件/表单适配器初始化、插件与指令注册、i18n、状态管理、路由守卫，
 * 最后挂载根实例并在 DOM 就绪后启动微前端运行时。
 *
 * @param namespace - 项目唯一命名空间（含版本与环境），用于隔离偏好设置与本地存储
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

  // 使用 DOMContentLoaded 确保 DOM 已渲染后注册微前端运行时
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', registerMicroRuntime);
  } else {
    registerMicroRuntime();
  }
}

export { bootstrap };
