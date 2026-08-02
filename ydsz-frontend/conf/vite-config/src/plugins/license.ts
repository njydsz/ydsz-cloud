/**
 * license 配置模块
 *
 * @path conf\vite-config\src\plugins\license.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type {
  NormalizedOutputOptions,
  OutputBundle,
  OutputChunk,
} from 'rollup';
import type { PluginOption } from 'vite';

import { EOL } from 'node:os';

import { dateUtil, readPackageJSON } from '@ydsz/node-utils';

/**
 * 在构建产物的入口 chunk 顶部注入版权头信息。
 *
 * 仅对入口 chunk（isEntry）追加 MIT 版权声明，包含版本、作者、构建日期等，
 * 既满足开源协议要求，也便于产物溯源。
 *
 * @param root - 项目根目录，默认 process.cwd()
 * @returns Vite 插件对象
 */
async function viteLicensePlugin(
  root = process.cwd(),
): Promise<PluginOption | undefined> {
  const {
    description = '',
    homepage = '',
    version = '',
  } = await readPackageJSON(root);

  return {
    apply: 'build',
    enforce: 'post',
    generateBundle: {
      handler: (_options: NormalizedOutputOptions, bundle: OutputBundle) => {
        const date = dateUtil().format('YYYY-MM-DD ');
        const copyrightText = `/*!
  * YDSZ Admin
  * Version: ${version}
  * Author: ydsz
  * Copyright (C) 2024 YDSZ
  * License: MIT License
  * Description: ${description}
  * Date Created: ${date}
  * Homepage: ${homepage}
  * Contact: ydsz-pmis-team@njydsz.com
*/
              `.trim();

        for (const [, fileContent] of Object.entries(bundle)) {
          if (fileContent.type === 'chunk' && fileContent.isEntry) {
            const chunkContent = fileContent as OutputChunk;
            // 插入版权信息
            const content = chunkContent.code;
            const updatedContent = `${copyrightText}${EOL}${content}`;

            // 更新bundle
            (fileContent as OutputChunk).code = updatedContent;
          }
        }
      },
      order: 'post',
    },
    name: 'vite:license',
  };
}

export { viteLicensePlugin };
