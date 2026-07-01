/**
 * @file Sentry 集成 单元测试 (批次 20 P2-1)
 * @description Sentry SDK 动态导入，仅测试降级路径与状态管理：
 *   1. 降级路径: DSN 为空时不报错
 *   2. 降级路径: DSN 无效时 captureError 走 console.error
 *   3. 状态管理: setUser / closeSentry 不抛错
 * @module utils/__tests__/sentry
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'

describe('sentry 工具 (降级模式)', () => {
  let consoleSpy: ReturnType<typeof vi.spyOn>

  beforeEach(() => {
    consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  afterEach(() => {
    consoleSpy.mockRestore()
  })

  it('DSN 为空时 initSentry 跳过初始化, 不报错', async () => {
    const { initSentry } = await import('../sentry')
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {})
    try {
      await initSentry({ dsn: '', environment: 'test' })
      expect(warnSpy).toHaveBeenCalled()
    } finally {
      warnSpy.mockRestore()
    }
  })

  it('未初始化时 captureError 走 console.error 降级', async () => {
    const { captureError } = await import('../sentry')
    const err = new Error('test error')
    captureError(err, { foo: 'bar' })
    expect(consoleSpy).toHaveBeenCalled()
    const callArgs = consoleSpy.mock.calls.find(
      (c) => c[0] === '[fallback]' || (typeof c[0] === 'string' && c[0].includes('[fallback]')),
    )
    expect(callArgs).toBeTruthy()
  })

  it('未初始化时 setUser 静默 noop', async () => {
    const { setUser } = await import('../sentry')
    expect(() => setUser({ id: 1, username: 'admin' })).not.toThrow()
    expect(() => setUser(null)).not.toThrow()
  })

  it('未初始化时 addBreadcrumb 静默 noop', async () => {
    const { addBreadcrumb } = await import('../sentry')
    expect(() => addBreadcrumb('test', 'message')).not.toThrow()
  })

  it('closeSentry 幂等安全', async () => {
    const { closeSentry } = await import('../sentry')
    expect(() => closeSentry()).not.toThrow()
    expect(() => closeSentry()).not.toThrow()
  })
})
