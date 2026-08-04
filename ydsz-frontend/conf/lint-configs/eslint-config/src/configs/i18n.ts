/**
 * vue-i18n 配置模块
 *
 * @path conf\lint-configs\eslint-config\src\configs\i18n.ts
 * @author ydsz-team
 * @since 1.1.0
 *
 * @remarks
 * 业务文案国际化治理：拦截 Vue 模板/脚本中的硬编码中文字符串，
 * 强制使用 $t() 调用。当前以 warn 级别渐进治理（对标蚂蚁 eslint-config-ali）。
 */
import type { Linter } from 'eslint';

import { interopDefault } from '../util';

/**
 * 启用 eslint-plugin-vue-i18n 的 no-raw-text 规则。
 *
 * @returns ESLint flat 配置数组
 */
export async function i18n(): Promise<Linter.Config[]> {
  const plugin = await interopDefault(import('eslint-plugin-vue-i18n'));

  const recommended = plugin.configs?.['flat/recommended'] || [];

  return [
    ...recommended,
    {
      files: ['**/*.vue', '**/*.ts', '**/*.tsx'],
      plugins: {
        'vue-i18n': plugin,
      },
      rules: {
        // 禁止模板中的硬编码文本（中文场景重点治理，warn 渐进）
        'vue-i18n/no-raw-text': [
          'warn',
          {
            ignoreNodes: ['v-html'],
            ignoreText: ['-', '…', '/', '×', '·'],
            ignorePattern: '^\\d+(\\.\\d+)?%?$',
          },
        ],
        'vue-i18n/no-missing-keys': 'off',
        'vue-i18n/no-unused-keys': 'off',
        'vue-i18n/no-html-messages': 'warn',
      },
    },
    // 公共基础包 / 配置文件豁免（无 i18n 上下文）
    {
      files: [
        'conf/**/**',
        'bash/**/**',
        'docs/**/**',
        '**/__tests__/**',
        '**/mock/**',
      ],
      rules: {
        'vue-i18n/no-raw-text': 'off',
      },
    },
  ];
}
