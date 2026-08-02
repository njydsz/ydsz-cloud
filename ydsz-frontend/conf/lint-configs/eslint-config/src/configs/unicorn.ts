/**
 * unicorn 配置模块
 *
 * @path conf\lint-configs\eslint-config\src\configs\unicorn.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { Linter } from 'eslint';

import { interopDefault } from '../util';

/**
 * 启用 eslint-plugin-unicorn 推荐规则，并关闭与本项目风格冲突的部分。
 *
 * 如关闭 filename-case / no-null / prevent-abbreviations 等，避免对现有约定过度干预；
 * 构建脚本（conf/bash）另放宽 no-process-exit。
 *
 * @returns ESLint flat 配置数组
 */
export async function unicorn(): Promise<Linter.Config[]> {
  const [pluginUnicorn] = await Promise.all([
    interopDefault(import('eslint-plugin-unicorn')),
  ] as const);

  return [
    {
      plugins: {
        unicorn: pluginUnicorn,
      },
      rules: {
        ...pluginUnicorn.configs.recommended.rules,

        'unicorn/better-regex': 'off',
        'unicorn/consistent-destructuring': 'off',
        'unicorn/consistent-function-scoping': 'off',
        'unicorn/expiring-todo-comments': 'off',
        'unicorn/filename-case': 'off',
        'unicorn/import-style': 'off',
        'unicorn/no-array-for-each': 'off',
        'unicorn/no-null': 'off',
        'unicorn/no-useless-undefined': 'off',
        'unicorn/prefer-at': 'off',
        'unicorn/prefer-dom-node-text-content': 'off',
        'unicorn/prefer-export-from': ['error', { ignoreUsedVariables: true }],
        'unicorn/prefer-global-this': 'off',
        'unicorn/prefer-top-level-await': 'off',
        'unicorn/prevent-abbreviations': 'off',
      },
    },
    {
      files: ['bash/**/*.?([cm])[jt]s?(x)', 'conf/**/*.?([cm])[jt]s?(x)'],
      rules: {
        'unicorn/no-process-exit': 'off',
      },
    },
  ];
}
