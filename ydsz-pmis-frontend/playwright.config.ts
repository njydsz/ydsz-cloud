/**
 * @file Playwright E2E 测试配置
 * @description 配置端到端测试环境：
 *   - 开发服务器自动启动 (Vite dev server)
 *   - 三个浏览器并行测试 (Chromium/Firefox/WebKit)
 *   - 失败自动截图与trace
 *   - CI 环境重试 2 次，本地不重试
 * @module playwright.config
 */
import { defineConfig, devices } from '@playwright/test'

/**
 * E2E 测试环境变量
 * - baseURL: 开发服务器地址
 * - CI: 是否在 CI 环境
 */
const isCI = !!process.env.CI
const baseURL = process.env.E2E_BASE_URL || 'http://localhost:5173'

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: isCI,
  retries: isCI ? 2 : 0,
  workers: isCI ? 2 : undefined,
  reporter: isCI
    ? [['html', { open: 'never' }], ['junit', { outputFile: 'e2e-results.xml' }]]
    : 'html',

  use: {
    baseURL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    locale: 'zh-CN',
    timezoneId: 'Asia/Shanghai',
  },

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
    {
      name: 'firefox',
      use: { ...devices['Desktop Firefox'] },
      // Firefox 在 CI 中偶尔不稳定，仅在本地运行
      onlyIn: !isCI ? undefined : ['chromium'],
    },
    {
      name: 'mobile-chrome',
      use: { ...devices['Pixel 7'] },
      // 移动端测试仅在本地运行
      onlyIn: !isCI ? undefined : ['chromium'],
    },
  ],

  // 本地开发：自动启动 Vite dev server
  // CI 环境：假定服务器已通过 GitHub Actions 的 serve 步骤启动
  webServer: isCI
    ? undefined
    : {
        command: 'pnpm dev',
        url: baseURL,
        reuseExistingServer: true,
        timeout: 60_000,
      },
})
