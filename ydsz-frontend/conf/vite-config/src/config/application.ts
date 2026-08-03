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
import { readFileSync } from 'node:fs';

import { findMonorepoRoot } from '@ydsz/node-utils';

import { NodePackageImporter } from 'sass';
import { defineConfig, loadEnv, mergeConfig } from 'vite';

import { ALL_SHARED_DEPS } from '../micro-shared-deps';
import { getDefaultPwaOptions } from '../options';
import { loadApplicationPlugins } from '../plugins';
import { postcssLiteScopedPlugin } from '../plugins/postcss-lite-scoped';
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
      importmapOptions: {
        defaultProvider: 'esm.sh',
        importmap: [...ALL_SHARED_DEPS],
      },
      injectAppLoading: true,
      injectMetadata: true,
      isBuild,
      license: true,
      mode,
      print: !isBuild,
      printInfoMap: {
        'YDSZ Admin Docs': 'https://docs.njydsz.com.cn',
      },
      // 中后台管理端 PWA 价值低且有缓存脏数据风险，关闭
      pwa: false,
      pwaOptions: getDefaultPwaOptions(appTitle),
      vxeTableLazyImport: true,
      ...envConfig,
      ...application,
    });

    const { injectGlobalScss = true } = application;
    const subAppName = readSubAppName();

    // === lite-kernel manifest 插件：自动注入，子应用无需手工引入 ===
    if (isBuild && subAppName) {
      try {
        const { viteManifestPlugin } = await import('@ydsz/micro-kernel-lite');
        plugins.push(viteManifestPlugin({ name: subAppName }));
        console.info(`[ViteConfig] Manifest plugin injected for ${subAppName}`);
      } catch {
        // micro-kernel-lite 不可用时跳过（qiankun 模式无需 manifest）
      }
    }
    const { build: buildConf } = vite;

    const applicationConfig: UserConfig = {
      base,
      build: {
        rollupOptions: {
          output: {
            assetFileNames: '[ext]/[name]-[hash].[ext]',
            chunkFileNames: 'js/[name]-[hash].js',
            entryFileNames: 'jse/index-[name]-[hash].js',
          },
        },
        chunkSizeWarningLimit: 1000,
        target: 'es2022',
      },
      css: createCssOptions(injectGlobalScss, readSubAppName()),
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
 * 构造 SCSS 预处理器选项，并按需注入 lite-kernel CSS 作用域插件。
 *
 * - 仅对 apps 下的包注入 `@ydsz/styles/global` SCSS 全局样式
 * - build 模式下，对子应用注入 PostCSS prefix 插件（[data-lite-app="xxx"]），
 *   与 lite-kernel 的容器属性约定联动，实现构建期样式隔离
 *
 * @param injectGlobalScss - 是否注入全局 SCSS，默认 true
 * @param appName - 子应用名（如 'project-web'），build 模式下用于 CSS 作用域
 * @returns Vite CSS 配置对象
 */
function createCssOptions(injectGlobalScss = true, appName?: string): CSSOptions {
  const root = findMonorepoRoot();

  const result: CSSOptions = {
    preprocessorOptions: injectGlobalScss
      ? {
          scss: {
            additionalData: (content: string, filepath: string) => {
              const relativePath = relative(root, filepath);
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

  // === lite-kernel CSS 作用域：有 appName 时启用 ===
  if (appName) {
    result.postcss = {
      plugins: [postcssLiteScopedPlugin({ appName })],
    };
    console.info(`[ViteConfig] CSS scoping enabled for ${appName}`);
  }

  return result;
}

export { defineApplicationConfig };

/**
 * 从当前工作目录的 package.json 读取子应用名。
 *
 * 仅在 apps/ 或 main 目录下有效；库包（comm/、conf/）返回 undefined。
 */
function readSubAppName(): string | undefined {
  try {
    const pkgContent = readFileSync(
      path.join(process.cwd(), 'package.json'),
      'utf-8',
    );
    const pkg = JSON.parse(pkgContent);
    const name: string = pkg.name || '';
    if (name.startsWith('@ydsz/') && name.endsWith('-web')) {
      return name.replace('@ydsz/', '');
    }
    if (name === '@ydsz/main-web') return 'main-web';
  } catch {
    // package.json 不存在时静默
  }
  return undefined;
}
