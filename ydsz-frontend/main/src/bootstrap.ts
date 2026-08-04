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
import { registerWatermarkDirective } from '@ydsz/common-ui/es/watermark';
import { initLogger } from '@ydsz-core/shared/utils';
import { preferences } from '@ydsz/preferences';
import { initStores, useUserStore } from '@ydsz/stores';
import '@ydsz/styles';
import '@ydsz/styles/ele';

import { ElLoading } from 'element-plus';
import { useTitle } from '@vueuse/core';

import { $t, setupI18n } from '#/locales';

import { setupMonitor } from '@ydsz/monitor';

import { initComponentAdapter } from './adapter/component';
import { initSetupYDSZForm } from './adapter/form';
import App from './app.vue';
import {
  featureFlagsOptions,
  registerApplicationFlags,
} from './feature-flags';
import { useCrossTabSync } from './hooks/use-cross-tab-sync';
import { useSessionExpiryWarning } from './hooks/use-session-expiry-warning';
import { router, initRouterGuard } from './router';

import { createKernel, getVersionManager } from '@ydsz/micro-kernel';
import { createRuntime, registerKernel } from '@ydsz/micro-runtime';
import { createLogger } from '@ydsz-core/shared/utils';
import { MICRO_APPS } from '@ydsz/vite-config';

/** 单个 micro-runtime 实例（整个主应用生命周期唯一，供其他模块获取） */
export let microRuntime: ReturnType<typeof createRuntime> | null = null;

/** bootstrap 内部统一日志器（自动带 [ydsz][Bootstrap] 前缀） */
const logger = createLogger('Bootstrap');
/** 微运行时日志器 */
const runtimeLogger = createLogger('MicroRuntime');
/** 版本管理器日志器 */
const versionLogger = createLogger('VersionManager');

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
  runtimeLogger.info('Initialized with kernel: micro-kernel');

  // 3. 初始化版本管理器
  getVersionManager({
    checkInterval: 5 * 60 * 1000, // 5分钟检查一次
    autoCheck: true,
    onVersionCheck: (result) => {
      if (result.hasUpdate) {
        versionLogger.info(
          `App ${result.appName} updated: ${result.currentVersion} -> ${result.latestVersion}`,
        );
        // 可以在这里触发更新提示或自动刷新逻辑
      }
    },
  });
  versionLogger.info('Initialized');

  // 4. 从注册表注入子应用配置
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

  // 5. 生命周期钩子（debug 级别，避免生产噪音）
  microRuntime.addLifecycleHook('beforeLoad', (app) => {
    runtimeLogger.debug(`子应用 ${app.name} 开始加载...`);
  });
  microRuntime.addLifecycleHook('afterMount', (app) => {
    runtimeLogger.debug(`子应用 ${app.name} 挂载完成`);
  });
  microRuntime.addLifecycleHook('afterUnmount', (app) => {
    runtimeLogger.debug(`子应用 ${app.name} 卸载完成`);
  });

  // 6. 启动：micro-kernel 内建 prefetch 预热高频应用
  // 预加载应用清单优先从环境变量 VITE_PREFETCH_APPS 读取（逗号分隔），
  // 未配置时回退到默认的 userinfo-web / project-web
  const prefetchApps = import.meta.env.VITE_PREFETCH_APPS
    ? import.meta.env.VITE_PREFETCH_APPS.split(',').map((s) => s.trim())
    : ['userinfo-web', 'project-web'];

  microRuntime.start({
    prefetch: (app) => prefetchApps.includes(app.name),
  });
}

/**
 * 应用引导启动。
 */
async function bootstrap(namespace: string) {
  // E6: 初始化日志系统（生产默认 INFO，开发默认 DEBUG）
  // localStorage 'ydsz:debug' 可运行期覆盖调试过滤
  initLogger({ isDev: import.meta.env.DEV });

  await initComponentAdapter();
  await initSetupYDSZForm();

  // 功能开关：在 Pinia 之前注册定义，保证默认值尽早生效；
  // init 不阻塞（远程加载在内部异步进行，失败降级到默认值）
  registerApplicationFlags();
  const { initFeatureFlags } = await import('@ydsz-core/feature-flags');
  await initFeatureFlags(featureFlagsOptions());

  const app = createApp(App);

  app.directive('loading', ElLoading.directive);

  registerLoadingDirective(app, {
    loading: false, // YDSZ提供的v-loading指令和Element Plus提供的v-loading指令二选一即可，此处false表示不注册YDSZ提供的v-loading指令
    spinning: 'spinning',
  });

  await setupI18n(app);
  await initStores(app, { namespace });

  // 在 Pinia 初始化之后才创建路由守卫
  initRouterGuard();

  registerAccessDirective(app);

  // v-safe-html — XSS 防护指令
  registerSafeHtmlDirective(app);

  // v-watermark — 敏感页面水印指令
  registerWatermarkDirective(app);

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
  // v3.1: 注入 release 版本（sourcemap 关联）+ getUserId（全链路追踪）+ 生产采样
  setupMonitor(app, {
    getUserId: () => {
      try {
        return useUserStore().userInfo?.userId;
      } catch {
        return undefined;
      }
    },
    release: import.meta.env.VITE_APP_RELEASE || import.meta.env.VITE_APP_VERSION,
    // 生产环境高频错误采样 80%，开发环境全量
    sampleRate: import.meta.env.PROD ? 0.8 : 1,
  });

  app.mount('#app');

  // v3.1 修复：app.mount 同步渲染，#subapp-container 已就绪，
  // 直接同步注册微前端运行时，避免此前 readyState 延迟导致的初始路由
  // 匹配与子应用激活时序竞态（直连子应用 URL 时可能出现容器空白闪烁）。
  registerMicroRuntime();

  // E2: 会话超时预警（必须在 initStores 之后、app 挂载之后调用，
  // 此时 Pinia 与 effect scope 均已就绪，组件卸载时定时器自动清理）
  useSessionExpiryWarning();

  // F6: 跨标签页状态同步（登出/会话失效联动）
  useCrossTabSync();
}

export { bootstrap };
