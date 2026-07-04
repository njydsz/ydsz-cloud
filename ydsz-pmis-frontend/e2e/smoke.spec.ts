import { test, expect } from '@playwright/test'

const BASE_URL = process.env.BASE_URL || 'http://localhost:5173'

test.describe('PMIS E2E Smoke Tests', () => {
  test('首页应显示登录页面', async ({ page }) => {
    await page.goto(BASE_URL)
    await expect(page).toHaveTitle(/PMIS|项目管理/)
  })

  test('登录页面应包含必要元素', async ({ page }) => {
    await page.goto(BASE_URL)
    // 用户名输入框
    await expect(page.locator('input[placeholder*="用户名"], input[name="username"]')).toBeVisible()
    // 密码输入框
    await expect(page.locator('input[placeholder*="密码"], input[type="password"]')).toBeVisible()
  })

  test('空表单提交应显示验证错误', async ({ page }) => {
    await page.goto(BASE_URL)
    await page.click('button:has-text("登录")')
    // 应有验证提示
    await expect(page.locator('.el-message--error, .el-form-item__error, [role="alert"]')).toBeVisible({
      timeout: 5000,
    })
  })
})