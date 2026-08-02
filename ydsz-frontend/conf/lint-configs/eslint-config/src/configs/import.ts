/**
 * import 配置模块
 *
 * @path conf\lint-configs\eslint-config\src\configs\import.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { Linter } from 'eslint';

import * as pluginImport from 'eslint-plugin-import-x';

/**
 * 启用 import-x 插件，规范 ES 模块的导入行为。
 *
 * 约束导入顺序、去重、禁止自引用与 webpack-loader 语法，提升模块可读性。
 *
 * @returns ESLint flat 配置数组
 */
export async function importPluginConfig(): Promise<Linter.Config[]> {
  return [
    {
      plugins: {
        // @ts-expect-error - This is a dynamic import
        import: pluginImport,
      },
      rules: {
        'import/consistent-type-specifier-style': ['error', 'prefer-top-level'],
        'import/first': 'error',
        'import/newline-after-import': 'error',
        'import/no-duplicates': 'error',
        'import/no-mutable-exports': 'error',
        'import/no-named-default': 'error',
        'import/no-self-import': 'error',
        'import/no-unresolved': 'off',
        'import/no-webpack-loader-syntax': 'error',
      },
    },
  ];
}
