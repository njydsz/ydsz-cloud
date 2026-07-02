import { defineConfig, devices } from '@playwright/test'

/**
 * @file playwright.config.ts
 * @description Playwright E2E 测试配置, 负责测试目录、并发策略、浏览器工程、报告产物及本地 dev server 拉起.
 *              覆盖批次 21 / P1 的 3 条核心业务流 (立项-合同-结项、风险预警、AI 编排) E2E.
 * @module ydsz-pmis-frontend/playwright.config.ts
 *
 * 启动方式:
 *   pnpm exec playwright install chromium   # 一次性安装浏览器
 *   pnpm run test:e2e                        # 跑全部
 *   pnpm run test:e2e:headed                 # 显示浏览器
 *   pnpm run test:e2e:ui                     # 调试 UI
 */
export default defineConfig({
  testDir: './e2e', // E2E 用例根目录
  testMatch: /.*\.spec\.ts/, // 仅识别 *.spec.ts 作为用例文件
  fullyParallel: true, // 文件级并行执行, 提升整体执行速度
  forbidOnly: !!process.env.CI, // CI 环境禁止 test.only, 避免遗漏用例
  retries: process.env.CI ? 2 : 0, // CI 失败重试 2 次, 本地不重试以便快速暴露问题
  workers: process.env.CI ? 1 : undefined, // CI 单线程串行, 本地按 CPU 自动决定
  // 报告策略: CI 输出 github annotation + html + json; 本地输出 list + html
  reporter: process.env.CI
    ? [['github'], ['html', { open: 'never' }], ['json', { outputFile: 'e2e-report/report.json' }]]
    : [['list'], ['html', { open: 'never' }]],
  outputDir: './e2e-report/test-results', // 失败产物 (trace/screenshot/video) 输出目录
  timeout: 30_000, // 单个用例超时 30s
  expect: { timeout: 5_000 }, // 断言重试超时 5s
  use: {
    // 被测站点地址, 可通过 E2E_BASE_URL 覆盖 (默认指向本地 dev server)
    baseURL: process.env.E2E_BASE_URL || 'http://localhost:5173',
    trace: 'on-first-retry', // 首次重试时录制 trace, 便于定位 flaky
    screenshot: 'only-on-failure', // 仅失败时截图, 减少产物体积
    video: 'retain-on-failure', // 仅失败用例保留视频
    actionTimeout: 10_000, // 单个交互动作 (click/fill) 超时 10s
    navigationTimeout: 20_000, // 页面跳转超时 20s
  },
  projects: [
    {
      name: 'chromium', // 桌面 Chrome 工程
      use: {
        ...devices['Desktop Chrome'],
        viewport: { width: 1440, height: 900 }, // 与设计稿基准分辨率一致
        locale: 'zh-CN', // 中文环境, 校验日期/数字格式
        timezoneId: 'Asia/Shanghai', // 时区锁定, 避免时间相关断言因时区漂移
      },
    },
    // P2-14: Firefox 跨浏览器覆盖
    // Firefox 渲染引擎与 Chromium 不同（Gecko vs Blink），可暴露 CSS 兼容性、
    // 日期格式化、localStorage 序列化等差异。CI 中并行执行不增加墙钟时间。
    {
      name: 'firefox', // 桌面 Firefox 工程
      use: {
        ...devices['Desktop Firefox'],
        viewport: { width: 1440, height: 900 },
        locale: 'zh-CN',
        timezoneId: 'Asia/Shanghai',
      },
    },
    // P2-14: 移动端响应式覆盖（iPhone 14 视口）
    // 验证 vxe-table/Element Plus 在窄屏下的横向滚动、抽屉式筛选、底部导航等适配
    {
      name: 'mobile-chrome', // 移动 Chrome 工程
      use: {
        ...devices['Pixel 7'],
        locale: 'zh-CN',
        timezoneId: 'Asia/Shanghai',
      },
    },
    // 未来扩展:
    // { name: 'webkit',  use: { ...devices['Desktop Safari'] } },
  ],
  // 自动拉起 dev server (除非显式设置 E2E_NO_WEBSERVER); CI 中不复用既有 server
  webServer: process.env.E2E_NO_WEBSERVER
    ? undefined
    : {
        command: 'pnpm run dev --mode e2e', // 使用 e2e 模式启动, 加载 .env.e2e
        url: 'http://localhost:5173', // 健康检查 URL, 可访问即视为就绪
        reuseExistingServer: !process.env.CI, // 本地复用已启动的 dev server, 加速迭代
        timeout: 120_000, // dev server 启动超时 120s (兼容冷启动)
        stdout: 'pipe', // 捕获 stdout 用于排障
        stderr: 'pipe', // 捕获 stderr 用于排障
      },
})
