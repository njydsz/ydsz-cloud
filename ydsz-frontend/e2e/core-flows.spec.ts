/**
 * YDSZ 核心链路 E2E 测试 — 登录 → 首页 → 子应用导航
 *
 * v3.0: 硬断言（移除 if-isVisible 静默跳过），测试账号走环境变量。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import { test, expect } from '@playwright/test';

/** 从环境变量读取测试账号（CI secrets 注入） */
const TEST_USER = process.env.E2E_TEST_USERNAME || 'admin';
const TEST_PASS = process.env.E2E_TEST_PASSWORD || 'admin123';

test.describe('核心链路：用户登录', () => {
  test('应成功登录并跳转到首页', async ({ page }) => {
    await page.goto('/#/auth/login');
    await expect(page.locator('input[placeholder*="账号"], input[placeholder*="用户"]').first())
      .toBeVisible({ timeout: 10000 });

    const usernameInput = page.locator('input').first();
    const passwordInput = page.locator('input[type="password"]');

    await usernameInput.fill(TEST_USER);
    await passwordInput.fill(TEST_PASS);

    const loginButton = page.locator('button:has-text("登录"), button[type="submit"]');
    await loginButton.click();

    await expect(page).toHaveURL(/\/dashboard|\/home/, { timeout: 15000 });
    await expect(page.locator('body')).toBeVisible();
  });

  test('密码错误应显示错误提示', async ({ page }) => {
    await page.goto('/#/auth/login');
    await expect(page.locator('input').first()).toBeVisible({ timeout: 10000 });

    await page.locator('input').first().fill(TEST_USER);
    await page.locator('input[type="password"]').fill('wrongpassword');

    const loginButton = page.locator('button:has-text("登录"), button[type="submit"]');
    await loginButton.click();

    await expect(page.locator('text=/错误|失败|无效|incorrect/i'))
      .toBeVisible({ timeout: 10000 });
  });
});

test.describe('核心链路：页面导航', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/#/auth/login');
    await expect(page.locator('input').first()).toBeVisible({ timeout: 10000 });
    await page.locator('input').first().fill(TEST_USER);
    await page.locator('input[type="password"]').fill(TEST_PASS);
    await page.locator('button:has-text("登录"), button[type="submit"]').click();
    await expect(page).toHaveURL(/\/dashboard|\/home/, { timeout: 15000 });
  });

  test('应能导航到用户管理页面', async ({ page }) => {
    const menuLink = page.locator('a:has-text("用户"), a:has-text("人员"), [data-test="menu-userinfo"]').first();
    await expect(menuLink).toBeVisible({ timeout: 15000 });
    await menuLink.click();
    await expect(page).toHaveURL(/userinfo|user/, { timeout: 10000 });
  });

  test('应能导航到系统设置页面', async ({ page }) => {
    const menuLink = page.locator('a:has-text("系统"), a:has-text("设置"), [data-test="menu-system"]').first();
    await expect(menuLink).toBeVisible({ timeout: 15000 });
    await menuLink.click();
    await expect(page).toHaveURL(/system/, { timeout: 10000 });
  });

  test('应能正常退出登录', async ({ page }) => {
    const userDropdown = page.locator('[class*="avatar"], [class*="user-dropdown"], [data-test="user-menu"]').first();
    await expect(userDropdown).toBeVisible({ timeout: 15000 });
    await userDropdown.click();

    const logoutLink = page.locator('text=/退出|登出|注销|logout/i').first();
    await expect(logoutLink).toBeVisible({ timeout: 5000 });
    await logoutLink.click();

    await expect(page).toHaveURL(/login|auth/, { timeout: 10000 });
  });
});
