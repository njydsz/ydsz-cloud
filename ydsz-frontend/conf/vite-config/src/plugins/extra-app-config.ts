/**
 * extra-app-config 配置模块
 *
 * @path conf\vite-config\src\plugins\extra-app-config.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { PluginOption } from 'vite';

import {
  colors,
  generatorContentHash,
  readPackageJSON,
} from '@ydsz/node-utils';

import { loadEnv } from '../utils/env';

/** 抽离应用配置插件所需的运行参数 */
interface PluginOptions {
  /** 是否为构建模式（仅构建时抽离配置） */
  isBuild: boolean;
  /** 项目根目录，用于读取 package.json */
  root: string;
}

/** 抽离出的全局配置文件名，注入到 HTML 后由运行时加载 */
const GLOBAL_CONFIG_FILE_NAME = '_app.config.js';
/** 挂载到 window 上的应用配置全局变量名，值经 freeze 防止被篡改 */
const YDSZ_ADMIN_PRO_APP_CONF = '_YDSZ_ADMIN_PRO_APP_CONF_';

/**
 * 将应用配置从打包产物中抽离为独立文件，并在 HTML 中按需注入。
 *
 * 仅构建阶段生效：把以 VITE_ 前缀的环境变量写入 `_app.config.js` 并挂载到
 * window 全局变量（freeze 防篡改），HTML 通过带版本 hash 的 script 标签加载，
 * 从而实现不重新打包即可调整运行期配置。
 *
 * @param options - 插件运行参数（是否构建、项目根目录）
 * @returns Vite 插件对象；非构建阶段返回 undefined
 */
async function viteExtraAppConfigPlugin({
  isBuild,
  root,
}: PluginOptions): Promise<PluginOption | undefined> {
  let publicPath: string;
  let source: string;

  if (!isBuild) {
    return;
  }

  const { version = '' } = await readPackageJSON(root);

  return {
    async configResolved(config) {
      publicPath = ensureTrailingSlash(config.base);
      source = await getConfigSource();
    },
    async generateBundle() {
      try {
        this.emitFile({
          fileName: GLOBAL_CONFIG_FILE_NAME,
          source,
          type: 'asset',
        });

        console.log(colors.cyan(`✨configuration file is build successfully!`));
      } catch (error) {
        console.log(
          colors.red(
            `configuration file configuration file failed to package:\n${error}`,
          ),
        );
      }
    },
    name: 'vite:extra-app-config',
    async transformIndexHtml(html) {
      const hash = `v=${version}-${generatorContentHash(source, 8)}`;

      const appConfigSrc = `${publicPath}${GLOBAL_CONFIG_FILE_NAME}?${hash}`;

      return {
        html,
        tags: [{ attrs: { src: appConfigSrc }, tag: 'script' }],
      };
    },
  };
}

/**
 * 读取环境变量并拼装为挂载到 window 的冻结配置脚本片段。
 *
 * 通过 Object.freeze / defineProperty 阻止运行时改写配置，提升安全性。
 *
 * @returns 可直接注入 HTML 的配置脚本字符串
 */
async function getConfigSource() {
  const config = await loadEnv();
  const windowVariable = `window.${YDSZ_ADMIN_PRO_APP_CONF}`;
  // 确保变量不会被修改
  let source = `${windowVariable}=${JSON.stringify(config)};`;
  source += `
    Object.freeze(${windowVariable});
    Object.defineProperty(window, "${YDSZ_ADMIN_PRO_APP_CONF}", {
      configurable: false,
      writable: false,
    });
  `.replaceAll(/\s/g, '');
  return source;
}

function ensureTrailingSlash(path: string) {
  return path.endsWith('/') ? path : `${path}/`;
}

export { viteExtraAppConfigPlugin };
