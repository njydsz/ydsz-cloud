/**
 * prettier 配置模块
 *
 * @path conf\lint-configs\eslint-config\src\configs\prettier.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { Linter } from 'eslint';

import { interopDefault } from '../util';

/**
 * 接入 prettier，将代码格式问题作为 ESLint 错误报告。
 *
 * 与 `prettier/` 规则联动，保证 lint 与格式化结果一致，避免风格分歧。
 *
 * @returns ESLint flat 配置数组
 */
export async function prettier(): Promise<Linter.Config[]> {
  const [pluginPrettier] = await Promise.all([
    interopDefault(import('eslint-plugin-prettier')),
  ] as const);
  return [
    {
      plugins: {
        prettier: pluginPrettier,
      },
      rules: {
        'prettier/prettier': 'error',
      },
    },
  ];
}
