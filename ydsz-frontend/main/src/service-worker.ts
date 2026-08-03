/**
 * Service Worker 注册模块
 *
 * 负责注册和管理 Service Worker，实现离线缓存和性能优化
 *
 * @path main/src/service-worker.ts
 * @author ydsz-team
 * @since 1.0.0
 */

/**
 * 注册 Service Worker
 *
 * 仅在支持 Service Worker 的环境（HTTPS 或 localhost）中注册
 * 使用 vite-plugin-pwa 生成的 sw.js
 */
export async function registerServiceWorker(): Promise<void> {
  // 检查是否支持 Service Worker
  if (!('serviceWorker' in navigator)) {
    console.warn('[ServiceWorker] Service Worker not supported');
    return;
  }

  // 检查是否在安全上下文中（HTTPS 或 localhost）
  const isSecureContext = window.location.protocol === 'https:' || window.location.hostname === 'localhost';
  if (!isSecureContext) {
    console.warn('[ServiceWorker] Service Worker requires HTTPS or localhost');
    return;
  }

  try {
    // 注册 Service Worker
    const registration = await navigator.serviceWorker.register('/sw.js', {
      scope: '/',
    });

    console.info('[ServiceWorker] Registered successfully', registration.scope);

    // 监听更新
    registration.addEventListener('updatefound', () => {
      const newWorker = registration.installing;
      if (!newWorker) return;

      console.info('[ServiceWorker] Update found, installing...');

      newWorker.addEventListener('statechange', () => {
        if (newWorker.state === 'installed') {
          if (navigator.serviceWorker.controller) {
            // 有新版本可用，提示用户刷新
            console.info('[ServiceWorker] New version available, please refresh');
            // 可以在这里触发 UI 提示用户刷新页面
          } else {
            console.info('[ServiceWorker] Content cached for offline use');
          }
        }
      });
    });

    // 监听控制器变化（新版本激活后）
    let refreshing = false;
    navigator.serviceWorker.addEventListener('controllerchange', () => {
      if (refreshing) return;
      refreshing = true;
      console.info('[ServiceWorker] New controller activated, reloading...');
      window.location.reload();
    });
  } catch (error) {
    console.error('[ServiceWorker] Registration failed:', error);
  }
}

/**
 * 注销 Service Worker
 *
 * 用于开发环境或需要清除缓存时
 */
export async function unregisterServiceWorker(): Promise<boolean> {
  if (!('serviceWorker' in navigator)) {
    return false;
  }

  try {
    const registration = await navigator.serviceWorker.ready;
    const result = await registration.unregister();
    console.info('[ServiceWorker] Unregistered:', result);
    return result;
  } catch (error) {
    console.error('[ServiceWorker] Unregister failed:', error);
    return false;
  }
}

/**
 * 清除所有缓存
 *
 * 清除 Service Worker 的所有缓存存储
 */
export async function clearServiceWorkerCache(): Promise<void> {
  if ('caches' in window) {
    const keys = await caches.keys();
    await Promise.all(keys.map((key) => caches.delete(key)));
    console.info('[ServiceWorker] Cache cleared');
  }
}
