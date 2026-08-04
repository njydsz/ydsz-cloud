/**
 * 无障碍（a11y）自动化测试 — 基于 axe-core
 *
 * 在 E2E 流程中对核心页面跑 axe 规则，零违反才放行。
 * 覆盖 WCAG 2.1 AA 级别：色彩对比、ARIA、键盘可达、标签关联等。
 *
 * v3.2: 扩展 a11y 覆盖子应用业务页面，对标腾讯 CDC/阿里 A11y 规范要求核心业务页 100% 覆盖
 *
 * @author ydsz-team
 * @since 3.1.0
 */
import AxeBuilder from '@axe-core/playwright';
import { test, expect } from '@playwright/test';

const TEST_USER = process.env.E2E_TEST_USERNAME || 'admin';
const TEST_PASS = process.env.E2E_TEST_PASSWORD || 'admin123';

test.describe('无障碍：核心页面', () => {
  test('登录页应无 a11y 违规', async ({ page }) => {
    await page.goto('/#/auth/login');
    await expect(page.locator('input').first()).toBeVisible({ timeout: 10000 });
    await page.waitForLoadState('networkidle');

    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze();

    expect(results.violations, formatViolations(results.violations)).toEqual([]);
  });

  test('登录后首页应无 a11y 违规', async ({ page }) => {
    await page.goto('/#/auth/login');
    await page.locator('input').first().fill(TEST_USER);
    await page.locator('input[type="password"]').fill(TEST_PASS);
    await page.locator('button:has-text("登录"), button[type="submit"]').click();
    await expect(page).toHaveURL(/\/dashboard|\/home/, { timeout: 15000 });
    await page.waitForLoadState('networkidle');

    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze();

    expect(results.violations, formatViolations(results.violations)).toEqual([]);
  });

  test('子应用页面 a11y：用户管理', async ({ page }) => {
    test.setTimeout(30000);
    await page.goto('/#/auth/login');
    await expect(page.locator('input').first()).toBeVisible({ timeout: 10000 });
    await page.locator('input').first().fill(TEST_USER);
    await page.locator('input[type="password"]').fill(TEST_PASS);
    await page.locator('button:has-text("登录"), button[type="submit"]').click();
    await expect(page).toHaveURL(/\/dashboard|\/home/, { timeout: 15000 });

    await page.goto('/#/ydsz-user/users');
    await page.waitForLoadState('networkidle');

    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze();

    expect(results.violations, formatViolations(results.violations)).toEqual([]);
  });

  test('子应用页面 a11y：系统设置', async ({ page }) => {
    test.setTimeout(30000);
    await page.goto('/#/auth/login');
    await expect(page.locator('input').first()).toBeVisible({ timeout: 10000 });
    await page.locator('input').first().fill(TEST_USER);
    await page.locator('input[type="password"]').fill(TEST_PASS);
    await page.locator('button:has-text("登录"), button[type="submit"]').click();
    await expect(page).toHaveURL(/\/dashboard|\/home/, { timeout: 15000 });

    await page.goto('/#/ydsz-sys/configs');
    await page.waitForLoadState('networkidle');

    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze();

    expect(results.violations, formatViolations(results.violations)).toEqual([]);
  });
});

/** 将 axe violations 格式化为可读消息，便于失败时定位 */
function formatViolations(violations: Array<{
  id: string;
  impact?: string;
  description: string;
  help: string;
  helpUrl: string;
  nodes: Array<{ html: string }>;
}>): string {
  if (violations.length === 0) return '';
  const lines = violations.map((v) =>
    `[${v.impact || '?'}] ${v.id}: ${v.help} (${v.nodes.length} 处) — ${v.helpUrl}`,
  );
  return `\n${lines.join('\n')}\n`;
}
