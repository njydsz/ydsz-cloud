import { describe, it, expect, beforeEach, vi } from 'vitest'

vi.mock('element-plus', () => ({
  ElMessage: {
    success: vi.fn(),
    error: vi.fn(),
    warning: vi.fn(),
    info: vi.fn(),
  },
}))

const mockUserStore = {
  clearAuth: vi.fn(),
}

vi.mock('@/store/modules/user', () => ({
  useUserStore: () => mockUserStore,
}))

const getTokenMock = vi.fn()
vi.mock('@/utils/auth', () => ({
  getToken: () => getTokenMock(),
}))

vi.mock('@/utils/trace', () => ({
  generateTraceId: () => 'mock-trace-id-001',
}))

import { service } from '@/utils/request'

describe('request 拦截器逻辑', () => {
  // 拦截器通过 service.interceptors.use 注册后 axios 内部维护
  // 这里用 use 重新注册, 从注册的 handler 列表中读取

  // Axios 拦截器不支持读取已注册的 handler,
  // 这里改成: 验证 service 的 request/response 方法内部确实经过拦截器处理,
  // 通过 service.request 调用并通过 mock adapter 验证 header / response 处理

  let requestInterceptor: any
  let responseSuccessInterceptor: any
  let responseErrorInterceptor: any

  beforeEach(async () => {
    vi.clearAllMocks()
    // Axios 0.x 不允许读已注册 handler, 改为通过 mock adapter 验证最终行为
    // 我们直接重新 import 模块并验证 service 对象的拦截器链确实存在
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

  it('通过 mock adapter 验证响应拦截器: 业务失败 reject', async () => {
    service.defaults.adapter = () =>
      Promise.resolve({
        data: JSON.stringify({ code: 500, message: '业务错误' }),
        status: 200,
        statusText: 'OK',
        headers: {},
        config: {} as any,
      })
    await expect(service.request({ url: '/test' })).rejects.toBeInstanceOf(Error)
  })

  it('通过 mock adapter 验证响应拦截器: 401 系列 code 触发 clearAuth', async () => {
    for (const code of [20001, 20002, 20003]) {
      service.defaults.adapter = () =>
        Promise.resolve({
          data: JSON.stringify({ code, message: 'token失效' }),
          status: 200,
          statusText: 'OK',
          headers: {},
          config: {} as any,
        })
      await expect(service.request({ url: '/test' })).rejects.toBeInstanceOf(Error)
    }
    expect(mockUserStore.clearAuth).toHaveBeenCalled()
  })

  it('通过 mock adapter 验证响应错误拦截: HTTP 401 触发 clearAuth', async () => {
    service.defaults.adapter = () => {
      const err: any = new Error('Request failed')
      err.response = { status: 401, data: { message: 'token失效' } }
      return Promise.reject(err)
    }
    await expect(service.request({ url: '/test' })).rejects.toBeDefined()
    expect(mockUserStore.clearAuth).toHaveBeenCalled()
  })
})
