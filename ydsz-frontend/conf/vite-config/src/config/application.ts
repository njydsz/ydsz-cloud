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

import { ALL_SHARED_DEPS, getSharedDeps, isValidStrategy, type ShareStrategy } from '../micro-shared-deps';
import { getDefaultPwaOptions } from '../options';
import { loadApplicationPlugins } from '../plugins';
import { microScopedPostcssPlugin } from '../plugins/micro-scoped-postcss';
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

    // v4.0 P1-1: 读取子应用 package.json 中的共享策略声明，选择对应 importmap 依赖集
    const appName = readSubAppName();
    const shareStrategy = readSubAppShareStrategy();
    const sharedDeps = [...getSharedDeps(shareStrategy)];
    console.info(`[ViteConfig] ImportMap strategy for ${appName || 'unknown'}: ${shareStrategy} (${sharedDeps.length} deps)`);

    const plugins = await loadApplicationPlugins({
      archiver: env.VITE_ARCHIVER === 'true',
      archiverPluginOptions: {},
      compress: true,
      compressTypes: ['brotli', 'gzip'],
      devtools: true,
      env,
      extraAppConfig: true,
      font: true, // 启用字体子集化（中文字体优化）
      html: true,
      i18n: true,
      importmapOptions: {
        // v3.2: 默认启用自托管 vendor，消除公网 CDN SPOF 风险
        // 需先运行 `pnpm sync:shared-deps` 将 ESM 产物下载到 public/vendor/
        // 可通过 VITE_IMPORTMAP_SELF_HOST=false 回退到 CDN 模式
        ...(env.VITE_IMPORTMAP_SELF_HOST === 'false'
          ? {
              defaultProvider: 'esm.sh',
            }
          : {
              selfHostBase: env.VITE_IMPORTMAP_SELF_HOST || '/vendor',
            }),
        importmap: sharedDeps,
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
      // v3.3: PWA 离线缓存默认启用，提升二次访问速度
      // 已配置合理的缓存策略：API 使用 NetworkFirst，静态资源使用 CacheFirst
      // 如需关闭，可在应用层设置 application.pwa = false
      pwa: true,
      pwaOptions: getDefaultPwaOptions(appTitle),
      vxeTableLazyImport: true,
      ...envConfig,
      ...application,
    });

    const { injectGlobalScss = true } = application;
    const subAppName = readSubAppName();

    // === micro-kernel manifest 插件：自动注入，子应用无需手工引入 ===
    if (isBuild && subAppName) {
      try {
        const { viteManifestPlugin } = await import('@ydsz/micro-kernel');
        plugins.push(viteManifestPlugin({ name: subAppName }));
        console.info(`[ViteConfig] Manifest plugin injected for ${subAppName}`);
      } catch {
        // micro-kernel 不可用时跳过
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
            // v3.7.0 (P2-3): manualChunks — 将未外部化的第三方依赖拆分为独立缓存块
            // 注意：vue/vue-router/pinia/element-plus/vxe-table 等已通过 importmap 外部化，
            // 不会进入 chunk 拆分逻辑；此处针对 @vueuse/echarts/lodash-es 等未外置依赖
            manualChunks: createManualChunks(),
          },
        },
        // v3.1: VITE_MONITOR_SOURCEMAP=true 时生成 hidden sourcemap（不引用到 HTML），
        // 供 upload-sourcemaps 脚本上传到后端做 stack trace 符号化，上传后从产物删除。
        // v4.0 P0-3: common.ts 中已默认在生产环境启用 hidden sourcemap（用于 Sentry 符号化）；
        //       此处仅当 VITE_MONITOR_SOURCEMAP 显式设为 false 时关闭。
        sourcemap: env.VITE_MONITOR_SOURCEMAP === 'false' ? false : (process.env.NODE_ENV === 'production' ? 'hidden' : false),
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
 * 构造 SCSS 预处理器选项，并按需注入 micro-kernel CSS 作用域插件。
 *
 * - 仅对 apps 下的包注入 `@ydsz/styles/global` SCSS 全局样式
 * - build 模式下，对子应用注入 PostCSS prefix 插件（[data-micro-app="xxx"]），
 *   与 micro-kernel 的容器属性约定联动，实现构建期样式隔离
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

  // === micro-kernel CSS 作用域：有 appName 时启用 ===
  if (appName) {
    result.postcss = {
      plugins: [microScopedPostcssPlugin({ appName })],
    };
    console.info(`[ViteConfig] CSS scoping enabled for ${appName}`);
  }

  return result;
}

export { defineApplicationConfig };

/**
 * 生成 Rollup manualChunks 函数，将未外部化的第三方依赖拆分为独立缓存块。
 *
 * importmap 外部化的依赖（vue/vue-router/pinia/element-plus/vxe-table 等）不会进入
 * 构建产物，因此无需在此重复声明。本函数主要处理未外置的大型库：
 * - @vueuse/*: 工具函数集合，体积大且变动频率与业务代码不同
 * - echarts: 图表库（仅主子应用引用时进入 bundle）
 * - lodash-es / lodash: 工具库
 * - async-validator: 表单校验（element-plus 依赖，但可能被业务直接引用）
 * - @ctrl/*: tinycolor2 / @popperjs 等小型 UI 底座
 * - 其他 node_modules 统一归入 vendor 块
 *
 * 分包收益：
 * 1. 浏览器可独立缓存频繁变动的业务 chunk 与稳定的 vendor chunk
 * 2. 多个子应用共享同一份 vendor chunk 时（hash 一致）命中缓存概率更高
 *
 * @returns Rollup manualChunks 函数
 */
function createManualChunks(): (id: string) => string | undefined {
  return (id: string): string | undefined => {
    // 只处理 node_modules 中的依赖
    if (!id.includes('node_modules')) return undefined;

    // 规范化路径：id 可能是绝对路径或 "prefix:" 开头的 URL
    // 提取包名（scope 包取 scope/name，普通包取 name）
    const nmIndex = id.indexOf('node_modules');
    const subpath = id.slice(nmIndex + 'node_modules/'.length);
    const segments = subpath.split('/');
    const isScope = segments[0]?.startsWith('@');
    const pkgName = isScope ? `${segments[0]}/${segments[1]}` : segments[0];

    // 按包名分组
    switch (pkgName) {
      case '@vueuse/core':
      case '@vueuse/shared':
        return 'vendor-vueuse';
      case 'echarts':
      case 'echarts/core':
        return 'vendor-echarts';
      case 'lodash-es':
      case 'lodash':
        return 'vendor-lodash';
      case 'async-validator':
        return 'vendor-async-validator';
      case '@ctrl/tinycolor':
      case '@popperjs/core':
        return 'vendor-ui-base';
      default:
        // 其他所有 node_modules 依赖统一归入 vendor
        return 'vendor';
    }
  };
}

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

/**
 * 读取子应用 package.json 中声明的 importmap 共享策略（v4.0 P1-1）。
 *
 * 取值优先级：
 * 1. package.json 中 `ydsz.shareStrategy` 字段
 * 2. 默认 'all'（全量外置，保持向后兼容）
 *
 * 无效值会警告并回退到 'all'。
 */
function readSubAppShareStrategy(): ShareStrategy {
  try {
    const pkgContent = readFileSync(
      path.join(process.cwd(), 'package.json'),
      'utf-8',
    );
    const pkg = JSON.parse(pkgContent);
    const strategy = pkg?.ydsz?.shareStrategy;
    if (isValidStrategy(strategy)) {
      return strategy;
    }
    if (strategy !== undefined) {
      console.warn(
        `[ViteConfig] Invalid shareStrategy "${strategy}" in package.json; ` +
        `expected one of ${getAvailableStrategies().join(', ')}. Falling back to 'all'.`,
      );
    }
  } catch {
    // package.json 不存在时静默
  }
  return 'all';
}
