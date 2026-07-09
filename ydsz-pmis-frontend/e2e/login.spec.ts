/**
 * @file E2E 测试：登录流程
 * @description 覆盖核心登录场景：
 *   1. 正常账号密码登录
 *   2. 密码错误提示
 *   3. 登录后跳转 Dashboard
 *   4. 退出登录
 * @module e2e/login
 */
import { test, expect } from '@playwright/test'

test.describe('登录流程', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/#/login')
  })

  test('页面应显示登录表单', async ({ page }) => {
    await expect(page.locator('.login-container')).toBeVisible()
    await expect(page.locator('input[type="text"]')).toBeVisible()
    await expect(page.locator('input[type="password"]')).toBeVisible()
    await expect(page.getByRole('button', { name: /登录|Login/ })).toBeVisible()
  })

  test('空用户名应显示验证错误', async ({ page }) => {
    await page.getByRole('button', { name: /登录|Login/ }).click()
    await expect(page.locator('.el-form-item__error')).toBeVisible()
  })

  test('密码错误应提示错误', async ({ page }) => {
    await page.locator('input[type="text"]').fill('admin')
    await page.locator('input[type="password"]').fill('wrongpassword')
    await page.getByRole('button', { name: /登录|Login/ }).click()

    // 应显示错误提示
    await expect(page.locator('.el-message--error')).toBeVisible({ timeout: 5000 })
  })

  test('正确凭证登录成功并跳转 Dashboard', async ({ page }) => {
    // 使用测试账号
    await page.locator('input[type="text"]').fill('admin')
    await page.locator('input[type="password"]').fill('admin123')
    await page.getByRole('button', { name: /登录|Login/ }).click()

    // 等待跳转到 Dashboard
    await page.waitForURL('**/#/dashboard', { timeout: 10000 })
    await expect(page.locator('.dashboard-container, [class*="dashboard"]')).toBeVisible()
  })

  test('登录后退出应返回登录页', async ({ page }) => {
    // 先登录
    await page.locator('input[type="text"]').fill('admin')
    await page.locator('input[type="password"]').fill('admin123')
    await page.getByRole('button', { name: /登录|Login/ }).click()
    await page.waitForURL('**/#/dashboard', { timeout: 10000 })

    // 点击用户头像下拉菜单
    await page.locator('.user-info, .el-dropdown').first().click()
    // 点击退出登录
    await page.getByRole('listitem').filter({ hasText: /退出|Logout/ }).click()
    // 确认退出
    await page.getByRole('button', { name: /确定|确认|Confirm/ }).click()

    // 应返回登录页
    await page.waitForURL('**/#/login', { timeout: 5000 })
  })
})
