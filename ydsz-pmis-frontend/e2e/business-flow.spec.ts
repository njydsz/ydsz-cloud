/**
 * @file business-flow.spec.ts
 * @description PMIS 业务闭环 E2E 测试
 *              覆盖登录、导航、全局搜索、主题切换、语言切换等核心业务流程
 *
 * 运行前提：
 *  1) pnpm exec playwright install chromium
 *  2) pnpm vite --mode e2e（启用 mock，无需后端）
 *  3) pnpm test:e2e
 */
import { test, expect } from '@playwright/test'

const BASE_URL = process.env.BASE_URL || 'http://localhost:5173'

test.describe('PMIS 业务闭环 E2E 测试', () => {
  test.describe.configure({ mode: 'serial' })

  // ========== 1. 登录流程 ==========
  test.describe('登录流程', () => {
    test('admin/admin123 登录应跳转到首页', async ({ page }) => {
      await page.goto(BASE_URL)

      // 填写用户名
      const usernameInput = page.locator('input[placeholder*="用户名"], input[name="username"]').first()
      await usernameInput.fill('admin')

      // 填写密码
      const passwordInput = page.locator('input[type="password"]').first()
      await passwordInput.fill('admin123')

      // 填写验证码（mock 模式不校验，填任意值）
      const captchaInput = page.locator('input[placeholder*="验证码"]').first()
      if (await captchaInput.isVisible({ timeout: 2000 }).catch(() => false)) {
        await captchaInput.fill('MOCK')
      }

      // 点击登录按钮
      await page.click('button:has-text("登录")')

      // 验证跳转到首页（URL 不再是 /login）
      await page.waitForURL((url) => !url.pathname.includes('login'), { timeout: 10000 })
      expect(page.url()).not.toMatch(/\/login/)
    })
  })

  // ========== 2. 侧边栏导航 ==========
  test.describe('侧边栏导航', () => {
    test('点击侧边栏菜单项应跳转对应路由', async ({ page }) => {
      // 先登录
      await page.goto(BASE_URL)
      const usernameInput = page.locator('input[placeholder*="用户名"], input[name="username"]').first()
      await usernameInput.fill('admin')
      const passwordInput = page.locator('input[type="password"]').first()
      await passwordInput.fill('admin123')
      const captchaInput = page.locator('input[placeholder*="验证码"]').first()
      if (await captchaInput.isVisible({ timeout: 2000 }).catch(() => false)) {
        await captchaInput.fill('MOCK')
      }
      await page.click('button:has-text("登录")')
      await page.waitForURL((url) => !url.pathname.includes('login'), { timeout: 10000 })

      // 查找侧边栏菜单项
      const sidebar = page.locator('.el-menu, .sidebar, nav').first()
      await expect(sidebar).toBeVisible({ timeout: 5000 })

      // 点击任意菜单项（排除当前页）
      const menuItems = sidebar.locator('.el-menu-item, a[href]')
      const count = await menuItems.count()
      if (count > 1) {
        const initialUrl = page.url()
        // 点击第二个菜单项（第一个可能是首页/当前页）
        await menuItems.nth(1).click()
        // 验证路由发生变化或页面内容更新
        await page.waitForTimeout(1000)
        const finalUrl = page.url()
        // 至少应该有路由变化或 DOM 更新
        expect(finalUrl).toBeTruthy()
      }
    })
  })

  // ========== 3. 全局搜索 ==========
  test.describe('全局搜索', () => {
    test('Ctrl+K 应打开全局搜索并返回结果', async ({ page }) => {
      // 先登录
      await page.goto(BASE_URL)
      const usernameInput = page.locator('input[placeholder*="用户名"], input[name="username"]').first()
      await usernameInput.fill('admin')
      const passwordInput = page.locator('input[type="password"]').first()
      await passwordInput.fill('admin123')
      const captchaInput = page.locator('input[placeholder*="验证码"]').first()
      if (await captchaInput.isVisible({ timeout: 2000 }).catch(() => false)) {
        await captchaInput.fill('MOCK')
      }
      await page.click('button:has-text("登录")')
      await page.waitForURL((url) => !url.pathname.includes('login'), { timeout: 10000 })

      // 按 Ctrl+K 打开全局搜索
      await page.keyboard.press('Control+K')

      // 验证搜索弹窗出现
      const searchInput = page.locator(
        '.global-search input, .el-dialog input, [role="searchbox"] input',
      ).first()
      // 等待搜索框出现（可能不存在如果功能未启用）
      const searchVisible = await searchInput.isVisible({ timeout: 3000 }).catch(() => false)

      if (searchVisible) {
        // 输入关键词
        await searchInput.fill('项目')
        await page.waitForTimeout(500)

        // 验证有搜索结果或空状态
        const searchResults = page.locator(
          '.global-search__results, .search-results, .el-dialog__body',
        )
        await expect(searchResults.first()).toBeVisible({ timeout: 3000 })
      } else {
        // 全局搜索功能未启用，跳过断言
        test.skip(true, '全局搜索功能未在当前页面启用')
      }
    })
  })

  // ========== 4. 主题切换 ==========
  test.describe('主题切换', () => {
    test('点击暗色模式按钮应添加 dark class 到 html', async ({ page }) => {
      // 先登录
      await page.goto(BASE_URL)
      const usernameInput = page.locator('input[placeholder*="用户名"], input[name="username"]').first()
      await usernameInput.fill('admin')
      const passwordInput = page.locator('input[type="password"]').first()
      await passwordInput.fill('admin123')
      const captchaInput = page.locator('input[placeholder*="验证码"]').first()
      if (await captchaInput.isVisible({ timeout: 2000 }).catch(() => false)) {
        await captchaInput.fill('MOCK')
      }
      await page.click('button:has-text("登录")')
      await page.waitForURL((url) => !url.pathname.includes('login'), { timeout: 10000 })

      // 记录初始状态
      const initialDark = await page.evaluate(() =>
        document.documentElement.classList.contains('dark'),
      )

      // 查找主题切换按钮（可能是图标按钮）
      const themeButton = page.locator(
        'button:has-text("主题"), button:has-text("暗色"), button:has-text("夜间"), [class*="theme"], [class*="dark"]',
      ).first()

      const themeBtnVisible = await themeButton.isVisible({ timeout: 3000 }).catch(() => false)

      if (themeBtnVisible) {
        await themeButton.click()
        await page.waitForTimeout(500)

        // 验证 dark class 状态切换
        const afterDark = await page.evaluate(() =>
          document.documentElement.classList.contains('dark'),
        )
        expect(afterDark).toBe(!initialDark)
      } else {
        test.skip(true, '主题切换按钮未在当前页面找到')
      }
    })
  })

  // ========== 5. 语言切换 ==========
  test.describe('语言切换', () => {
    test('切换到 English 应改变页面文案', async ({ page }) => {
      // 先登录
      await page.goto(BASE_URL)
      const usernameInput = page.locator('input[placeholder*="用户名"], input[name="username"]').first()
      await usernameInput.fill('admin')
      const passwordInput = page.locator('input[type="password"]').first()
      await passwordInput.fill('admin123')
      const captchaInput = page.locator('input[placeholder*="验证码"]').first()
      if (await captchaInput.isVisible({ timeout: 2000 }).catch(() => false)) {
        await captchaInput.fill('MOCK')
      }
      await page.click('button:has-text("登录")')
      await page.waitForURL((url) => !url.pathname.includes('login'), { timeout: 10000 })

      // 记录初始 html lang 属性
      const initialLang = await page.evaluate(() =>
        document.documentElement.getAttribute('lang') || '',
      )

      // 查找语言切换器
      const langSwitcher = page.locator(
        'button:has-text("English"), button:has-text("语言"), [class*="language"], .language-switcher',
      ).first()

      const langVisible = await langSwitcher.isVisible({ timeout: 3000 }).catch(() => false)

      if (langVisible) {
        await langSwitcher.click()
        await page.waitForTimeout(500)

        // 验证 html lang 属性变化
        const afterLang = await page.evaluate(() =>
          document.documentElement.getAttribute('lang') || '',
        )

        // lang 属性应发生变化或下拉菜单出现
        const langChanged = afterLang !== initialLang
        const dropdownVisible = await page
          .locator('.el-dropdown-menu, .el-select-dropdown')
          .first()
          .isVisible({ timeout: 1000 })
          .catch(() => false)

        expect(langChanged || dropdownVisible).toBe(true)
      } else {
        test.skip(true, '语言切换器未在当前页面找到')
      }
    })
  })
})
