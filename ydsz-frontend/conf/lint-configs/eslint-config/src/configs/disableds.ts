/**
 * disableds 配置模块
 *
 * @path conf\lint-configs\eslint-config\src\configs\disableds.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { Linter } from 'eslint';

/**
 * 在测试、类型声明与 JS 文件中按需关闭部分规则以减少噪音。
 *
 * 例如测试文件允许 console、d.ts 放宽三斜线指令限制，避免无意义报错。
 *
 * @returns ESLint flat 配置数组
 */
export async function disableds(): Promise<Linter.Config[]> {
  return [
    {
      files: ['**/__tests__/**/*.?([cm])[jt]s?(x)'],
      name: 'disables/test',
      rules: {
        '@typescript-eslint/ban-ts-comment': 'off',
        'no-console': 'off',
      },
    },
    {
      files: ['**/*.d.ts'],
      name: 'disables/dts',
      rules: {
        '@typescript-eslint/triple-slash-reference': 'off',
      },
    },
    {
      files: ['**/*.js', '**/*.mjs', '**/*.cjs'],
      name: 'disables/js',
      rules: {
        '@typescript-eslint/explicit-module-boundary-types': 'off',
      },
    },
  ];
}
