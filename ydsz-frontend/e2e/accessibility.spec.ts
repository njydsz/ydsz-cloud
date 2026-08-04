/**
 * 无障碍（a11y）自动化测试 — 基于 axe-core
 *
 * 在 E2E 流程中对核心页面跑 axe 规则，零违反才放行。
 * 覆盖 WCAG 2.1 AA 级别：色彩对比、ARIA、键盘可达、标签关联等。
 *
 * v3.2: 扩展 a11y 覆盖子应用业务页面，对标腾讯 CDC/阿里 A11y 规范要求核心业务页 100% 覆盖
 * v4.0 P1-3: 全覆盖所有 9 个子应用 + 主应用核心页；CI 门禁：严重违规数 (critical/serious) = 0
 *
 * @author ydsz-team
 * @since 3.1.0
 */
import AxeBuilder from '@axe-core/playwright';
import { test, expect } from '@playwright/test';

const TEST_USER = process.env.E2E_TEST_USERNAME || 'admin';
const TEST_PASS = process.env.E2E_TEST_PASSWORD || 'admin123';

/** 通用登录流程，返回登录后跳转的 URL */
async function login(page: any): Promise<void> {
  await page.goto('/#/auth/login');
  await expect(page.locator('input').first()).toBeVisible({ timeout: 10000 });
  await page.locator('input').first().fill(TEST_USER);
  await page.locator('input[type="password"]').fill(TEST_PASS);
  await page.locator('button:has-text("登录"), button[type="submit"]').click();
  await expect(page).toHaveURL(/\/dashboard|\/home/, { timeout: 15000 });
}

/** 通用的 a11y 检测：在所有子应用关键页面通过 WCAG 21 AA */
async function assertA11yPass(page: any, url: string, pageName: string): Promise<void> {
  await page.goto(url);
  await page.waitForLoadState('networkidle');

  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze();

  // 严重违规 (critical / serious) 直接失败
  const seriousViolations = results.violations.filter(
    (v) => v.impact === 'critical' || v.impact === 'serious',
  );

  expect(
    seriousViolations,
    `[${pageName}] WCAG 21 AA 严重违规 (严重/阻断):\n${formatViolations(seriousViolations)}`,
  ).toEqual([]);

  // 中等/轻微违规仅做警告性记录（不阻断合并），便于渐进改善
  const minorViolations = results.violations.filter(
    (v) => v.impact === 'moderate' || v.impact === 'minor',
  );
  if (minorViolations.length > 0) {
    console.warn(
      `[a11y][${pageName}] 中等/轻微违规 ${minorViolations.length} 处（建议修复）:\n` +
      formatViolations(minorViolations),
    );
  }
}

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

  test('登录后首页应无 a11y 严重违规', async ({ page }) => {
    await login(page);
    await assertA11yPass(page, '/', 'Dashboard');
  });
});

// v4.0 P1-3: 所有子应用核心页 a11y 全覆盖
const SUB_APP_PAGES = [
  { name: '用户管理', path: '/#/ydsz-user/users' },
  { name: '系统设置', path: '/#/ydsz-sys/configs' },
  { name: '项目管理', path: '/#/ydsz-project/projects' },
  { name: '工作流', path: '/#/ydsz-workflow/flow-list' },
  { name: '定时任务', path: '/#/ydsz-cronjob/jobs' },
  { name: '规则引擎', path: '/#/ydsz-literule/rules' },
  { name: '知识库', path: '/#/ydsz-nextwiki/wiki' },
  { name: '消息中心', path: '/#/ydsz-message/notifications' },
  { name: '代理管理', path: '/#/ydsz-agent/agents' },
] as const;

test.describe('无障碍：子应用核心页面全覆盖', () => {
  for (const { name, path } of SUB_APP_PAGES) {
    test(`${name} (${path}) 应无 a11y 严重违规`, async ({ page }) => {
      test.setTimeout(30000);
      await login(page);
      await assertA11yPass(page, path, name);
    });
  }
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
