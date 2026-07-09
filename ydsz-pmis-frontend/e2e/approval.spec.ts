/**
 * @file E2E 测试：审批中心流程
 * @description 覆盖审批中心的待办列表、审批操作等核心场景
 * @module e2e/approval
 */
import { test, expect } from '@playwright/test'

test.describe('审批中心', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/#/login')
    await page.locator('input[type="text"]').fill('admin')
    await page.locator('input[type="password"]').fill('admin123')
    await page.getByRole('button', { name: /登录|Login/ }).click()
    await page.waitForURL('**/#/dashboard', { timeout: 10000 })
  })

  test('导航到审批中心', async ({ page }) => {
    await page.goto('/#/workflow/approval-center')
    // 验证页面加载
    await expect(page.locator('.el-table, .pro-table')).toBeVisible({ timeout: 10000 })
  })

  test('待办列表应显示数据或空状态', async ({ page }) => {
    await page.goto('/#/workflow/approval-center')
    await page.waitForSelector('.el-table, .empty-state, .el-empty', { timeout: 10000 })

    // 要么有数据表格，要么有空状态
    const hasTable = await page.locator('.el-table').isVisible().catch(() => false)
    const hasEmpty = await page.locator('.empty-state, .el-empty').isVisible().catch(() => false)
    expect(hasTable || hasEmpty).toBeTruthy()
  })

  test('审批操作应弹出确认对话框', async ({ page }) => {
    await page.goto('/#/workflow/approval-center')
    await page.waitForSelector('.el-table', { timeout: 10000 })

    // 查找审批按钮
    const approveBtn = page.getByRole('button', { name: /通过|同意|Approve/ }).first()
    if (await approveBtn.isVisible()) {
      await approveBtn.click()
      // 应弹出确认框或审批意见输入框
      await expect(page.locator('.el-dialog, .el-message-box')).toBeVisible({ timeout: 5000 })
    }
  })
})
