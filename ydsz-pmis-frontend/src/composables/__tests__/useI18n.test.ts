/**
 * i18n 单元测试 (批次 20 P2-2)
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'

describe('i18n', () => {
  beforeEach(() => {
    // 重置 localStorage
    if (typeof window !== 'undefined') {
      window.localStorage.clear()
    }
    vi.resetModules()
  })

  it('默认 locale 是 zh-CN', async () => {
    const { locale } = await import('../useI18n')
    expect(locale.value).toBe('zh-CN')
  })

  it('t(common.save) 返回中文', async () => {
    const { t } = await import('../useI18n')
    expect(t('common.save')).toBe('保存')
  })

  it('t(user.welcome, { name }) 正确插值', async () => {
    const { t } = await import('../useI18n')
    expect(t('user.welcome', { name: 'admin' })).toBe('欢迎, admin')
  })

  it('切换到 en-US 后翻译为英文', async () => {
    const { t, setLocale } = await import('../useI18n')
    setLocale('en-US')
    expect(t('common.save')).toBe('Save')
    expect(t('user.welcome', { name: 'admin' })).toBe('Welcome, admin')
  })

  it('缺失 key 返回 key 本身 + 开发环境警告', async () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    const { t } = await import('../useI18n')
    expect(t('nonexistent.key')).toBe('nonexistent.key')
    if (import.meta.env.DEV) {
      expect(warn).toHaveBeenCalled()
    }
    warn.mockRestore()
  })

  it('变量插值中缺失的 key 保留原样', async () => {
    const { t } = await import('../useI18n')
    expect(t('user.welcome', {})).toBe('欢迎, {name}')
  })

  it('supportedLocales 至少包含 zh-CN 和 en-US', async () => {
    const { supportedLocales } = await import('../useI18n')
    const codes = supportedLocales.map((l) => l.code)
    expect(codes).toContain('zh-CN')
    expect(codes).toContain('en-US')
  })

  it('setLocale 写入 localStorage', async () => {
    const { setLocale } = await import('../useI18n')
    setLocale('en-US')
    expect(window.localStorage.getItem('pmis_locale')).toBe('en-US')
  })
})
