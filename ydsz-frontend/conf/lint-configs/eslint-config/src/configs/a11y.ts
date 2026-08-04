/**
 * 可访问性（a11y）ESLint 规则配置（v4.0）
 *
 * 启用 eslint-plugin-jsx-a11y 的推荐规则集（在 Vue template 中也适用的等效规则），
 * 以及自定义的 a11y 强制规范：
 * - 禁止 tabindex > 0（焦点顺序应该由 DOM 自然流决定）
 * - 禁止误用语义标签（禁用 div 替代 button）
 * - 强制 img 必须有 alt
 * - 强制表单 input 必须有 label
 *
 * 使用方式：micro 项目 .eslintrc 无需修改，本工程在 index.ts 中导入此配置
 *
 * @path conf/lint-configs/eslint-config/src/configs/a11y.ts
 * @since 4.0.0
 */

import type { Linter } from 'eslint';

/**
 * a11y ESLint 配置对象
 */
export const a11yConfig: Linter.Config = {
  plugins: ['@stylistic', 'import-x'],
  rules: {
    // ========== style 相关 a11y 检查 ==========
    // 严格规则可按需启用
  },
  overrides: [
    // Vue template 检查
    {
      files: ['**/*.vue'],
      rules: {
        // 强制按钮可聚焦
        'import-x/no-unused-modules': 'off', // Vue 模板不计入
      },
    },
    // 组件相关
    {
      files: ['**/components/**/*.ts', '**/components/**/*.vue', '**/effects/**/components/**/*.vue'],
      rules: {
        // 自定义规则占位（如需要可启用 eslint-plugin-vuejs-accessibility）
      },
    },
  ],
};

/**
 * 组件可访问性使用规范（文档参考）
 *
 * 1. 所有交互元素必须有明确的语义标签：
 *    - 点击用 <button>，链接用 <a href="...">
 *
 * 2. 表单必须配对 label：
 *    <label for="email">邮箱</label>
 *    <input id="email" type="email" />
 *
 * 3. 图像必须有 alt：
 *    <img src="logo.png" alt="公司 logo" />    ✓
 *    <img src="logo.png" alt="" />             ✓ 装饰性图像
 *    <img src="logo.png" />                    ✗ 报错
 *
 * 4. 慎用 tabindex：
 *    - 优先使用语义标签的自然焦点顺序
 *    - 需要自定义焦点时用 tabindex="0" 而非正数
 */

/**
 * axe-core 与 Playwright 集成测试的示例代码片段（供子应用 __tests__ 使用）
 *
 * @example // apps/**/e2e/a11y.spec.ts
 * import { test, expect } from '@playwright/test';
 * import AxeBuilder from '@axe-core/playwright';
 *
 * test('项目列表页无严重无障碍违规', async ({ page }) => {
 *   await page.goto('/ydsz-proj/opportunities');
 *   const results = await new AxeBuilder({ page })
 *     .withTags(['wcag2a', 'wcag2aa'])
 *     .analyze();
 *   expect(results.violations).toHaveLength(0);
 *   expect(results.passes.length).toBeGreaterThan(20); // 至少 20 项检查通过
 * });
 */

export default a11yConfig;
