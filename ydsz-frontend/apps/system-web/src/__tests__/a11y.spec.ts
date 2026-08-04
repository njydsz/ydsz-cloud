/**
 * a11y 端到端测试示例（v4.0）
 *
 * 所有子应用 __tests__ 目录应至少包含此测试，
 * 目标：严重违规（ violations ）数量 === 0，
 * 至少 20 项检查通过。
 *
 * @path apps/*/e2e/a11y.spec.ts
 * @since 4.0.0
 */
import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

/**
 * 子应用应覆盖的核心路由（替换为实际路由列表）
 */
const PAGE_URLS = ['/'];

test.describe('可访问性（a11y）检查', () => {
  for (const url of PAGE_URLS) {
    test(`页面 "${url}" 无严重无障碍违规`, async ({ page }) => {
      await page.goto(url);
      // 等待页面加载稳定
      await page.waitForLoadState('networkidle');

      const results = await new AxeBuilder({ page })
        .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
        // 忽略已知的外部组件问题（如 element-plus 内部）
        .exclude('[data-a11y-ignore]')
        .analyze();

      // 仅输出所有违规详情便于调试
      if (results.violations.length > 0) {
        console.table(
          results.violations.map((v) => ({
            id: v.id,
            impact: v.impact,
            description: v.description,
            nodes: v.nodes.length,
          })),
        );
      }

      expect(results.violations).toHaveLength(0);
      expect(results.passes.length).toBeGreaterThan(20);
    });
  }

  test('键盘可访问性 —— 所有交互元素可 Tab 聚焦', async ({ page }) => {
    await page.goto('/');
    await page.waitForLoadState('networkidle');

    // Tab 遍历所有可交互元素
    const focusableElements = await page.locator(
      'a[href], button:not([disabled]), input:not([disabled]), textarea:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])',
    ).count();

    expect(focusableElements).toBeGreaterThan(0);

    for (let i = 0; i < focusableElements; i++) {
      await page.keyboard.press('Tab');
      const focused = await page.evaluate(() => document.activeElement?.tagName);
      expect(focused).not.toBeNull();
    }
  });

  test('图片 alt 文本检查', async ({ page }) => {
    await page.goto('/');
    const imagesWithoutAlt = await page.locator('img:not([alt])').count();
    expect(imagesWithoutAlt).toBe(0);
  });

  test('表单标签检查', async ({ page }) => {
    await page.goto('/');
    const inputs = await page.locator('input, textarea, select').count();
    if (inputs === 0) test.skip(); // 无表单则跳过

    for (let i = 0; i < inputs; i++) {
      const input = page.locator('input, textarea, select').nth(i);
      const id = await input.getAttribute('id');
      const ariaLabel = await input.getAttribute('aria-label');
      const ariaLabelledBy = await input.getAttribute('aria-labelledby');
      const hasLabel = id ? await page.locator(`label[for="${id}"]`).count() > 0 : false;

      expect(hasLabel || !!ariaLabel || !!ariaLabelledBy).toBeTruthy();
    }
  });
});
