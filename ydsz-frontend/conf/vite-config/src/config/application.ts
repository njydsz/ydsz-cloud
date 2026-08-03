/**
 * application 配置模块
 *
 * @path conf\vite-config\src\config\application.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { CSSOptions, UserConfig } from 'vite';

import type { DefineApplicationOptions } from '../typing';

import path, { relative } from 'node:path';

import { findMonorepoRoot } from '@ydsz/node-utils';

import { NodePackageImporter } from 'sass';
import { defineConfig, loadEnv, mergeConfig } from 'vite';

import { defaultImportmapOptions, getDefaultPwaOptions } from '../options';
import { loadApplicationPlugins } from '../plugins';
import { loadAndConvertEnv } from '../utils/env';
import { getCommonConfig } from './common';

/**
 * 构造应用（Web 应用）类型的 Vite 配置。
 *
 * 加载并转换环境配置、装配应用插件集（压缩/归档/PWA/打印等），
 * 并将 vue/element/vxe 等做 vendor 分包以优化缓存；最后依次叠加
 * 共用配置与用户自定义 vite 配置，优先级：用户配置 > 应用配置 > 共用配置。
 *
 * @param userConfigPromise - 用户自定义应用配置函数
 * @returns 应用类型的 Vite 配置
 */
function defineApplicationConfig(userConfigPromise?: DefineApplicationOptions) {
  return defineConfig(async (config) => {
    const options = await userConfigPromise?.(config);
    const { appTitle, base, port, ...envConfig } = await loadAndConvertEnv();
    const { command, mode } = config;
    const { application = {}, vite = {} } = options || {};
    const root = process.cwd();
    const isBuild = command === 'build';
    const env = loadEnv(mode, root);

    const plugins = await loadApplicationPlugins({
      archiver: env.VITE_ARCHIVER === 'true',
      archiverPluginOptions: {},
      compress: true,
      compressTypes: ['brotli', 'gzip'],
      devtools: true,
      env,
      extraAppConfig: true,
      html: true,
      i18n: true,
      importmapOptions: defaultImportmapOptions,
      injectAppLoading: true,
      injectMetadata: true,
      isBuild,
      license: true,
      mode,
      print: !isBuild,
      printInfoMap: {
        'YDSZ Admin Docs': 'https://docs.njydsz.com.cn',
      },
      pwa: true,
      pwaOptions: getDefaultPwaOptions(appTitle),
      vxeTableLazyImport: true,
      ...envConfig,
      ...application,
    });

    const { injectGlobalScss = true } = application;

    const applicationConfig: UserConfig = {
      base,
      build: {
        rollupOptions: {
          output: {
            assetFileNames: '[ext]/[name]-[hash].[ext]',
            chunkFileNames: 'js/[name]-[hash].js',
            entryFileNames: 'jse/index-[name]-[hash].js',
            manualChunks: {
              'vue-vendor': ['vue', 'vue-router', 'pinia'],
              'element-vendor': ['element-plus', '@element-plus/icons-vue'],
              'vxe-vendor': ['vxe-table', 'vxe-pc-ui'],
            },
          },
        },
        chunkSizeWarningLimit: 1000,
        target: 'es2018',
      },
      css: createCssOptions(injectGlobalScss),
      esbuild: {
        drop: isBuild
          ? [
              'console',
              'debugger',
            ]
          : [],
        legalComments: 'none',
      },
      plugins,
      server: {
        host: true,
        port,
        warmup: {
          // 预热文件
          clientFiles: [
            './index.html',
            './src/bootstrap.ts',
            './src/{views,layouts,router,store,api,adapter}/*',
          ],
        },
      },
    };

    const mergedCommonConfig = mergeConfig(
      await getCommonConfig(),
      applicationConfig,
    );
    return mergeConfig(mergedCommonConfig, vite);
  });
}

/**
 * 构造 SCSS 预处理器选项，按需向应用注入全局样式。
 *
 * 仅对 apps 下的包注入 `@ydsz/styles/global`，保证全局变量/混合宏可用；
 * 非应用包（如库）不注入以避免副作用污染。
 *
 * @param injectGlobalScss - 是否注入全局 SCSS，默认 true
 * @returns Vite CSS 配置对象
 */
function createCssOptions(injectGlobalScss = true): CSSOptions {
  const root = findMonorepoRoot();
  return {
    preprocessorOptions: injectGlobalScss
      ? {
          scss: {
            additionalData: (content: string, filepath: string) => {
              const relativePath = relative(root, filepath);
              // apps下的包注入全局样式
              if (relativePath.startsWith(`apps${path.sep}`)) {
                return `@use "@ydsz/styles/global" as *;\n${content}`;
              }
              return content;
            },
            api: 'modern',
            importers: [new NodePackageImporter()],
          },
        }
      : {},
  };
}

export { defineApplicationConfig };
