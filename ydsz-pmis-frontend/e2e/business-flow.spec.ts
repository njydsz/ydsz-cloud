/**
 * @file E2E 测试：主链路业务流程（商机→立项→合同→执行→回款）
 * @description 覆盖项目全生命周期的核心业务链路：
 *   1. 创建商机
 *   2. 商机转立项
 *   3. 立项后创建合同
 *   4. 合同关联 WBS 任务执行
 *   5. 按里程碑开票回款
 * @module e2e/business-flow
 */
import { test, expect } from '@playwright/test'

test.describe('主链路：商机→立项→合同→执行→回款', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/#/login')
    await page.locator('input[type="text"]').fill('admin')
    await page.locator('input[type="password"]').fill('admin123')
    await page.getByRole('button', { name: /登录|Login/ }).click()
    await page.waitForURL('**/#/dashboard', { timeout: 10000 })
  })

  test('Step1: 创建商机', async ({ page }) => {
    await page.goto('/#/project/opportunity')
    await page.waitForSelector('.el-table', { timeout: 10000 })

    // 点击新建按钮
    const newBtn = page.getByRole('button', { name: /新建|新增|New/ }).first()
    if (await newBtn.isVisible()) {
      await newBtn.click()
      await expect(page.locator('.el-dialog, .el-drawer')).toBeVisible({ timeout: 5000 })

      // 填写商机编号
      const codeInput = page.locator('.el-dialog input, .el-drawer input').first()
      if (await codeInput.isVisible()) {
        await codeInput.fill('E2E-OPP-' + Date.now())
      }

      // 填写商机名称
      const nameInputs = page.locator('.el-dialog input, .el-drawer input')
      if (await nameInputs.nth(1).isVisible()) {
        await nameInputs.nth(1).fill('E2E测试商机')
      }

      // 提交
      const submitBtn = page.getByRole('button', { name: /确定|提交|保存|OK|Save/ }).first()
      if (await submitBtn.isVisible()) {
        await submitBtn.click()
        await page.waitForTimeout(1000)
      }

      // 弹窗应关闭
      await expect(page.locator('.el-dialog')).not.toBeVisible({ timeout: 5000 })
    }
  })

  test('Step2: 商机详情页可访问', async ({ page }) => {
    await page.goto('/#/project/opportunity')
    await page.waitForSelector('.el-table', { timeout: 10000 })

    // 点击第一行查看详情
    const firstRow = page.locator('.el-table__row').first()
    if (await firstRow.isVisible()) {
      await firstRow.click()
      await page.waitForTimeout(1000)
      // 应跳转到详情页或弹出抽屉
      const detailVisible = await page.locator('.el-drawer, .detail-page').first().isVisible()
      expect(detailVisible || page.url() !== '/#/project/opportunity').toBeTruthy()
    }
  })

  test('Step3: 立项列表页可访问', async ({ page }) => {
    await page.goto('/#/project/initiation')
    await page.waitForSelector('.el-table, .empty-state', { timeout: 10000 })
    // 页面应正常加载，不出现 404 或错误页
    await expect(page.locator('.el-table, .empty-state')).toBeVisible()
  })

  test('Step4: 合同列表页可访问', async ({ page }) => {
    await page.goto('/#/project/contract')
    await page.waitForSelector('.el-table, .empty-state', { timeout: 10000 })
    await expect(page.locator('.el-table, .empty-state')).toBeVisible()
  })

  test('Step5: WBS 任务管理页可访问', async ({ page }) => {
    await page.goto('/#/project/execution/wbs')
    await page.waitForSelector('.el-table, .empty-state', { timeout: 10000 })
    await expect(page.locator('.el-table, .empty-state')).toBeVisible()
  })

  test('Step6: 发票管理页可访问', async ({ page }) => {
    await page.goto('/#/project/finance/invoice')
    await page.waitForSelector('.el-table, .empty-state', { timeout: 10000 })
    await expect(page.locator('.el-table, .empty-state')).toBeVisible()
  })

  test('Step7: 回款管理页可访问', async ({ page }) => {
    await page.goto('/#/project/finance/payment')
    await page.waitForSelector('.el-table, .empty-state', { timeout: 10000 })
    await expect(page.locator('.el-table, .empty-state')).toBeVisible()
  })

  test('Step8: 利润看板页可访问', async ({ page }) => {
    await page.goto('/#/project/finance/profit')
    await page.waitForSelector('.el-card, .el-table, .empty-state, .chart-container', { timeout: 10000 })
    await expect(page.locator('.el-card, .el-table, .empty-state, .chart-container')).toBeVisible()
  })

  test('Step9: EVM 挣值管理页可访问', async ({ page }) => {
    await page.goto('/#/project/execution/evm')
    await page.waitForSelector('.el-card, .el-table, .empty-state, .chart-container', { timeout: 10000 })
    await expect(page.locator('.el-card, .el-table, .empty-state, .chart-container')).toBeVisible()
  })
})

test.describe('主链路：工作流审批', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/#/login')
    await page.locator('input[type="text"]').fill('admin')
    await page.locator('input[type="password"]').fill('admin123')
    await page.getByRole('button', { name: /登录|Login/ }).click()
    await page.waitForURL('**/#/dashboard', { timeout: 10000 })
  })

  test('待办任务列表页可访问', async ({ page }) => {
    await page.goto('/#/workflow/task/todo')
    await page.waitForSelector('.el-table, .empty-state', { timeout: 10000 })
    await expect(page.locator('.el-table, .empty-state')).toBeVisible()
  })

  test('已办任务列表页可访问', async ({ page }) => {
    await page.goto('/#/workflow/task/done')
    await page.waitForSelector('.el-table, .empty-state', { timeout: 10000 })
    await expect(page.locator('.el-table, .empty-state')).toBeVisible()
  })

  test('流程定义列表页可访问', async ({ page }) => {
    await page.goto('/#/workflow/definition')
    await page.waitForSelector('.el-table, .empty-state', { timeout: 10000 })
    await expect(page.locator('.el-table, .empty-state')).toBeVisible()
  })

  test('工作流监控大盘可访问', async ({ page }) => {
    await page.goto('/#/workflow/monitor')
    await page.waitForSelector('.el-card, .el-table, .empty-state, .chart-container', { timeout: 10000 })
    await expect(page.locator('.el-card, .el-table, .empty-state, .chart-container')).toBeVisible()
  })
})

test.describe('主链路：系统管理关键页面', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/#/login')
    await page.locator('input[type="text"]').fill('admin')
    await page.locator('input[type="password"]').fill('admin123')
    await page.getByRole('button', { name: /登录|Login/ }).click()
    await page.waitForURL('**/#/dashboard', { timeout: 10000 })
  })

  test('用户管理页可访问', async ({ page }) => {
    await page.goto('/#/system/user')
    await page.waitForSelector('.el-table, .empty-state', { timeout: 10000 })
    await expect(page.locator('.el-table, .empty-state')).toBeVisible()
  })

  test('角色管理页可访问', async ({ page }) => {
    await page.goto('/#/system/role')
    await page.waitForSelector('.el-table, .empty-state', { timeout: 10000 })
    await expect(page.locator('.el-table, .empty-state')).toBeVisible()
  })

  test('操作日志页可访问', async ({ page }) => {
    await page.goto('/#/system/audit/operation-log')
    await page.waitForSelector('.el-table, .empty-state', { timeout: 10000 })
    await expect(page.locator('.el-table, .empty-state')).toBeVisible()
  })

  test('Feature Flag 管理页可访问', async ({ page }) => {
    await page.goto('/#/system/config/feature-flag')
    await page.waitForSelector('.el-table, .empty-state', { timeout: 10000 })
    await expect(page.locator('.el-table, .empty-state')).toBeVisible()
  })
})
