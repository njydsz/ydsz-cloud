/**
 * jsdoc 配置模块
 *
 * @path conf\lint-configs\eslint-config\src\configs\jsdoc.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { Linter } from 'eslint';

import { interopDefault } from '../util';

/**
 * 启用 eslint-plugin-jsdoc，要求导出的函数/类具备规范的 JSDoc。
 *
 * 以 warn 级别校验 @param / @returns / @property 等标签的存在与命名，
 * 配合本仓库的注释规约推动文档完整性。
 *
 * @returns ESLint flat 配置数组
 */
export async function jsdoc(): Promise<Linter.Config[]> {
  const [pluginJsdoc] = await Promise.all([
    interopDefault(import('eslint-plugin-jsdoc')),
  ] as const);

  return [
    {
      plugins: {
        jsdoc: pluginJsdoc,
      },
      rules: {
        'jsdoc/check-access': 'warn',
        'jsdoc/check-param-names': 'warn',
        'jsdoc/check-property-names': 'warn',
        'jsdoc/check-types': 'warn',
        'jsdoc/empty-tags': 'warn',
        'jsdoc/implements-on-classes': 'warn',
        'jsdoc/no-defaults': 'warn',
        'jsdoc/no-multi-asterisks': 'warn',
        'jsdoc/require-param-name': 'warn',
        'jsdoc/require-property': 'warn',
        'jsdoc/require-property-description': 'warn',
        'jsdoc/require-property-name': 'warn',
        'jsdoc/require-returns-check': 'warn',
        'jsdoc/require-returns-description': 'warn',
        'jsdoc/require-yields-check': 'warn',
      },
    },
  ];
}
