/**
 * 参考 https://github.com/jspm/vite-plugin-jspm，调整为需要的功能
 *
 * v3.1 新增 selfHostBase 选项：当指定时，跳过 jspm generator 的公网 CDN 解析，
 * 改为生成指向同源 `/vendor/<pkg>@<version>/` 路径的 importmap，消除公网 CDN SPOF。
 * 配合 `bash/sync-shared-deps.mjs` 将 ESM 产物预下载到 public/vendor/ 即可自托管。
 *
 * 自托管 importmap 来源优先级：
 *   1. `public/vendor/importmap.json`（sync 脚本生成，含完整传递依赖与 scopes）
 *   2. 简易路径映射 `buildSelfHostedImportMap`（仅顶层依赖，适合快速验证）
 */
import type { GeneratorOptions } from '@jspm/generator';
import type { Plugin } from 'vite';

import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';

import { Generator } from '@jspm/generator';
import { load } from 'cheerio';
import { minify } from 'html-minifier-terser';

/** 默认 CDN 供应商，未显式指定时使用 */
const DEFAULT_PROVIDER = 'jspm.io';

/** importmap 插件选项：在 jspm GeneratorOptions 基础上扩展依赖列表与供应商 */
type pluginOptions = GeneratorOptions & {
  debug?: boolean;
  defaultProvider?: 'esm.sh' | 'jsdelivr' | 'jspm.io';
  importmap?: Array<{ name: string; range?: string }>;
  /**
   * 自托管基路径（如 `/vendor`）。指定后：
   * - 跳过 jspm generator 公网解析
   * - importmap 各项指向 `${selfHostBase}/<pkg>@<range>/index.js`
   * - 需配合 `bash/sync-shared-deps.mjs` 预下载 ESM 产物到 public/vendor/
   * - es-module-shims 也从同源 `/vendor/es-module-shims/` 加载
   */
  selfHostBase?: string;
};

// async function getLatestVersionOfShims() {
//   const result = await fetch('https://ga.jspm.io/npm:es-module-shims');
//   const version = result.text();
//   return version;
// }

/**
 * 根据 CDN 供应商返回 es-module-shims 垫片的 URL。
 *
 * 版本固定为 1.10.0（升级需人工验证兼容性），未知供应商回退到默认 jspm.io。
 * 当 selfHostBase 指定时，回退到同源 `${selfHostBase}/es-module-shims@<version>/dist/es-module-shims.js`，
 * 消除垫片脚本的公网 CDN 依赖。
 *
 * @param provide - CDN 供应商名（esm.sh / jsdelivr / jspm.io）
 * @param selfHostBase - 自托管基路径，指定时覆盖 CDN 解析
 * @returns 对应供应商的 es-module-shims CDN 地址
 */
async function getShimsUrl(provide: string, selfHostBase?: string) {
  // 版本固定锁定，避免 CDN 升级引入破坏性变更
  const version = '1.10.0';
  const shimsSubpath = `dist/es-module-shims.js`;

  // 自托管模式：垫片也从同源加载
  if (selfHostBase) {
    return `${selfHostBase}/es-module-shims@${version}/${shimsSubpath}`;
  }

  const providerShimsMap: Record<string, string> = {
    'esm.sh': `https://esm.sh/es-module-shims@${version}/${shimsSubpath}`,
    jsdelivr: `https://cdn.jsdelivr.net/npm/es-module-shims@${version}/${shimsSubpath}`,
    'jspm.io': `https://ga.jspm.io/npm:es-module-shims@${version}/${shimsSubpath}`,
  };

  return providerShimsMap[provide] || providerShimsMap[DEFAULT_PROVIDER];
}

/**
 * 生成自托管 importmap JSON。
 *
 * 将每个依赖映射到 `${selfHostBase}/<pkg>@<range>/index.js`，
 * range 中的 `^` 等前缀符号会被剥离（目录名不可含特殊字符）。
 * 实际文件由 `bash/sync-shared-deps.mjs` 从 esm.sh 下载并写入 public/vendor/。
 *
 * @param deps - 共享依赖列表
 * @param selfHostBase - 自托管基路径
 * @returns importmap JSON 对象（{ imports: { ... } }）
 */
function buildSelfHostedImportMap(
  deps: Array<{ name: string; range?: string }>,
  selfHostBase: string,
): { imports: Record<string, string> } {
  const imports: Record<string, string> = {};
  for (const dep of deps) {
    const cleanRange = (dep.range || '').replace(/[\^~>=<]/g, '');
    imports[dep.name] = `${selfHostBase}/${dep.name}@${cleanRange}/index.js`;
  }
  return { imports };
}

let generator: Generator;

/**
 * 通过 CDN 以 importmap 方式加载指定依赖的 Vite 插件（参考 vite-plugin-jspm 改造）。
 *
 * 在构建阶段将声明的依赖通过 jspm generator 安装为 external，并在 HTML 注入
 * importmap 与 es-module-shims 垫片，使其走 CDN 加载；非构建或 SSR 下不生效。
 *
 * @param pluginOptions - 插件选项（CDN 供应商、依赖列表、调试开关）
 * @returns 由 external / install / html 三段组成的 Vite 插件数组
 */
async function viteImportMapPlugin(
  pluginOptions?: pluginOptions,
): Promise<Plugin[]> {
  const { importmap, selfHostBase } = pluginOptions || {};

  let isSSR = false;
  let isBuild = false;
  let installed = false;
  let installError: Error | null = null;

  const options: pluginOptions = Object.assign(
    {},
    {
      debug: false,
      defaultProvider: 'jspm.io',
      env: ['production', 'browser', 'module'],
      importmap: [],
    },
    pluginOptions,
  );

  // 自托管模式：优先读取 sync 脚本预生成的 importmap.json（含完整传递依赖），
  // 不存在时回退到简易路径映射（仅顶层依赖，用于快速验证）
  let selfHostedImportMap: { imports: Record<string, string> } | null = null;
  if (selfHostBase) {
    const importmapFile = path.join(process.cwd(), 'public', 'vendor', 'importmap.json');
    if (existsSync(importmapFile)) {
      try {
        selfHostedImportMap = JSON.parse(readFileSync(importmapFile, 'utf-8'));
        console.info(`[ImportMap] 使用预生成 importmap: ${importmapFile}`);
      } catch {
        selfHostedImportMap = buildSelfHostedImportMap(importmap || [], selfHostBase);
        console.warn(`[ImportMap] importmap.json 解析失败，回退到简易映射`);
      }
    } else {
      selfHostedImportMap = buildSelfHostedImportMap(importmap || [], selfHostBase);
      console.warn(
        `[ImportMap] 未找到 ${importmapFile}，使用简易映射。运行 \`pnpm sync:shared-deps\` 获取完整依赖图。`,
      );
    }
  }

  if (!selfHostBase) {
    generator = new Generator({
      ...options,
      baseUrl: process.cwd(),
    });

    if (options?.debug) {
      (async () => {
        for await (const { message, type } of generator.logStream()) {
          console.log(`${type}: ${message}`);
        }
      })();
    }
  }

  const imports = options.inputMap?.imports ?? {};
  const scopes = options.inputMap?.scopes ?? {};
  const firstLayerKeys = Object.keys(scopes);
  const inputMapScopes: string[] = [];
  firstLayerKeys.forEach((key) => {
    inputMapScopes.push(...Object.keys(scopes[key] || {}));
  });
  const inputMapImports = Object.keys(imports);

  const allDepNames: string[] = [
    ...(importmap?.map((item) => item.name) || []),
    ...inputMapImports,
    ...inputMapScopes,
  ];
  const depNames = new Set<string>(allDepNames);

  const installDeps = importmap?.map((item) => ({
    range: item.range,
    target: item.name,
  }));

  return [
    {
      async config(_, { command, isSsrBuild }) {
        isBuild = command === 'build';
        isSSR = !!isSsrBuild;
      },
      enforce: 'pre',
      name: 'importmap:external',
      resolveId(id) {
        if (isSSR || !isBuild) {
          return null;
        }

        if (!depNames.has(id)) {
          return null;
        }
        return { external: true, id };
      },
    },
    {
      enforce: 'post',
      name: 'importmap:install',
      async resolveId() {
        if (isSSR || !isBuild || installed) {
          return null;
        }
        // 自托管模式无需公网安装
        if (selfHostBase) {
          installed = true;
          console.info(
            `[ImportMap] Self-hosted mode → ${selfHostBase} (${importmap?.length ?? 0} deps). Run \`pnpm sync:shared-deps\` to populate.`,
          );
          return null;
        }
        try {
          installed = true;
          await Promise.allSettled(
            (installDeps || []).map((dep) => generator.install(dep)),
          );
        } catch (error: any) {
          installError = error;
          installed = false;
        }
        return null;
      },
    },
    {
      buildEnd() {
        // 未生成importmap时，抛出错误，防止被turbo缓存
        if (!installed && !isSSR) {
          installError && console.error(installError);
          throw new Error('Importmap installation failed.');
        }
      },
      enforce: 'post',
      name: 'importmap:html',
      transformIndexHtml: {
        async handler(html) {
          if (isSSR || !isBuild) {
            return html;
          }

          const importmapJson = selfHostBase
            ? selfHostedImportMap
            : generator.getMap();

          if (!importmapJson) {
            return html;
          }

          const esModuleShimsSrc = await getShimsUrl(
            options.defaultProvider || DEFAULT_PROVIDER,
            selfHostBase,
          );

          const resultHtml = await injectShimsToHtml(
            html,
            esModuleShimsSrc || '',
          );
          html = await minify(resultHtml || html, {
            collapseWhitespace: true,
            minifyCSS: true,
            minifyJS: true,
            removeComments: false,
          });

          return {
            html,
            tags: [
              {
                attrs: {
                  type: 'importmap',
                },
                injectTo: 'head-prepend',
                tag: 'script',
                children: `${JSON.stringify(importmapJson)}`,
              },
            ],
          };
        },
        order: 'post',
      },
    },
  ];
}

/**
 * 将入口模块改写为经 es-module-shims 垫片加载，兼容不支持 importmap 的浏览器。
 *
 * 通过 cheerio 解析 HTML，移除原生 module 脚本属性后以 importShim 代理方式
 * 加载入口，保证老旧浏览器也能使用 importmap。
 *
 * @param html - 原始 HTML 字符串
 * @param esModuleShimUrl - es-module-shims 垫片脚本地址
 * @returns 注入垫片加载逻辑后的 HTML 字符串
 */
async function injectShimsToHtml(html: string, esModuleShimUrl: string) {
  const $ = load(html);

  const $script = $(`script[type='module']`);

  if (!$script) {
    return;
  }

  const entry = $script.attr('src');

  $script.removeAttr('type');
  $script.removeAttr('crossorigin');
  $script.removeAttr('src');
  $script.html(`
if (!HTMLScriptElement.supports || !HTMLScriptElement.supports('importmap')) {
  self.importShim = function () {
      const promise = new Promise((resolve, reject) => {
          document.head.appendChild(
              Object.assign(document.createElement('script'), {
                  src: '${esModuleShimUrl}',
                  crossorigin: 'anonymous',
                  async: true,
                  onload() {
                      if (!importShim.$proxy) {
                          resolve(importShim);
                      } else {
                          reject(new Error('No globalThis.importShim found:' + esModuleShimUrl));
                      }
                  },
                  onerror(error) {
                      reject(error);
                  },
              }),
          );
      });
      importShim.$proxy = true;
      return promise.then((importShim) => importShim(...arguments));
  };
}

var modules = ['${entry}'];
typeof importShim === 'function'
  ? modules.forEach((moduleName) => importShim(moduleName))
  : modules.forEach((moduleName) => import(moduleName));
 `);
  $('body').after($script);
  $('head').remove(`script[type='module']`);
  return $.html();
}

export { viteImportMapPlugin };
