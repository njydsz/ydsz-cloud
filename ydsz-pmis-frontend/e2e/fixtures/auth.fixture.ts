import { test as base, expect, type Page } from '@playwright/test'

/**
 * E2E 测试基类
 * 批次 21 / P1 - 通用 fixtures: 登录、登出
 *
 * 默认账号 (E2E 环境):
 *   admin / admin123  - 超管
 *   pm   / pm123      - PM
 *   cfo  / cfo123     - CFO
 *
 * 通过环境变量 E2E_* 覆盖
 */

export type E2EUser = 'admin' | 'pm' | 'cfo' | 'risk' | 'agent'

export interface E2EFixtures {
  loginAs: (user: E2EUser) => Promise<void>
  logout: () => Promise<void>
  authenticatedPage: Page
  apiBaseURL: string
}

const USERS: Record<E2EUser, { username: string; password: string }> = {
  admin: { username: 'admin', password: 'admin123' },
  pm:    { username: 'pm',    password: 'pm123' },
  cfo:   { username: 'cfo',   password: 'cfo123' },
  risk:  { username: 'risk',  password: 'risk123' },
  agent: { username: 'agent', password: 'agent123' },
}

export const test = base.extend<E2EFixtures>({
  apiBaseURL: async ({}, use) => {
    await use(process.env.E2E_API_BASE_URL || 'http://localhost:9001')
  },

  authenticatedPage: async ({ page }, use) => {
    // 默认登录为 admin
    await loginViaUI(page, 'admin')
    await use(page)
  },

  loginAs: async ({ page }, use) => {
    await use(async (user: E2EUser) => {
      await loginViaUI(page, user)
    })
  },

  logout: async ({ page }, use) => {
    await use(async () => {
      await page.context().clearCookies()
      await page.evaluate(() => {
        try { localStorage.clear() } catch { /* ignore */ }
        try { sessionStorage.clear() } catch { /* ignore */ }
      })
    })
  },
})

/**
 * 通过 UI 登录 (适用于真实集成)
 * 通过 E2E_LOGIN_VIA_API=1 走 API 直登, 速度更快且更稳定
 */
export async function loginViaUI(page: Page, user: E2EUser) {
  if (process.env.E2E_LOGIN_VIA_API === '1') {
    return loginViaAPI(page, user)
  }
  const { username, password } = USERS[user]
  await page.goto('/login')
  await page.getByPlaceholder(/请输入用户名|用户名|account/i).fill(username)
  await page.getByPlaceholder(/请输入密码|密码|password/i).fill(password)
  await page.getByRole('button', { name: /登录|login|sign in/i }).click()
  await page.waitForURL(/\/(dashboard|home|workbench|index)/i, { timeout: 15_000 })
}

/**
 * 通过 API 登录, 注入 cookie + localStorage
 */
export async function loginViaAPI(page: Page, user: E2EUser) {
  const { username, password } = USERS[user]
  const apiBase = process.env.E2E_API_BASE_URL || 'http://localhost:9001'

  // POST 登录
  const res = await page.request.post(`${apiBase}/api/v1/auth/login`, {
    data: { username, password },
    headers: { 'Content-Type': 'application/json' },
  })
  expect(res.status(), `login ${user}`).toBe(200)
  const body = await res.json()
  const token = body.data?.token || body.data?.accessToken
  if (!token) {
    throw new Error(`登录响应缺少 token: ${JSON.stringify(body)}`)
  }

  // 注入 localStorage
  await page.addInitScript((t) => {
    try { localStorage.setItem('pmis_token', t) } catch { /* ignore */ }
    try { localStorage.setItem('token', t) } catch { /* ignore */ }
  }, token)
}

export { expect }
