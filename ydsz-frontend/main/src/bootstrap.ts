/**
 * 应用引导程序，初始化全局插件和配置
 *
 * @path main\src\bootstrap.ts
 * @author ydsz-team
 * @since 1.0.0
 */
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

import { setupMonitor } from '@ydsz/monitor';

import { initComponentAdapter } from './adapter/component';
import { initSetupYDSZForm } from './adapter/form';
import App from './app.vue';
import {
  getSubAppProps,
  initGlobalStateCommunication,
} from './qiankun/global-state';
import { microApps } from './qiankun';
import { initRouterGuard, router } from './router';

/**
 * 应用引导启动。
 *
 * 依次完成组件/表单适配器初始化、插件与指令注册、i18n、状态管理、路由守卫与微前端注册，最后挂载根实例。
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

  // 使用 DOMContentLoaded 确保 DOM 已渲染后注册 qiankun
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', registerQiankun);
  } else {
    registerQiankun();
  }
}

function registerQiankun() {
  // 初始化全局状态通信
  initGlobalStateCommunication();

  registerMicroApps(
    microApps.map((app) => ({
      ...app,
      // 注入全局状态通信 props
      props: getSubAppProps(app.name),
    })),
    {
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
  );

  start({
    sandbox: {
      experimentalStyleIsolation: true,
    },
    // 按需预加载：用户首次交互后开始预加载，而非启动时全量预加载
    prefetch: false,
  });

  // 按需预加载：用户鼠标 hover 菜单项时预加载对应子应用
  setupHoverPrefetch();
}

export { bootstrap };

/**
 * 按需预加载：监听鼠标 hover 菜单链接，预加载对应子应用入口
 *
 * 对标飞书微前端方案：避免启动时全量预加载 9 个子应用影响首屏性能。
 */
function setupHoverPrefetch() {
  const prefetched = new Set<string>();
  let prefetchTimer: null | ReturnType<typeof setTimeout> = null;

  // 菜单路由前缀到子应用入口的映射
  const routePrefixMap: Record<string, string> = {
    '/ydsz-user': import.meta.env.DEV
      ? '//localhost:5601'
      : '/ydsz-userinfo-web/',
    '/ydsz-sys': import.meta.env.DEV
      ? '//localhost:5602'
      : '/ydsz-system-web/',
    '/ydsz-proj': import.meta.env.DEV
      ? '//localhost:5603'
      : '/ydsz-project-web/',
    '/ydsz-msg': import.meta.env.DEV
      ? '//localhost:5604'
      : '/ydsz-message-web/',
    '/ydsz-cron': import.meta.env.DEV
      ? '//localhost:5605'
      : '/ydsz-cronjob-web/',
    '/ydsz-flow': import.meta.env.DEV
      ? '//localhost:5606'
      : '/ydsz-workflow-web/',
    '/ydsz-wiki': import.meta.env.DEV
      ? '//localhost:5607'
      : '/ydsz-nextwiki-web/',
    '/ydsz-rule': import.meta.env.DEV
      ? '//localhost:5608'
      : '/ydsz-literule-web/',
    '/ydsz-ai': import.meta.env.DEV
      ? '//localhost:5610'
      : '/ydsz-agent-web/',
  };

  function prefetchApp(entry: string) {
    if (prefetched.has(entry)) return;
    prefetched.add(entry);

    // 使用 link rel=prefetch 预加载子应用 HTML 入口
    const link = document.createElement('link');
    link.rel = 'prefetch';
    link.href = entry;
    link.as = 'document';
    document.head.appendChild(link);
  }

  // 监听全局 mouseover 事件（事件委托）
  document.addEventListener(
    'mouseover',
    (event) => {
      const target = event.target as HTMLElement;
      if (!target) return;

      // 查找最近的 <a> 标签
      const anchor = target.closest('a');
      if (!anchor) return;

      const href = anchor.getAttribute('href') || '';

      // 匹配子应用路由前缀
      for (const [prefix, entry] of Object.entries(routePrefixMap)) {
        if (href.startsWith(prefix)) {
          // 延迟 200ms 预加载（避免快速划过）
          if (prefetchTimer) clearTimeout(prefetchTimer);
          prefetchTimer = setTimeout(() => prefetchApp(entry), 200);
          break;
        }
      }
    },
    { capture: true },
  );

  // 页面首次交互后预加载最常用的子应用
  function prefetchOnFirstInteraction() {
    // 预加载第一个子应用（userinfo-web 是最常用的）
    const firstEntry = routePrefixMap['/ydsz-user'];
    if (firstEntry) {
      setTimeout(() => prefetchApp(firstEntry), 3000);
    }

    // 移除监听
    document.removeEventListener('click', prefetchOnFirstInteraction);
    document.removeEventListener('keydown', prefetchOnFirstInteraction);
  }

  document.addEventListener('click', prefetchOnFirstInteraction, { once: true });
  document.addEventListener('keydown', prefetchOnFirstInteraction, { once: true });

  console.info('[Qiankun] Hover-based prefetch strategy installed');
}
