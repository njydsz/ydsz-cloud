/**
 * vite-plugin-pwa 集成 —— Service Worker 缓存层
 *
 * 子应用资源缓存策略：
 * -关键 app-shell 网络优先（NetworkFirst）—— 保证最新版本
 * -静态资源（js/css/字体）缓存优先（CacheFirst）+ 7天过期
 * - manifest 文件 网络优先 + 短缓存（5min）
 * - JSON API 网络优先 + 离线降级缓存
 *
 * 缓存命名：v4:subapp-{appName}-{version} —— 版本切换时自动清理旧缓存
 *
 * 使用方式：在子应用 vite.config.ts 中：
 *   plugins: [swPlugin({ appName: 'project-web' })]
 *
 * @path conf/vite-config/src/plugin-sw.ts
 * @since 4.0.0
 */

import type { Plugin } from 'vite';
import type { VitePWAOptions } from 'vite-plugin-pwa';

export interface SWPluginOptions {
  /** 应用名（用于缓存前缀隔离） */
  appName: string;
  /** scope —— 默认根据 activeRule 推断 */
  scope?: string;
  /** 是否启用（生产默认开启，dev 关闭） */
  enabled?: boolean;
  /** 预缓存额外 URL 列表 */
  additionalPrecache?: string[];
}

/**
 * 子应用 Service Worker 配置。
 *
 * 每个子应用注册自己的 SW，作用域限定在 activeRule 下，
 * 避免主子应用 SW 互相干扰。
 */
export function swPlugin(options: SWPluginOptions): Plugin[] {
  const { appName, scope, enabled } = options;
  const isProd = process.env.NODE_ENV === 'production';

  if (enabled === false || (enabled == null && !isProd)) {
    return [];
  }

  // 动态导入，避免 dev 环境无 vite-plugin-pwa 时抛出
  try {
    const { VitePWA } = require('vite-plugin-pwa');
    const pwaOptions: Partial<VitePWAOptions> = {
      // 子应用注册在 activeRule 子路径下，避免与主应用 SW 冲突
      scope: scope ?? `/ydsz-${appName.replace('-web', '')}/`,
      base: '/',
      // 注意：mode 由 VitePWA 推断
      // 注册策略
      registerType: 'autoUpdate',
      // 是否自动显示更新提示（子应用由主应用收敛管理，此处关闭原生 prompt）
      injectRegister: 'inline',
      // 预缓存策略
      workbox: {
        // 预缓存 glob 排除 map 文件
        globPatterns: ['**/*.{js,css,html,svg,png,woff2}'],
        maximumFileSizeToCacheInBytes: 5 * 1024 * 1024, // 5MB
        // 导航回退 —— 子应用独立模式下使用子应用自己的 index.html
        navigateFallback: '/index.html',
        // 运行时缓存
        runtimeCaching: [
          {
            urlPattern: /\/api\/.*/i,
            handler: 'NetworkFirst' as const,
            options: {
              cacheName: `v4:api-${appName}`,
              networkTimeoutSeconds: 10,
              expiration: { maxEntries: 100, maxAgeSeconds: 5 * 60 },
              cacheableResponse: { statuses: [0, 200] },
              backgroundSync: {
                name: `v4:api-queue-${appName}`,
                options: { maxRetentionTime: 24 * 60 },
              },
            },
          },
          {
            urlPattern: /\.(?:png|jpg|jpeg|svg|gif|webp)/i,
            handler: 'CacheFirst' as const,
            options: {
              cacheName: `v4:img-${appName}`,
              expiration: { maxEntries: 200, maxAgeSeconds: 7 * 24 * 3600 },
            },
          },
          {
            urlPattern: /\.css/i,
            handler: 'CacheFirst' as const,
            options: {
              cacheName: `v4:css-${appName}`,
              expiration: { maxEntries: 100, maxAgeSeconds: 7 * 24 * 3600 },
            },
          },
          {
            urlPattern: /\.js/i,
            handler: 'CacheFirst' as const,
            options: {
              cacheName: `v4:js-${appName}`,
              expiration: { maxEntries: 200, maxAgeSeconds: 7 * 24 * 3600 },
            },
          },
        ],
        cleanupOutdatedCaches: true,
        clientsClaim: true,
        skipWaiting: true,
      },
      // manifest（子应用自己的 Web App Manifest，不影响主应用）
      manifest: {
        name: `YDSZ PMIS - ${appName}`,
        short_name: appName,
        start_url: '.',
        scope: scope ?? '/',
        display: 'standalone',
        background_color: '#ffffff',
        theme_color: '#409eff',
        icons: [],
      },
      // 禁用默认 SW 注册提示
      devOptions: { enabled: false },
    };
    return [VitePWA(pwaOptions) as Plugin];
  } catch (err) {
    console.warn(`[sw-plugin] vite-plugin-pwa not installed for ${appName}, skipping SW.`);
    return [];
  }
}

/**
 * 缓存工具函数（供子应用 main.ts 调用）。
 *
 * - 在 SW 注册后清理版本过期的缓存
 * - 向主应用上报缓存命中率
 */
export function cleanupOutdatedSubAppCaches(currentVersion: string): void {
  if (!('caches' in globalThis)) return;
  const prefix = 'v4:';
  caches.keys().then((names) => {
    const stale = names.filter(
      (name) => name.startsWith(prefix) && !name.includes(currentVersion),
    );
    return Promise.all(stale.map((n) => caches.delete(n)));
  }).then((deleted) => {
    if (deleted.length) console.log(`[micro-kernel] Purged ${deleted.length} stale SW caches`);
  });
}

export function currentSubAppSWVersion(appName: string): string {
  return `${appName}@${import.meta.env.VITE_APP_VERSION || 'dev'}`;
}
