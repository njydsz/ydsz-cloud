/**
 * print 配置模块
 *
 * @path conf\vite-config\src\plugins\print.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { PluginOption } from 'vite';

import type { PrintPluginOptions } from '../typing';

import { colors } from '@ydsz/node-utils';

/**
 * 在开发服务器启动时打印自定义信息（如文档地址）的 Vite 插件。
 *
 * 包裹 server.printUrls，在默认地址之后额外输出 infoMap 中的键值对，
 * 方便本地开发时快速获取相关链接。
 *
 * @param options - 打印配置（infoMap 自定义键值对）
 * @returns Vite 插件对象
 */
export const vitePrintPlugin = (
  options: PrintPluginOptions = {},
): PluginOption => {
  const { infoMap = {} } = options;

  return {
    configureServer(server) {
      const _printUrls = server.printUrls;
      server.printUrls = () => {
        _printUrls();

        for (const [key, value] of Object.entries(infoMap)) {
          console.log(
            `  ${colors.green('➜')}  ${colors.bold(key)}: ${colors.cyan(value)}`,
          );
        }
      };
    },
    enforce: 'pre',
    name: 'vite:print-info',
  };
};
