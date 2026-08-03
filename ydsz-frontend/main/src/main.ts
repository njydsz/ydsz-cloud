/**
 * 应用入口文件，创建并挂载 Vue 实例
 *
 * @path main\src\main.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import { initPreferences } from '@ydsz/preferences';
import { unmountGlobalLoading } from '@ydsz/utils';

import { overridesPreferences } from './preferences';
import { registerServiceWorker } from './service-worker';

/**
 * 应用初始化完成之后再进行页面加载渲染
 */
async function initApplication() {
  // name用于指定项目唯一标识
  // 用于区分不同项目的偏好设置以及存储数据的key前缀以及其他一些需要隔离的数据
  const env = import.meta.env.PROD ? 'prod' : 'dev';
  const appVersion = import.meta.env.VITE_APP_VERSION;
  const namespace = `${import.meta.env.VITE_APP_NAMESPACE}-${appVersion}-${env}`;

  // app偏好设置初始化
  await initPreferences({
    namespace,
    overrides: overridesPreferences,
  });

  // 启动应用并挂载
  // vue应用主要逻辑及视图
  const { bootstrap } = await import('./bootstrap');
  await bootstrap(namespace);

  // 移除并销毁loading
  unmountGlobalLoading();

  // 注册 Service Worker（仅生产环境）
  if (import.meta.env.PROD) {
    registerServiceWorker();
  }
}

initApplication();
