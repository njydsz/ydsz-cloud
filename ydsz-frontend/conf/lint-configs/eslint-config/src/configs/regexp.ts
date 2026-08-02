/**
 * regexp 配置模块
 *
 * @path conf\lint-configs\eslint-config\src\configs\regexp.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { Linter } from 'eslint';

import { interopDefault } from '../util';

/**
 * 启用 eslint-plugin-regexp 并采用其推荐规则校验正则表达式。
 *
 * 检测冗余分组、低效写法等正则问题，提升表达式正确性与性能。
 *
 * @returns ESLint flat 配置数组
 */
export async function regexp(): Promise<Linter.Config[]> {
  const [pluginRegexp] = await Promise.all([
    interopDefault(import('eslint-plugin-regexp')),
  ] as const);

  return [
    {
      plugins: {
        regexp: pluginRegexp,
      },
      rules: {
        ...pluginRegexp.configs.recommended.rules,
      },
    },
  ];
}
