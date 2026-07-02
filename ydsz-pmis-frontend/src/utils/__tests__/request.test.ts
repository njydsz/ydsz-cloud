/**
 * @file request 请求封装 单元测试
 * @description 验证 axios service 拦截器逻辑：
 *  - 请求拦截器注入 Authorization / X-Trace-Id / 全局 loading
 *  - 响应拦截器 code=0/200 resolve、业务失败 reject BizException、401 触发 clearAuth
 *  - silent 模式跳过全局 loading
 *  - 错误去重：BizException/HttpException 携带 handled=true
 * @module utils/__tests__/request
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'

// Mock element-plus：ElMessage + ElLoading（使用 vi.hoisted 避免 TDZ 报错）
const { mockElMessageError, mockLoadingClose, mockElLoadingService } = vi.hoisted(() => ({
  mockElMessageError: vi.fn(),
  mockLoadingClose: vi.fn(),
  mockElLoadingService: vi.fn(() => ({ close: mockLoadingClose })),
}))

vi.mock('element-plus', () => ({
  ElMessage: {
    success: vi.fn(),
    error: mockElMessageError,
    warning: vi.fn(),
    info: vi.fn(),
  },
  ElLoading: {
    service: mockElLoadingService,
  },
}))

/** 用户 store mock：仅暴露 clearAuth 供 401 拦截器调用 */
const { mockUserStore } = vi.hoisted(() => ({
  mockUserStore: { clearAuth: vi.fn() },
}))

vi.mock('@/store/modules/user', () => ({
  useUserStore: () => mockUserStore,
}))

/** getToken mock：控制请求拦截器是否注入 Authorization 头 */
const { getTokenMock } = vi.hoisted(() => ({
  getTokenMock: vi.fn(),
}))

vi.mock('@/utils/auth', () => ({
  getToken: () => getTokenMock(),
}))

// Mock traceId 生成，保证断言可预测
vi.mock('@/utils/trace', () => ({
  generateTraceId: () => 'mock-trace-id-001',
}))

import { service } from '@/utils/request'
import { BizException, HttpException, isHandledError } from '@/utils/error'

describe('request 拦截器逻辑', () => {
  beforeEach(async () => {
    vi.clearAllMocks()
    expect(service).toBeDefined()
    expect(service.interceptors.request).toBeDefined()
    expect(service.interceptors.response).toBeDefined()
  })

  it('service.interceptors.request 应可用', () => {
    expect(typeof service.interceptors.request.use).toBe('function')
  })

  it('service.interceptors.response 应可用', () => {
    expect(typeof service.interceptors.response.use).toBe('function')
  })

  it('通过 mock adapter 验证请求拦截器: 注入 Authorization + X-Trace-Id', async () => {
    let capturedConfig: any = null
    service.defaults.adapter = (config: any) => {
      capturedConfig = config
      return Promise.resolve({
        data: JSON.stringify({ code: 0, message: 'ok', data: 'mock' }),
        status: 200,
        statusText: 'OK',
        headers: {},
        config,
      })
    }
    getTokenMock.mockReturnValue('jwt-token-xyz')

    await service.request({ url: '/test', method: 'GET' })

    expect(capturedConfig.headers.Authorization).toBe('Bearer jwt-token-xyz')
    expect(capturedConfig.headers['X-Trace-Id']).toBe('mock-trace-id-001')
  })

  it('通过 mock adapter 验证请求拦截器: 无 token 不设置 Authorization', async () => {
    let capturedConfig: any = null
    service.defaults.adapter = (config: any) => {
      capturedConfig = config
      return Promise.resolve({
        data: JSON.stringify({ code: 0, message: 'ok', data: 'mock' }),
        status: 200,
        statusText: 'OK',
        headers: {},
        config,
      })
    }
    getTokenMock.mockReturnValue(null)

    await service.request({ url: '/test', method: 'GET' })

    expect(capturedConfig.headers.Authorization).toBeUndefined()
    expect(capturedConfig.headers['X-Trace-Id']).toBe('mock-trace-id-001')
  })

  it('通过 mock adapter 验证响应拦截器: code=0 resolve 数据', async () => {
    service.defaults.adapter = () =>
      Promise.resolve({
        data: JSON.stringify({ code: 0, message: 'ok', data: { value: 99 } }),
        status: 200,
        statusText: 'OK',
        headers: {},
        config: {} as any,
      })
    const res: any = await service.request({ url: '/test' })
    expect(res.data.value).toBe(99)
  })

  it('通过 mock adapter 验证响应拦截器: code=200 resolve 数据', async () => {
    service.defaults.adapter = () =>
      Promise.resolve({
        data: JSON.stringify({ code: 200, message: 'ok', data: 42 }),
        status: 200,
        statusText: 'OK',
        headers: {},
        config: {} as any,
      })
    const res: any = await service.request({ url: '/test' })
    expect(res.data).toBe(42)
  })

  // ==================== P1-7: BizException 错误去重 ====================

  it('通过 mock adapter 验证响应拦截器: 业务失败 reject BizException(handled=true)', async () => {
    service.defaults.adapter = () =>
      Promise.resolve({
        data: JSON.stringify({ code: 500, message: '业务错误' }),
        status: 200,
        statusText: 'OK',
        headers: {},
        config: {} as any,
      })
    const error = await service.request({ url: '/test' }).catch((e: unknown) => e)
    expect(error).toBeInstanceOf(BizException)
    expect((error as BizException).code).toBe(500)
    expect((error as BizException).handled).toBe(true)
    expect(isHandledError(error)).toBe(true)
    expect(mockElMessageError).toHaveBeenCalledWith('业务错误')
  })

  it('通过 mock adapter 验证响应拦截器: 401 系列 code 触发 clearAuth + reject BizException', async () => {
    for (const code of [20001, 20002, 20003]) {
      service.defaults.adapter = () =>
        Promise.resolve({
          data: JSON.stringify({ code, message: 'token失效' }),
          status: 200,
          statusText: 'OK',
          headers: {},
          config: {} as any,
        })
      const error = await service.request({ url: '/test' }).catch((e: unknown) => e)
      expect(error).toBeInstanceOf(BizException)
      expect((error as BizException).code).toBe(code)
    }
    expect(mockUserStore.clearAuth).toHaveBeenCalledTimes(3)
  })

  it('通过 mock adapter 验证响应错误拦截: HTTP 401 触发 clearAuth + reject HttpException', async () => {
    service.defaults.adapter = () => {
      const err: any = new Error('Request failed')
      err.response = { status: 401, data: { message: 'token失效' } }
      err.config = {}
      return Promise.reject(err)
    }
    const error = await service.request({ url: '/test' }).catch((e: unknown) => e)
    expect(error).toBeInstanceOf(HttpException)
    expect((error as HttpException).status).toBe(401)
    expect(mockUserStore.clearAuth).toHaveBeenCalled()
  })

  it('通过 mock adapter 验证响应错误拦截: 网络异常 reject HttpException', async () => {
    service.defaults.adapter = () => {
      const err: any = new Error('Network Error')
      err.config = {}
      return Promise.reject(err)
    }
    const error = await service.request({ url: '/test' }).catch((e: unknown) => e)
    expect(error).toBeInstanceOf(HttpException)
    expect(isHandledError(error)).toBe(true)
    expect(mockElMessageError).toHaveBeenCalledWith('网络连接异常，请检查网络')
  })

  it('通过 mock adapter 验证响应错误拦截: 请求超时 ECONNABORTED', async () => {
    service.defaults.adapter = () => {
      const err: any = new Error('timeout of 30000ms exceeded')
      err.code = 'ECONNABORTED'
      err.config = {}
      return Promise.reject(err)
    }
    const error = await service.request({ url: '/test' }).catch((e: unknown) => e)
    expect(error).toBeInstanceOf(HttpException)
    expect(mockElMessageError).toHaveBeenCalledWith('请求超时，请稍后重试')
  })

  // ==================== P1-7: 全局 Loading 服务 ====================

  it('非 silent 请求 → 开启全局 loading → 响应后关闭', async () => {
    service.defaults.adapter = (config: any) =>
      Promise.resolve({
        data: JSON.stringify({ code: 0, message: 'ok', data: 'ok' }),
        status: 200,
        statusText: 'OK',
        headers: {},
        config,
      })

    await service.request({ url: '/test' })

    expect(mockElLoadingService).toHaveBeenCalled()
    expect(mockLoadingClose).toHaveBeenCalled()
  })

  it('silent 请求 → 不开启全局 loading', async () => {
    vi.clearAllMocks()
    service.defaults.adapter = (config: any) =>
      Promise.resolve({
        data: JSON.stringify({ code: 0, message: 'ok', data: 'ok' }),
        status: 200,
        statusText: 'OK',
        headers: {},
        config,
      })

    await service.request({ url: '/test', silent: true } as any)

    expect(mockElLoadingService).not.toHaveBeenCalled()
    expect(mockLoadingClose).not.toHaveBeenCalled()
  })

  it('并发请求 → loading 只开启一次，全部完成后关闭一次', async () => {
    vi.clearAllMocks()
    service.defaults.adapter = () =>
      Promise.resolve({
        data: JSON.stringify({ code: 0, message: 'ok', data: 'ok' }),
        status: 200,
        statusText: 'OK',
        headers: {},
        config: {} as any,
      })

    // 并发 3 个请求
    await Promise.all([
      service.request({ url: '/test1' }),
      service.request({ url: '/test2' }),
      service.request({ url: '/test3' }),
    ])

    // loading.service 只调用 1 次（并发计数）
    expect(mockElLoadingService).toHaveBeenCalledTimes(1)
    // close 只调用 1 次（计数归零）
    expect(mockLoadingClose).toHaveBeenCalledTimes(1)
  })

  it('请求失败时也关闭 loading', async () => {
    vi.clearAllMocks()
    service.defaults.adapter = () => {
      const err: any = new Error('fail')
      err.config = {}
      return Promise.reject(err)
    }

    await service.request({ url: '/test' }).catch(() => {})

    expect(mockElLoadingService).toHaveBeenCalled()
    expect(mockLoadingClose).toHaveBeenCalled()
  })
})
