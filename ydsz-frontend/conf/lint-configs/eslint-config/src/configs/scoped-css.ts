/**
 * vue-scoped-css 配置模块
 *
 * @path conf\lint-configs\eslint-config\src\configs\scoped-css.ts
 * @author ydsz-team
 * @since 1.1.0
 *
 * @remarks
 * 强制所有 Vue 单文件组件使用 scoped 样式，杜绝全局样式污染。
 * 微前端多应用共存场景下，样式隔离是第一优先级（对标大厂规范）。
 */
import type { Linter } from 'eslint';

import { interopDefault } from '../util';

/**
 * 启用 eslint-plugin-vue-scoped-css 核心规则。
 *
 * - `enforce-style-type`: 强制 `<style>` 必须带 `scoped` 或 `module` 属性
 *   （公共包/全局样式文件通过 ignore 名单豁免）
 *
 * @returns ESLint flat 配置数组
 */
export async function scopedCss(): Promise<Linter.Config[]> {
  const plugin = await interopDefault(import('eslint-plugin-vue-scoped-css'));

  const recommended = plugin.configs?.['flat/recommended'] || [];

  return [
    ...recommended,
    {
      files: ['**/*.vue'],
      plugins: {
        'vue-scoped-css': plugin,
      },
      rules: {
        // 强制 style 带 scoped/module
        'vue-scoped-css/enforce-style-type': [
          'warn',
          {
            allows: ['scoped', 'module'],
          },
        ],
        'vue-scoped-css/no-unused-selector': 'warn',
        'vue-scoped-css/require-selector-attribute': 'off',
      },
    },
    // 全局样式文件豁免：仅允许这些目录下的 style 不带 scoped
    {
      files: ['comm/styles/**/**', '**/styles/**/**', '**/assets/**/**'],
      rules: {
        'vue-scoped-css/enforce-style-type': 'off',
      },
    },
  ];
}
