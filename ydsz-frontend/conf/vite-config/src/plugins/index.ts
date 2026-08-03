/**
 * index 配置模块
 *
 * @path conf\vite-config\src\plugins\index.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { PluginOption } from 'vite';

import type {
  ApplicationPluginOptions,
  CommonPluginOptions,
  ConditionPlugin,
  LibraryPluginOptions,
} from '../typing';

import viteVueI18nPlugin from '@intlify/unplugin-vue-i18n/vite';
import viteVue from '@vitejs/plugin-vue';
import viteVueJsx from '@vitejs/plugin-vue-jsx';
import { visualizer as viteVisualizerPlugin } from 'rollup-plugin-visualizer';
import viteCompressPlugin from 'vite-plugin-compression';
import viteDtsPlugin from 'vite-plugin-dts';
import { createHtmlPlugin as viteHtmlPlugin } from 'vite-plugin-html';
import { VitePWA } from 'vite-plugin-pwa';
import viteVueDevTools from 'vite-plugin-vue-devtools';

import { viteArchiverPlugin } from './archiver';
import { viteFontPlugin } from './font';
import { viteExtraAppConfigPlugin } from './extra-app-config';
import { viteImageminPlugin } from './imagemin';
import { viteImportMapPlugin } from './importmap';
import { viteInjectAppLoadingPlugin } from './inject-app-loading';
import { viteMetadataPlugin } from './inject-metadata';
import { viteLicensePlugin } from './license';
import { vitePrintPlugin } from './print';
import { viteVxeTableImportsPlugin } from './vxe-table';

/**
 * 过滤并执行条件成立的条件插件，收集其实际插件实例。
 *
 * 遍历条件插件列表，仅当 `condition` 为真时才调用对应工厂函数，
 * 最终将结果展平为一维插件数组。
 *
 * @param conditionPlugins - 条件插件列表（含 condition 与插件工厂）
 * @returns 条件成立插件展开后的 Vite 插件数组
 */
async function loadConditionPlugins(conditionPlugins: ConditionPlugin[]) {
  const plugins: PluginOption[] = [];
  for (const conditionPlugin of conditionPlugins) {
    if (conditionPlugin.condition) {
      const realPlugins = await conditionPlugin.plugins();
      plugins.push(...realPlugins);
    }
  }
  return plugins.flat();
}

/**
 * 构造所有项目类型共用的条件插件集合（Vue/Vue-JSX/DevTools/元数据/依赖分析）。
 *
 * 以 {@link ConditionPlugin} 形式返回，便于由 {@link loadConditionPlugins}
 * 统一按 condition 实际装载，devtools/visualizer 等仅在对应条件满足时生效。
 *
 * @param options - 通用插件配置（是否开发工具、是否注入元数据、是否构建、是否依赖分析）
 * @returns 条件插件数组
 */
async function loadCommonPlugins(
  options: CommonPluginOptions,
): Promise<ConditionPlugin[]> {
  const { devtools, injectMetadata, isBuild, visualizer } = options;
  return [
    {
      condition: true,
      plugins: () => [
        viteVue({
          script: {
            defineModel: true,
            // propsDestructure: true,
          },
        }),
        viteVueJsx(),
      ],
    },

    {
      condition: !isBuild && devtools,
      plugins: () => [viteVueDevTools()],
    },
    {
      condition: injectMetadata,
      plugins: async () => [await viteMetadataPlugin()],
    },
    {
      condition: isBuild && !!visualizer,
      plugins: () => [<PluginOption>viteVisualizerPlugin({
          filename: './node_modules/.cache/visualizer/stats.html',
          gzipSize: true,
          open: true,
        })],
    },
  ];
}

/**
 * 装配应用类型所需的全部 Vite 插件（i18n/打印/归档/PWA/压缩等）。
 *
 * 先提取应用专属开关，剩余字段作为通用配置交给 {@link loadCommonPlugins}，
 * 再按各自 condition 追加应用插件；所有插件经 {@link loadConditionPlugins}
 * 过滤后合并返回。
 *
 * @param options - 应用插件配置选项
 * @returns 应用类型插件数组
 */
async function loadApplicationPlugins(
  options: ApplicationPluginOptions,
): Promise<PluginOption[]> {
  // 单独取，否则commonOptions拿不到
  const isBuild = options.isBuild;
  const env = options.env;

  const {
    archiver,
    archiverPluginOptions,
    compress,
    compressTypes,
    extraAppConfig,
    html,
    i18n,
    imagemin,
    font,
    importmap,
    importmapOptions,
    injectAppLoading,
    license,
    print,
    printInfoMap,
    pwa,
    pwaOptions,
    vxeTableLazyImport,
    ...commonOptions
  } = options;

  const commonPlugins = await loadCommonPlugins(commonOptions);

  return await loadConditionPlugins([
    ...commonPlugins,
    {
      condition: i18n,
      plugins: async () => {
        return [
          viteVueI18nPlugin({
            compositionOnly: true,
            fullInstall: true,
            runtimeOnly: true,
          }),
        ];
      },
    },
    {
      condition: print,
      plugins: async () => {
        return [await vitePrintPlugin({ infoMap: printInfoMap })];
      },
    },
    {
      condition: vxeTableLazyImport,
      plugins: async () => {
        return [await viteVxeTableImportsPlugin()];
      },
    },
    {
      condition: injectAppLoading,
      plugins: async () => [await viteInjectAppLoadingPlugin(!!isBuild, env)],
    },
    {
      condition: license,
      plugins: async () => [await viteLicensePlugin()],
    },
    {
      condition: pwa,
      plugins: () =>
        VitePWA({
          // 手动注册 Service Worker，避免自动注入
          injectRegister: false,
          // 开发模式下禁用，避免干扰 HMR
          disable: !isBuild,
          workbox: {
            // 缓存静态资源（JS/CSS/图片/字体）
            globPatterns: ['**/*.{js,css,html,png,jpg,jpeg,gif,svg,ico,woff,woff2,ttf,eot,webp,wasm}'],
            // 忽略较大的文件（> 2MB）
            maximumFileSizeToCacheInBytes: 2 * 1024 * 1024,
            // 运行时缓存策略
            runtimeCaching: [
              {
                // API 请求使用 NetworkFirst 策略
                urlPattern: /^https?:\/\/.*\/api\//,
                handler: 'NetworkFirst',
                options: {
                  cacheName: 'api-cache',
                  expiration: {
                    maxEntries: 50,
                    maxAgeSeconds: 5 * 60, // 5分钟
                  },
                  cacheableResponse: {
                    statuses: [0, 200],
                  },
                },
              },
              {
                // 图片资源使用 CacheFirst 策略
                urlPattern: /\.(?:png|jpg|jpeg|svg|gif|webp|ico)$/,
                handler: 'CacheFirst',
                options: {
                  cacheName: 'image-cache',
                  expiration: {
                    maxEntries: 100,
                    maxAgeSeconds: 30 * 24 * 60 * 60, // 30天
                  },
                },
              },
              {
                // 字体资源使用 CacheFirst 策略
                urlPattern: /\.(?:woff|woff2|ttf|eot)$/,
                handler: 'CacheFirst',
                options: {
                  cacheName: 'font-cache',
                  expiration: {
                    maxEntries: 30,
                    maxAgeSeconds: 365 * 24 * 60 * 60, // 1年
                  },
                },
              },
              {
                // 第三方库使用 StaleWhileRevalidate 策略
                urlPattern: /^https?:\/\/(?:cdn|unpkg|esm\.sh|jspm\.io)/,
                handler: 'StaleWhileRevalidate',
                options: {
                  cacheName: 'third-party-cache',
                  expiration: {
                    maxEntries: 50,
                    maxAgeSeconds: 7 * 24 * 60 * 60, // 7天
                  },
                },
              },
            ],
            // 离线回退页面
            navigateFallback: '/index.html',
            navigateFallbackDenylist: [/^\/api/, /\/__\w+__/],
          },
          ...pwaOptions,
          manifest: {
            name: 'YDSZ PMIS',
            short_name: 'YDSZ',
            description: 'YDSZ 项目管理系统',
            display: 'standalone',
            start_url: '/',
            theme_color: '#409eff',
            background_color: '#ffffff',
            icons: [
              {
                src: '/pwa-192x192.png',
                sizes: '192x192',
                type: 'image/png',
              },
              {
                src: '/pwa-512x512.png',
                sizes: '512x512',
                type: 'image/png',
              },
              {
                src: '/pwa-512x512.png',
                sizes: '512x512',
                type: 'image/png',
                purpose: 'maskable',
              },
            ],
            ...pwaOptions?.manifest,
          },
        }),
    },
    {
      condition: isBuild && !!compress,
      plugins: () => {
        const compressPlugins: PluginOption[] = [];
        if (compressTypes?.includes('brotli')) {
          compressPlugins.push(
            viteCompressPlugin({ deleteOriginFile: false, ext: '.br' }),
          );
        }
        if (compressTypes?.includes('gzip')) {
          compressPlugins.push(
            viteCompressPlugin({ deleteOriginFile: false, ext: '.gz' }),
          );
        }
        return compressPlugins;
      },
    },
    {
      condition: !!html,
      plugins: () => [viteHtmlPlugin({ minify: true })],
    },
    {
      condition: isBuild && importmap,
      plugins: () => {
        return [viteImportMapPlugin(importmapOptions)];
      },
    },
    {
      condition: isBuild && extraAppConfig,
      plugins: async () => [
        await viteExtraAppConfigPlugin({ isBuild: true, root: process.cwd() }),
      ],
    },
    {
      condition: archiver,
      plugins: async () => {
        return [await viteArchiverPlugin(archiverPluginOptions)];
      },
    },
  ]);
}

/**
 * 装配库类型所需的 Vite 插件（仅 Vue/Vue-JSX 与可选的 DTS 类型输出）。
 *
 * 与 {@link loadApplicationPlugins} 不同，库不需要 PWA/压缩/归档等应用特性，
 * 仅在构建时按需开启 DTS 声明文件生成。
 *
 * @param options - 库插件配置选项
 * @returns 库类型插件数组
 */
async function loadLibraryPlugins(
  options: LibraryPluginOptions,
): Promise<PluginOption[]> {
  // 单独取，否则commonOptions拿不到
  const isBuild = options.isBuild;
  const { dts, ...commonOptions } = options;
  const commonPlugins = await loadCommonPlugins(commonOptions);
  return await loadConditionPlugins([
    ...commonPlugins,
    {
      condition: isBuild && !!dts,
      plugins: () => [viteDtsPlugin({ logLevel: 'error' })],
    },
  ]);
}

export {
  loadApplicationPlugins,
  loadLibraryPlugins,
  viteArchiverPlugin,
  viteCompressPlugin,
  viteDtsPlugin,
  viteHtmlPlugin,
  viteVisualizerPlugin,
  viteVxeTableImportsPlugin,
};
