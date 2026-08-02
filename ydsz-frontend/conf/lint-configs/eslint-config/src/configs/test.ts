/**
 * test 配置模块
 *
 * @path conf\lint-configs\eslint-config\src\configs\test.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { Linter } from 'eslint';

import { interopDefault } from '../util';

/**
 * 配置测试文件（vitest 等）的专属规则。
 *
 * 统一使用 `it`、禁止 `.only` 遗留、关闭 console 限制，适配测试场景。
 *
 * @returns ESLint flat 配置数组
 */
export async function test(): Promise<Linter.Config[]> {
  const [pluginTest, pluginNoOnlyTests] = await Promise.all([
    interopDefault(import('eslint-plugin-vitest')),
    // @ts-expect-error - no types
    interopDefault(import('eslint-plugin-no-only-tests')),
  ] as const);

  return [
    {
      files: [
        `**/__tests__/**/*.?([cm])[jt]s?(x)`,
        `**/*.spec.?([cm])[jt]s?(x)`,
        `**/*.test.?([cm])[jt]s?(x)`,
        `**/*.bench.?([cm])[jt]s?(x)`,
        `**/*.benchmark.?([cm])[jt]s?(x)`,
      ],
      plugins: {
        test: {
          ...pluginTest,
          rules: {
            ...pluginTest.rules,
            ...pluginNoOnlyTests.rules,
          },
        },
      },
      rules: {
        'no-console': 'off',
        'node/prefer-global/process': 'off',
        'test/consistent-test-it': [
          'error',
          { fn: 'it', withinDescribe: 'it' },
        ],
        'test/no-identical-title': 'error',
        'test/no-import-node-test': 'error',
        'test/no-only-tests': 'error',
        'test/prefer-hooks-in-order': 'error',
        'test/prefer-lowercase-title': 'error',
      },
    },
  ];
}
