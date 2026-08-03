/**
 * 视觉回归测试 — 核心页面截图基线
 *
 * 首次运行生成基线快照（e2e/visual-regression.spec.ts-snapshots/），
 * 后续运行与基线对比，像素差异超阈值则失败，阻断视觉回归 PR。
 *
 * 更新基线：pnpm exec playwright test --update-snapshots
 *
 * @author ydsz-team
 * @since 3.1.0
 */
import { test, expect } from '@playwright/test';

const TEST_USER = process.env.E2E_TEST_USERNAME || 'admin';
const TEST_PASS = process.env.E2E_TEST_PASSWORD || 'admin123';

test.describe('视觉回归：核心页面', () => {
  test('登录页应与基线一致', async ({ page }) => {
    await page.goto('/#/auth/login');
    await expect(page.locator('input').first()).toBeVisible({ timeout: 10000 });
    // 等待动画/字体加载完成
    await page.waitForLoadState('networkidle');
    await expect(page).toHaveScreenshot('login-page.png', {
      maxDiffPixelRatio: 0.01,
      animations: 'disabled',
    });
  });

  test('登录后首页应与基线一致', async ({ page }) => {
    await page.goto('/#/auth/login');
    await page.locator('input').first().fill(TEST_USER);
    await page.locator('input[type="password"]').fill(TEST_PASS);
    await page.locator('button:has-text("登录"), button[type="submit"]').click();
    await expect(page).toHaveURL(/\/dashboard|\/home/, { timeout: 15000 });
    await page.waitForLoadState('networkidle');

    await expect(page).toHaveScreenshot('dashboard.png', {
      maxDiffPixelRatio: 0.01,
      animations: 'disabled',
    });
  });

  test('子应用容器加载中应显示骨架屏', async ({ page }) => {
    await page.goto('/#/auth/login');
    await page.locator('input').first().fill(TEST_USER);
    await page.locator('input[type="password"]').fill(TEST_PASS);
    await page.locator('button:has-text("登录"), button[type="submit"]').click();
    await expect(page).toHaveURL(/\/dashboard|\/home/, { timeout: 15000 });

    // 导航到子应用，捕获加载态
    const menuLink = page.locator('[data-test="menu-userinfo"], a:has-text("用户")').first();
    if (await menuLink.isVisible({ timeout: 5000 }).catch(() => false)) {
      await menuLink.click();
      // 截取子应用加载瞬态
      await expect(page.locator('#subapp-container')).toHaveScreenshot(
        'subapp-loading.png',
        { maxDiffPixelRatio: 0.05, animations: 'disabled },
      );
    }
  });
});
