/**
 * prettier 配置模块
 *
 * @path conf\node-utils\src\prettier.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import fs from 'node:fs/promises';

import { format, getFileInfo, resolveConfig } from 'prettier';

/**
 * 按项目 prettier 配置格式化指定文件并就地写回。
 *
 * 先解析文件路径对应的 prettier 配置与推断的 parser，
 * 仅当格式化结果与原内容不一致时才写回，避免无谓的文件变更与 mtime 更新。
 *
 * @param filepath - 待格式化的文件绝对或相对路径
 * @returns 格式化后的文本内容
 */
async function prettierFormat(filepath: string) {
  const prettierOptions = await resolveConfig(filepath, {});

  const fileInfo = await getFileInfo(filepath);

  const input = await fs.readFile(filepath, 'utf8');
  const output = await format(input, {
    ...prettierOptions,
    parser: fileInfo.inferredParser as any,
  });
  if (output !== input) {
    await fs.writeFile(filepath, output, 'utf8');
  }
  return output;
}

export { prettierFormat };
