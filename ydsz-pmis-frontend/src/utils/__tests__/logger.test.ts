/**
 * @file logger.test.ts
 * @description 测试 logger 工具的不同日志级别行为
 * @vitest-environment jsdom
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'

// ========== Mock @sentry/vue ==========
const { mockCaptureException, mockCaptureMessage, mockAddBreadcrumb } = vi.hoisted(() => ({
  mockCaptureException: vi.fn(),
  mockCaptureMessage: vi.fn(),
  mockAddBreadcrumb: vi.fn(),
}))

vi.mock('@sentry/vue', () => ({
  captureException: mockCaptureException,
  captureMessage: mockCaptureMessage,
  addBreadcrumb: mockAddBreadcrumb,
}))

// 模拟 import.meta.env.PROD = false（开发环境）
vi.stubEnv('PROD', false)

describe('logger - 开发环境行为', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.stubEnv('PROD', false)
  })

  it('debug 应在开发环境调用 console.debug', async () => {
    const spy = vi.spyOn(console, 'debug').mockImplementation(() => {})
    const { logger } = await import('@/utils/logger')
    logger.debug('[Test]', 'debug message', { key: 'value' })
    expect(spy).toHaveBeenCalledWith('[Test]', 'debug message', { key: 'value' })
    spy.mockRestore()
  })

  it('info 应在开发环境调用 console.info', async () => {
    const spy = vi.spyOn(console, 'info').mockImplementation(() => {})
    const { logger } = await import('@/utils/logger')
    logger.info('[Test]', 'info message')
    expect(spy).toHaveBeenCalledWith('[Test]', 'info message')
    spy.mockRestore()
  })

  it('warn 应同时调用 console.warn 和 Sentry.addBreadcrumb', async () => {
    const spy = vi.spyOn(console, 'warn').mockImplementation(() => {})
    const { logger } = await import('@/utils/logger')
    logger.warn('[Test]', 'warning message', { detail: 'extra' })
    expect(spy).toHaveBeenCalledWith('[Test]', 'warning message', { detail: 'extra' })
    expect(mockAddBreadcrumb).toHaveBeenCalledWith(
      expect.objectContaining({
        category: '[Test]',
        level: 'warning',
        message: 'warning message',
      }),
    )
    spy.mockRestore()
  })

  it('error 应在 Error 实例时调用 Sentry.captureException', async () => {
    const spy = vi.spyOn(console, 'error').mockImplementation(() => {})
    const { logger } = await import('@/utils/logger')
    const err = new Error('test error')
    logger.error('[Test]', err, { context: 'extra' })
    expect(spy).toHaveBeenCalledWith('[Test]', err)
    expect(mockCaptureException).toHaveBeenCalledWith(err, {
      tags: { module: '[Test]' },
      extra: { context: 'extra' },
    })
    spy.mockRestore()
  })

  it('error 应在非 Error 实例时调用 Sentry.captureMessage', async () => {
    const spy = vi.spyOn(console, 'error').mockImplementation(() => {})
    const { logger } = await import('@/utils/logger')
    logger.error('[Test]', 'string error message')
    expect(spy).toHaveBeenCalledWith('[Test]', 'string error message')
    expect(mockCaptureMessage).toHaveBeenCalledWith(
      '[Test] string error message',
      'error',
    )
    spy.mockRestore()
  })
})

describe('logger - 生产环境行为', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.stubEnv('PROD', true)
  })

  it('debug 在生产环境不应输出', async () => {
    const spy = vi.spyOn(console, 'debug').mockImplementation(() => {})
    vi.resetModules()
    vi.doMock('@sentry/vue', () => ({
      captureException: mockCaptureException,
      captureMessage: mockCaptureMessage,
      addBreadcrumb: mockAddBreadcrumb,
    }))
    const { logger } = await import('@/utils/logger')
    logger.debug('[Test]', 'should not appear')
    expect(spy).not.toHaveBeenCalled()
    spy.mockRestore()
  })

  it('info 在生产环境不应输出', async () => {
    const spy = vi.spyOn(console, 'info').mockImplementation(() => {})
    vi.resetModules()
    vi.doMock('@sentry/vue', () => ({
      captureException: mockCaptureException,
      captureMessage: mockCaptureMessage,
      addBreadcrumb: mockAddBreadcrumb,
    }))
    const { logger } = await import('@/utils/logger')
    logger.info('[Test]', 'should not appear')
    expect(spy).not.toHaveBeenCalled()
    spy.mockRestore()
  })

  it('warn 在生产环境仍应输出 console.warn', async () => {
    const spy = vi.spyOn(console, 'warn').mockImplementation(() => {})
    vi.resetModules()
    vi.doMock('@sentry/vue', () => ({
      captureException: mockCaptureException,
      captureMessage: mockCaptureMessage,
      addBreadcrumb: mockAddBreadcrumb,
    }))
    const { logger } = await import('@/utils/logger')
    logger.warn('[Test]', 'production warning')
    expect(spy).toHaveBeenCalled()
    spy.mockRestore()
  })

  it('error 在生产环境仍应输出 console.error', async () => {
    const spy = vi.spyOn(console, 'error').mockImplementation(() => {})
    vi.resetModules()
    vi.doMock('@sentry/vue', () => ({
      captureException: mockCaptureException,
      captureMessage: mockCaptureMessage,
      addBreadcrumb: mockAddBreadcrumb,
    }))
    const { logger } = await import('@/utils/logger')
    logger.error('[Test]', new Error('prod error'))
    expect(spy).toHaveBeenCalled()
    spy.mockRestore()
  })
})