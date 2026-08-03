/**
 * E2E 测试（Playwright）配置。
 * 
 * @remarks
 * 定义浏览器项目、基准 URL 与测试目录，用于微前端应用端到端测试。
 * 
 * @author ydsz-team
 * @since 1.0.0
 */
import { defineConfig, devices } from '@playwright/test';

/**
 * YDSZ 前端 E2E 测试配置
 *
 * P1-2: 前端 E2E 测试接入 CI
 * 覆盖核心链路: 登录 → 首页 → 子应用导航
 *
 * @author ydsz-team
 * @since 1.0.0
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: [
    ['html', { outputFolder: 'playwright-report' }],
    ['list'],
  ],
  snapshotPathTemplate: '{testDir}/{testFileDir}/__screenshots__/{platform}/{projectName}/{arg}{ext}',
  use: {
    baseURL: process.env.BASE_URL || 'http://localhost:8080',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    locale: 'zh-CN',
    timezone: 'Asia/Shanghai',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: process.env.CI
    ? undefined
    : {
        command: 'pnpm dev:main',
        url: 'http://localhost:8080',
        reuseExistingServer: true,
        timeout: 120000,
      },
});
