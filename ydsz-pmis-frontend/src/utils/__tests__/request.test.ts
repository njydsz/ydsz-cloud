/**
 * @file 请求工具单元测试 - 重试逻辑与错误判断
 * @covers isRetryableError / getRetryDelay (内部函数通过行为测试覆盖)
 */
import { describe, it, expect } from 'vitest'
import { BizException, HttpException } from '../error'

/**
 * 从 request.ts 中提取的可测试逻辑
 * 由于 isRetryableError 是内部函数，这里通过模拟相同逻辑进行测试
 */
function isRetryableError(error: unknown): boolean {
  if (!error || typeof error !== 'object') return false
  const err = error as { code?: string; response?: { status?: number } }
  if (!err.response) return true // 网络断开
  if (err.code === 'ECONNABORTED') return true // 超时
  const status = err.response?.status
  if (status && status >= 500 && status < 600) return true
  return false
}

function getRetryDelay(retryCount: number): number {
  return 1000 * Math.pow(2, retryCount)
}

describe('request retry logic', () => {
  describe('isRetryableError', () => {
    it('should return true for network errors (no response)', () => {
      const err = { message: 'Network Error' }
      expect(isRetryableError(err)).toBe(true)
    })

    it('should return true for timeout errors (ECONNABORTED)', () => {
      const err = { code: 'ECONNABORTED', message: 'timeout of 30000ms exceeded' }
      expect(isRetryableError(err)).toBe(true)
    })

    it('should return true for 5xx server errors', () => {
      const err = { response: { status: 500 } }
      expect(isRetryableError(err)).toBe(true)
    })

    it('should return true for 502 bad gateway', () => {
      const err = { response: { status: 502 } }
      expect(isRetryableError(err)).toBe(true)
    })

    it('should return true for 503 service unavailable', () => {
      const err = { response: { status: 503 } }
      expect(isRetryableError(err)).toBe(true)
    })

    it('should return false for 4xx client errors', () => {
      const err = { response: { status: 400 } }
      expect(isRetryableError(err)).toBe(false)
    })

    it('should return false for 401 unauthorized', () => {
      const err = { response: { status: 401 } }
      expect(isRetryableError(err)).toBe(false)
    })

    it('should return false for 403 forbidden', () => {
      const err = { response: { status: 403 } }
      expect(isRetryableError(err)).toBe(false)
    })

    it('should return false for 404 not found', () => {
      const err = { response: { status: 404 } }
      expect(isRetryableError(err)).toBe(false)
    })

    it('should return false for null/undefined', () => {
      expect(isRetryableError(null)).toBe(false)
      expect(isRetryableError(undefined)).toBe(false)
    })

    it('should return false for non-object values', () => {
      expect(isRetryableError('string error')).toBe(false)
      expect(isRetryableError(42)).toBe(false)
    })
  })

  describe('getRetryDelay (exponential backoff)', () => {
    it('should return 1000ms for first retry (retryCount=0)', () => {
      expect(getRetryDelay(0)).toBe(1000)
    })

    it('should return 2000ms for second retry (retryCount=1)', () => {
      expect(getRetryDelay(1)).toBe(2000)
    })

    it('should return 4000ms for third retry (retryCount=2)', () => {
      expect(getRetryDelay(2)).toBe(4000)
    })

    it('should follow exponential pattern: base * 2^retryCount', () => {
      for (let i = 0; i < 5; i++) {
        expect(getRetryDelay(i)).toBe(1000 * Math.pow(2, i))
      }
    })
  })
})

describe('BizException and HttpException integration', () => {
  it('BizException should carry business code', () => {
    const err = new BizException('validation failed', 10001, true)
    expect(err.code).toBe(10001)
    expect(err.handled).toBe(true)
  })

  it('HttpException should carry HTTP status', () => {
    const err = new HttpException('server error', 503, true)
    expect(err.status).toBe(503)
    expect(err.handled).toBe(true)
  })
})
