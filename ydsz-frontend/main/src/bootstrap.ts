/**
 * 应用引导程序，初始化全局插件和配置
 *
 * v3.0: 接入 @ydsz/micro-runtime 接口层，脱离对 qiankun 的直接依赖。
 *       内核通过 VITE_MICRO_KERNEL 环境变量选择（lite | qiankun，默认 lite）。
 *       寄存器同时注册两个内核，运行时按环境变量实例化一个。
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
import { createLiteKernel } from '@ydsz/micro-kernel-lite';
import {
  createRuntime,
  provideGlobalState,
  registerKernel,
  type KernelName,
} from '@ydsz/micro-runtime';
import { microApps } from './qiankun';

/** 当前选用的内核名（环境变量 VITE_MICRO_KERNEL，默认 lite） */
const KERNEL: KernelName = (import.meta.env.VITE_MICRO_KERNEL as string) || 'lite';

/** 单个 micro-runtime 实例（整个主应用生命周期唯一，供其他模块获取） */
export let microRuntime: ReturnType<typeof createRuntime> | null = null;

/**
 * 启动微前端运行时。
 *
 * 双内核寄存器：同时注册 qiankun 与 lite-kernel，运行时按 VITE_MICRO_KERNEL
 * 环境变量选择。切回 qiankun 仅需设置 VITE_MICRO_KERNEL=qiankun 重启。
 */
function registerMicroRuntime() {
  // 1. 注册双内核
  registerKernel('qiankun', () => createQiankunAdapter());
  registerKernel('lite', () => createLiteKernel());

  // 2. 创建运行时实例
  microRuntime = createRuntime({ kernel: KERNEL });
  console.info(`[MicroRuntime] Initialized with kernel: ${KERNEL}`);

  // 3. 注入已有的子应用注册表
  microRuntime.registerApps([...microApps]);

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

  // 5. 启动
  microRuntime.start({
    sandbox: { styleIsolation: KERNEL === 'qiankun' },
    // lite-kernel: 启动后按 prefetch 函数预加载，qiankun: 走 idle prefetch
    prefetch: KERNEL === 'lite'
      ? (app) => ['userinfo-web', 'project-web'].includes(app.name)
      : false,
  });

  // 6. qiankun 内核额外的预加载补丁（lite-kernel 通过 prefetch 函数已覆盖）
  if (KERNEL === 'qiankun') {
    setupIdlePrefetch();
  }
}

/**
 * 空闲预加载：登录后按配置列表预加载最常用的子应用入口。
 *
 * 替代原来的自研 hover prefetch（bootstrap.ts 137-227 行），
 * 用 requestIdleCallback 延迟预加载，不依赖鼠标行为。
 */
function setupIdlePrefetch() {
  const PREFETCH_LIST = ['userinfo-web', 'project-web'];

  const prefetchByLink = (entry: string) => {
    const link = document.createElement('link');
    link.rel = 'prefetch';
    link.href = entry;
    link.as = 'document';
    document.head.appendChild(link);
  };

  const doPrefetch = () => {
    for (const name of PREFETCH_LIST) {
      const app = microApps.find((a) => a.name === name);
      if (app) prefetchByLink(app.entry);
    }
  };

  if (typeof requestIdleCallback !== 'undefined') {
    requestIdleCallback(doPrefetch, { timeout: 4000 });
  } else {
    setTimeout(doPrefetch, 3000);
  }

  console.info('[MicroRuntime] Idle prefetch strategy installed');
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
