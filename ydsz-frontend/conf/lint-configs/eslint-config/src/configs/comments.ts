/**
 * comments 配置模块
 *
 * @path conf\lint-configs\eslint-config\src\configs\comments.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { Linter } from 'eslint';

import { interopDefault } from '../util';

/**
 * 启用 eslint-plugin-eslint-comments，约束 `eslint-disable` 等指令的规范使用。
 *
 * 防止出现聚合/重复/无限/未使用的 disable 指令，保持 lint 抑制可追溯。
 *
 * @returns ESLint flat 配置数组
 */
export async function comments(): Promise<Linter.Config[]> {
  const [pluginComments] = await Promise.all([
    // @ts-expect-error - no types
    interopDefault(import('eslint-plugin-eslint-comments')),
  ] as const);

  return [
    {
      plugins: {
        'eslint-comments': pluginComments,
      },
      rules: {
        'eslint-comments/no-aggregating-enable': 'error',
        'eslint-comments/no-duplicate-disable': 'error',
        'eslint-comments/no-unlimited-disable': 'error',
        'eslint-comments/no-unused-enable': 'error',
      },
    },
  ];
}
