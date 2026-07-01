/**
 * E2E 通用工具
 */
import { expect, type Page, type Locator } from '@playwright/test'

/** 等待 loading 消失 */
export async function waitForTableLoaded(page: Page, tableSelector = '.vxe-table') {
  // 关闭可能的 loading 蒙层
  const loading = page.locator('.vxe-table--loading, .el-loading-mask, [v-loading]').first()
  if (await loading.isVisible({ timeout: 500 }).catch(() => false)) {
    await loading.waitFor({ state: 'hidden', timeout: 15_000 }).catch(() => {})
  }
  await page.waitForSelector(tableSelector, { state: 'visible', timeout: 10_000 })
}

/** 通过文本点击行操作按钮 */
export async function clickRowAction(page: Page, rowText: string | RegExp, actionText: string) {
  const row = page.locator('.vxe-table .vxe-body--row, .el-table .el-table__row', {
    hasText: rowText,
  }).first()
  await row.locator('button', { hasText: new RegExp(actionText) }).first().click()
}

/** 关闭 ElMessage 弹窗, 避免遮挡 */
export async function dismissMessages(page: Page) {
  await page.evaluate(() => {
    document.querySelectorAll('.el-message, .el-notification').forEach((n) => n.remove())
  })
}

/** 等待 dialog 出现 */
export async function waitForDialog(page: Page, title?: string | RegExp) {
  const dlg = page.locator('.el-dialog').filter({
    has: title ? page.locator('.el-dialog__title', { hasText: title }) : undefined,
  }).first()
  await dlg.waitFor({ state: 'visible', timeout: 5_000 })
  return dlg
}

/** 在 dialog 中点击确认按钮 */
export async function confirmDialog(page: Page) {
  const dlg = page.locator('.el-dialog__wrapper:not([style*="display: none"])').last()
  await dlg.locator('.el-dialog__footer .el-button--primary').click()
}

/** 断言 toast 文本 */
export async function expectToast(page: Page, text: string | RegExp, timeout = 5_000) {
  const msg = page.locator('.el-message', { hasText: text }).first()
  await msg.waitFor({ state: 'visible', timeout })
  await expect(msg).toBeVisible()
  return msg
}

export { expect, type Page, type Locator }
