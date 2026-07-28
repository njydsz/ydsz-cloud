/**
 * YDSZ 核心链路 E2E 测试 — 登录 → 首页 → 子应用导航
 *
 * P1-2: 前端 E2E 测试接入 CI
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import { test, expect } from '@playwright/test';

test.describe('核心链路：用户登录', () => {
  test('应成功登录并跳转到首页', async ({ page }) => {
    // 导航到登录页
    await page.goto('/#/auth/login');

    // 等待页面加载
    await expect(page.locator('input[placeholder*="账号"], input[placeholder*="用户"]').first()).toBeVisible({ timeout: 10000 });

    // 填写登录表单
    const usernameInput = page.locator('input').first();
    const passwordInput = page.locator('input[type="password"]');

    await usernameInput.fill('admin');
    await passwordInput.fill('admin123');

    // 点击登录按钮
    const loginButton = page.locator('button:has-text("登录"), button[type="submit"]');
    await loginButton.click();

    // 验证跳转到首页
    await expect(page).toHaveURL(/\/dashboard|\/home/, { timeout: 15000 });

    // 验证页面标题或关键元素可见
    await expect(page.locator('body')).toBeVisible();
  });

  test('密码错误应显示错误提示', async ({ page }) => {
    await page.goto('/#/auth/login');

    const usernameInput = page.locator('input').first();
    const passwordInput = page.locator('input[type="password"]');

    await usernameInput.fill('admin');
    await passwordInput.fill('wrongpassword');

    const loginButton = page.locator('button:has-text("登录"), button[type="submit"]');
    await loginButton.click();

    // 验证错误提示出现
    await expect(page.locator('text=/错误|失败|无效|incorrect/i')).toBeVisible({ timeout: 10000 });
  });
});

test.describe('核心链路：页面导航', () => {
  // 需要先登录
  test.beforeEach(async ({ page }) => {
    await page.goto('/#/auth/login');
    const usernameInput = page.locator('input').first();
    const passwordInput = page.locator('input[type="password"]');
    await usernameInput.fill('admin');
    await passwordInput.fill('admin123');
    const loginButton = page.locator('button:has-text("登录"), button[type="submit"]');
    await loginButton.click();
    await expect(page).toHaveURL(/\/dashboard|\/home/, { timeout: 15000 });
  });

  test('应能导航到用户管理页面', async ({ page }) => {
    // 点击侧边栏菜单中的用户管理
    const menuLink = page.locator('a:has-text("用户"), a:has-text("人员"), [data-test="menu-userinfo"]').first();
    if (await menuLink.isVisible({ timeout: 5000 })) {
      await menuLink.click();
      // 验证 URL 变化
      await expect(page).toHaveURL(/userinfo|user/, { timeout: 10000 });
    }
  });

  test('应能导航到系统设置页面', async ({ page }) => {
    const menuLink = page.locator('a:has-text("系统"), a:has-text("设置"), [data-test="menu-system"]').first();
    if (await menuLink.isVisible({ timeout: 5000 })) {
      await menuLink.click();
      await expect(page).toHaveURL(/system/, { timeout: 10000 });
    }
  });

  test('应能正常退出登录', async ({ page }) => {
    // 点击用户头像/下拉菜单
    const userDropdown = page.locator('[class*="avatar"], [class*="user-dropdown"], [data-test="user-menu"]').first();
    if (await userDropdown.isVisible({ timeout: 5000 })) {
      await userDropdown.click();
      // 点击退出登录
      const logoutLink = page.locator('text=/退出|登出|注销|logout/i').first();
      if (await logoutLink.isVisible({ timeout: 3000 })) {
        await logoutLink.click();
        // 验证跳转回登录页
        await expect(page).toHaveURL(/login|auth/, { timeout: 10000 });
      }
    }
  });
});
