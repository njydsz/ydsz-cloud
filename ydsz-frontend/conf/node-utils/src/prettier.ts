/**
 * prettier 配置模块
 *
 * @path conf\node-utils\src\prettier.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import fs from 'node:fs/promises';

import { format, getFileInfo, resolveConfig } from 'prettier';

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
