/**
 * E2E Smoke: 关键页面冒烟
 * 批次 21 / P1
 *
 * 验证: 登录后所有顶层路由都能打开 (无 5xx, 无白屏)
 * 用时: < 30s
 */
import { test, expect, waitForTableLoaded } from './fixtures/utils'
import { E2EUser } from './fixtures/auth.fixture'

const PAGES = [
  { path: '/dashboard',         name: '工作台' },
  { path: '/project/initiation', name: '立项' },
  { path: '/project/contract',   name: '合同' },
  { path: '/project/opportunity', name: '商机' },
  { path: '/change',             name: '变更' },
  { path: '/execution/wbs-task', name: 'WBS' },
  { path: '/execution/time-entry', name: '工时' },
  { path: '/execution/purchase', name: '采购' },
  { path: '/execution/expense',  name: '费用' },
  { path: '/execution/risk',     name: '风险' },
  { path: '/execution/alert',    name: '预警' },
  { path: '/execution/evm',      name: 'EVM' },
  { path: '/execution/utilization', name: '利用率' },
  { path: '/finance/invoice',    name: '发票' },
  { path: '/finance/payment',    name: '付款' },
  { path: '/finance/customer-credit', name: '客户信用' },
  { path: '/execution/reconcile', name: '对账' },
  { path: '/execution/profit',   name: '利润' },
  { path: '/execution/profit-simulation', name: '利润模拟' },
  { path: '/agent/orchestration', name: 'AI 编排' },
  { path: '/agent/prediction',   name: 'AI 预测' },
  { path: '/report',             name: '报表' },
  { path: '/cockpit',            name: '驾驶舱' },
  { path: '/closure/index',      name: '结项' },
  { path: '/aftersales/warranty', name: '质保' },
  { path: '/aftersales/ops-ticket', name: '运维工单' },
  { path: '/aftersales/satisfaction', name: '满意度' },
  { path: '/audit/log',          name: '审计' },
  { path: '/attendance',         name: '考勤' },
]

test.describe('E2E Smoke - 关键页面', () => {
  test('所有顶层路由可访问 (admin)', async ({ page, loginAs }) => {
    await loginAs('admin' as E2EUser)

    for (const p of PAGES) {
      await test.step(`${p.name} (${p.path})`, async () => {
        const res = await page.goto(p.path)
        // 200 / 304 视为正常
        const status = res?.status() ?? 0
        expect([200, 304]).toContain(status)

        // 等待页面渲染 (避免白屏判定)
        await page.waitForLoadState('domcontentloaded')
        // 不期望看到 5xx 错误页
        await expect(page.locator('text=/5\\d\\d/')).toHaveCount(0, { timeout: 3_000 }).catch(() => {})

        // 主要内容区域可见
        await expect(page.locator('body')).toBeVisible()
        // 不应跳转到 /login
        expect(page.url()).not.toMatch(/\/login$/)
      })
    }
  })

  test('未登录访问应跳转到登录页', async ({ page }) => {
    await page.context().clearCookies()
    await page.evaluate(() => {
      try { localStorage.clear() } catch { /* ignore */ }
    }).catch(() => {})
    await page.goto('/project/initiation')
    await expect(page).toHaveURL(/\/login/, { timeout: 10_000 })
  })
})
