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

  test('应能导航到项目管理页面', async ({ page }) => {
    const menuLink = page.locator('a:has-text("项目"), a:has-text("工程"), [data-test="menu-project"]').first();
    await expect(menuLink).toBeVisible({ timeout: 15000 });
    await menuLink.click();
    await expect(page).toHaveURL(/project/, { timeout: 10000 });
  });

  test('应能导航到消息中心', async ({ page }) => {
    const menuLink = page.locator('a:has-text("消息"), a:has-text("通知"), [data-test="menu-message"]').first();
    await expect(menuLink).toBeVisible({ timeout: 15000 });
    await menuLink.click();
    await expect(page).toHaveURL(/message|notification/, { timeout: 10000 });
  });

  test('应能导航到工作流页面', async ({ page }) => {
    const menuLink = page.locator('a:has-text("工作流"), a:has-text("流程"), [data-test="menu-workflow"]').first();
    await expect(menuLink).toBeVisible({ timeout: 15000 });
    await menuLink.click();
    await expect(page).toHaveURL(/workflow/, { timeout: 10000 });
  });
});

test.describe('核心链路：微前端集成', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/#/auth/login');
    await expect(page.locator('input').first()).toBeVisible({ timeout: 10000 });
    await page.locator('input').first().fill(TEST_USER);
    await page.locator('input[type="password"]').fill(TEST_PASS);
    await page.locator('button:has-text("登录"), button[type="submit"]').click();
    await expect(page).toHaveURL(/\/dashboard|\/home/, { timeout: 15000 });
  });

  test('子应用加载应显示容器', async ({ page }) => {
    const menuLink = page.locator('a:has-text("用户"), a:has-text("人员"), [data-test="menu-userinfo"]').first();
    await expect(menuLink).toBeVisible({ timeout: 15000 });
    await menuLink.click();
    
    // 等待子应用容器出现
    const subAppContainer = page.locator('#subapp-container, [data-test="subapp-container"]').first();
    await expect(subAppContainer).toBeVisible({ timeout: 15000 });
  });

  test('子应用切换应无控制台错误', async ({ page }) => {
    const errors: string[] = [];
    page.on('console', (msg) => {
      if (msg.type() === 'error') errors.push(msg.text());
    });

    // 导航到用户管理
    const userMenu = page.locator('a:has-text("用户"), [data-test="menu-userinfo"]').first();
    await expect(userMenu).toBeVisible({ timeout: 15000 });
    await userMenu.click();
    await page.waitForTimeout(2000);

    // 导航到系统设置
    const systemMenu = page.locator('a:has-text("系统"), [data-test="menu-system"]').first();
    await systemMenu.click();
    await page.waitForTimeout(2000);

    // 检查是否有严重错误（排除警告和预期的网络错误）
    const criticalErrors = errors.filter((e) => 
      e.includes('Error') || e.includes('Exception') || e.includes('Failed')
    );
    
    expect(criticalErrors.length, `发现 ${criticalErrors.length} 个严重错误`).toBe(0);
  });
});
