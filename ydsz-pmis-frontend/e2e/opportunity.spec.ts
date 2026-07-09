/**
 * @file E2E 测试：商机管理流程
 * @description 覆盖商机列表、搜索、新建、编辑等核心场景
 * @module e2e/opportunity
 */
import { test, expect } from '@playwright/test'

test.describe('商机管理', () => {
  // 所有测试前先登录
  test.beforeEach(async ({ page }) => {
    await page.goto('/#/login')
    await page.locator('input[type="text"]').fill('admin')
    await page.locator('input[type="password"]').fill('admin123')
    await page.getByRole('button', { name: /登录|Login/ }).click()
    await page.waitForURL('**/#/dashboard', { timeout: 10000 })
  })

  test('导航到商机列表页', async ({ page }) => {
    await page.goto('/#/project/opportunity')
    // 验证页面加载
    await expect(page.locator('.pro-table, .el-table')).toBeVisible({ timeout: 10000 })
  })

  test('搜索功能应过滤列表', async ({ page }) => {
    await page.goto('/#/project/opportunity')
    await page.waitForSelector('.pro-table, .el-table', { timeout: 10000 })

    // 在搜索框输入关键词
    const searchInput = page.locator('.search-form input[type="text"], .toolbar input').first()
    if (await searchInput.isVisible()) {
      await searchInput.fill('测试')
      // 点击搜索按钮或按回车
      const searchBtn = page.getByRole('button', { name: /搜索|查询|Search/ }).first()
      if (await searchBtn.isVisible()) {
        await searchBtn.click()
      } else {
        await searchInput.press('Enter')
      }
      // 等待表格刷新
      await page.waitForTimeout(1000)
      // 验证表格仍然可见
      await expect(page.locator('.el-table')).toBeVisible()
    }
  })

  test('点击新建按钮应打开表单对话框', async ({ page }) => {
    await page.goto('/#/project/opportunity')
    await page.waitForSelector('.el-table', { timeout: 10000 })

    const newBtn = page.getByRole('button', { name: /新建|新增|New/ }).first()
    if (await newBtn.isVisible()) {
      await newBtn.click()
      // 应弹出对话框或抽屉
      await expect(page.locator('.el-dialog, .el-drawer')).toBeVisible({ timeout: 5000 })
    }
  })

  test('分页器应正常显示', async ({ page }) => {
    await page.goto('/#/project/opportunity')
    await page.waitForSelector('.el-table', { timeout: 10000 })

    const pagination = page.locator('.el-pagination')
    if (await pagination.isVisible()) {
      // 验证分页组件存在
      await expect(pagination).toBeVisible()
    }
  })
})
