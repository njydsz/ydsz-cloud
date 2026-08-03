/**
 * options 配置模块
 *
 * @path conf\vite-config\src\options.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { Options as PwaPluginOptions } from 'vite-plugin-pwa';

import type { ImportmapPluginOptions } from './typing';

const isDevelopment = process.env.NODE_ENV === 'development';

/**
 * 生成 PWA 插件的默认 manifest 配置。
 *
 * 基于应用名称生成 name/short_name（开发环境加 ` dev` 后缀便于区分），
 * 图标固定引用静态资源 CDN，供各子应用复用一致的可安装体验。
 *
 * @param name - 应用名称，用于 manifest 展示
 * @returns 部分 PWA 插件配置（可与其他配置合并）
 */
const getDefaultPwaOptions = (name: string): Partial<PwaPluginOptions> => ({
  manifest: {
    description:
      'YDSZ Admin is a modern admin dashboard template based on Vue 3. ',
    icons: [
      {
        sizes: '192x192',
        src: 'https://unpkg.com/@njydsz/static-source@0.1.7/source/pwa-icon-192.png',
        type: 'image/png',
      },
      {
        sizes: '512x512',
        src: 'https://unpkg.com/@njydsz/static-source@0.1.7/source/pwa-icon-512.png',
        type: 'image/png',
      },
    ],
    name: `${name}${isDevelopment ? ' dev' : ''}`,
    short_name: `${name}${isDevelopment ? ' dev' : ''}`,
  },
});

/**
 * importmap CDN 外置共享依赖。
 *
 * 与 micro-kernel ESM 直引模式联动：通过 importmap 将 Vue/Pinia/Element Plus
 * 等核心依赖标记为 external，浏览器运行时按 importmap 映射统一加载单例。
 * 具体依赖清单见 {@link ./micro-shared-deps.ts}。
 *
 * @deprecated 请直接使用 {@link ./micro-shared-deps.ts} 的 ALL_SHARED_DEPS / getSharedDeps
 */
import { ALL_SHARED_DEPS } from './micro-shared-deps';
const defaultImportmapOptions: ImportmapPluginOptions = {
  defaultProvider: 'esm.sh',
  importmap: [...ALL_SHARED_DEPS],
};

export { defaultImportmapOptions, getDefaultPwaOptions };
