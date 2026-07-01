/**
 * E2E #1: 立项 → 合同 → 结项 全链路
 * 批次 21 / P1
 *
 * 业务流:
 *   1) PM 登录, 进入 /project/initiation
 *   2) 新建立项 (project_code 自动生成, 名称 "E2E-测试项目-xxx")
 *   3) 提交 (DRAFT → SUBMITTED)
 *   4) 审批通过 (SUBMITTED → APPROVED, 模拟主管审批)
 *   5) 进入合同页, 引用立项 ID 新建合同
 *   6) 合同签订 (DRAFT → SIGNED)
 *   7) 进入结项页 (closure), 选择同一项目, 提交结项申请
 *   8) 验收通过 → 项目状态变为 CLOSED
 *
 * 涉及页面:
 *   /project/initiation  - 项目立项
 *   /project/contract    - 合同
 *   /closure/index       - 项目结项
 */
import { test, expect, waitForTableLoaded, dismissMessages, waitForDialog, confirmDialog, expectToast } from './fixtures/utils'
import { E2EUser } from './fixtures/auth.fixture'

const PROJECT_CODE_PREFIX = 'E2E-PROJ'
const CONTRACT_CODE_PREFIX = 'E2E-CT'
const PROJECT_NAME = `E2E 测试项目 ${Date.now()}`

test.describe('立项 → 合同 → 结项 全链路 E2E', () => {
  test.beforeEach(async ({ loginAs }) => {
    await loginAs('pm' as E2EUser)
  })

  test('完整业务闭环', async ({ page }) => {
    // ========== 步骤 1: 立项 ==========
    await test.step('PM 访问立项页', async () => {
      await page.goto('/project/initiation')
      await waitForTableLoaded(page)
      await expect(page.locator('.page-title, h2, h1').filter({ hasText: /立项|initiation/i }).first())
        .toBeVisible({ timeout: 10_000 })
    })

    let createdProjectId: number | undefined
    let createdProjectCode: string

    await test.step('点击新增, 填写立项表单', async () => {
      // 触发新增弹窗
      await page.getByRole('button', { name: /新增|新建|创建|add|new/i }).first().click()
      const dlg = await waitForDialog(page, /新增立项|新建立项|新增项目/)
      await expect(dlg).toBeVisible()

      // 填写
      const code = `${PROJECT_CODE_PREFIX}-${Date.now().toString().slice(-6)}`
      createdProjectCode = code
      await dlg.getByLabel(/项目编号|编号/).fill(code)
      await dlg.getByLabel(/项目名称|名称/).fill(PROJECT_NAME)
      await dlg.getByLabel(/客户名称|客户/).fill('E2E 测试客户')
      await dlg.getByLabel(/合同金额|预算/).fill('1000000')

      // 提交
      await dlg.getByRole('button', { name: /确定|保存|提交/ }).last().click()
      await expectToast(page, /成功|success/i, 10_000)

      // 解析新建项目 ID
      await page.waitForTimeout(1_000)
      const row = page.locator('.vxe-table .vxe-body--row, .el-table__row', {
        hasText: code,
      }).first()
      await expect(row).toBeVisible({ timeout: 10_000 })

      // 点击详情按钮获取 ID (从 URL 或详情面板)
      await row.getByRole('button', { name: /详情|查看/ }).first().click()
      await page.waitForTimeout(500)
      const url = page.url()
      const m = url.match(/[?&]id=(\d+)/)
      if (m) createdProjectId = Number(m[1])
    })

    await test.step('立项状态: DRAFT → SUBMITTED', async () => {
      // 关闭详情
      await page.keyboard.press('Escape')
      await page.waitForTimeout(300)
      await dismissMessages(page)

      // 找到刚创建的行, 点击"提交"按钮
      const row = page.locator('.vxe-table .vxe-body--row, .el-table__row', {
        hasText: createdProjectCode!,
      }).first()
      await row.getByRole('button', { name: /提交|sub/i }).first().click()

      // 确认
      const cfm = page.locator('.el-message-box').last()
      await cfm.waitFor({ state: 'visible', timeout: 5_000 })
      await cfm.locator('.el-button--primary').click()
      await expectToast(page, /成功|submitted/i, 10_000)
    })

    // ========== 步骤 2: 审批 (切换到 admin 角色) ==========
    await test.step('Admin 审批立项', async ({ loginAs }) => {
      await loginAs('admin' as E2EUser)
      await page.goto('/project/initiation')
      await waitForTableLoaded(page)
      const row = page.locator('.vxe-table .vxe-body--row, .el-table__row', {
        hasText: createdProjectCode!,
      }).first()
      await row.getByRole('button', { name: /审批|通过|approve/i }).first().click()
      const cfm = page.locator('.el-message-box').last()
      await cfm.waitFor({ state: 'visible', timeout: 5_000 })
      await cfm.locator('.el-button--primary').click()
      await expectToast(page, /成功|approved/i, 10_000)
    })

    // ========== 步骤 3: 合同 ==========
    let createdContractId: number | undefined
    await test.step('基于立项 ID 创建合同', async () => {
      await page.goto('/project/contract')
      await waitForTableLoaded(page)
      await page.getByRole('button', { name: /新增|新建/ }).first().click()
      const dlg = await waitForDialog(page, /新增合同|新建合同/)

      const contractCode = `${CONTRACT_CODE_PREFIX}-${Date.now().toString().slice(-6)}`
      await dlg.getByLabel(/合同编号|编号/).fill(contractCode)
      await dlg.getByLabel(/合同名称|名称/).fill(`${PROJECT_NAME}-主合同`)
      // 选择项目 (下拉 + 搜索)
      if (createdProjectId) {
        const projSel = dlg.getByLabel(/项目|立项/).first()
        await projSel.click()
        await page.waitForTimeout(300)
        await page.getByText(createdProjectCode!, { exact: false }).first().click()
      }
      await dlg.getByLabel(/合同金额|金额/).fill('1000000')
      await dlg.getByRole('button', { name: /确定|保存/ }).last().click()
      await expectToast(page, /成功/i, 10_000)

      // 解析合同 ID
      await page.waitForTimeout(1_000)
      const row = page.locator('.vxe-table .vxe-body--row, .el-table__row', {
        hasText: contractCode,
      }).first()
      await expect(row).toBeVisible({ timeout: 10_000 })
    })

    // ========== 步骤 4: 结项 ==========
    await test.step('提交结项申请', async ({ loginAs }) => {
      await loginAs('admin' as E2EUser)
      await page.goto('/closure/index')
      await waitForTableLoaded(page)
      await page.getByRole('button', { name: /新增结项|新建结项|结项申请/ }).first().click()
      const dlg = await waitForDialog(page, /结项/)

      if (createdProjectId) {
        const projSel = dlg.getByLabel(/项目|立项/).first()
        await projSel.click()
        await page.waitForTimeout(300)
        await page.getByText(createdProjectCode!, { exact: false }).first().click()
      }
      await dlg.getByLabel(/结项原因|说明|备注/).fill('E2E 测试结项')
      await dlg.getByRole('button', { name: /确定|保存|提交/ }).last().click()
      await expectToast(page, /成功/i, 10_000)
    })

    await test.step('验收: 项目状态变为 CLOSED', async () => {
      await page.goto('/project/initiation')
      await waitForTableLoaded(page)
      const row = page.locator('.vxe-table .vxe-body--row, .el-table__row', {
        hasText: createdProjectCode!,
      }).first()
      // 验证状态列已变为 CLOSED 或包含"已结项"
      await expect(row).toContainText(/(已结项|CLOSED|closed)/i, { timeout: 10_000 })
    })
  })

  test('表单校验: 必填项缺失应提示', async ({ page }) => {
    await page.goto('/project/initiation')
    await waitForTableLoaded(page)
    await page.getByRole('button', { name: /新增|新建/ }).first().click()
    const dlg = await waitForDialog(page, /新增立项|新建立项/)
    // 不填任何字段, 直接点保存
    await dlg.getByRole('button', { name: /确定|保存/ }).last().click()
    // 期望看到 el-form-item__error
    await expect(dlg.locator('.el-form-item__error').first()).toBeVisible({ timeout: 3_000 })
  })

  test('权限校验: 销售角色不能新增立项', async ({ page, loginAs }) => {
    // 销售角色 (假设 username=sales), 此处复用一个普通用户测试
    await page.context().clearCookies()
    await loginAs('cfo' as E2EUser)
    await page.goto('/project/initiation')
    await waitForTableLoaded(page)
    // CFO 应当只有查看权限
    const addBtn = page.getByRole('button', { name: /新增|新建/ }).first()
    // 可能没有这个按钮, 也可能被禁用 — 任一即可
    const isVisible = await addBtn.isVisible({ timeout: 2_000 }).catch(() => false)
    if (isVisible) {
      await expect(addBtn).toBeDisabled()
    } else {
      // 如果根本没渲染新增按钮, 也算符合权限约束
      expect(isVisible).toBe(false)
    }
  })
})
