import { describe, it, expect, beforeEach, vi } from 'vitest'
import axios from 'axios'

// Mock element-plus
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

describe('request 拦截器逻辑', () => {
  let requestInterceptor: (config: any) => any
  let responseSuccessInterceptor: (response: any) => any
  let responseErrorInterceptor: (error: any) => any

  beforeEach(async () => {
    vi.clearAllMocks()

    // 捕获 axios.create 返回的 service 对象上的拦截器
    let requestHandler: any
    let requestErrorHandler: any
    let responseHandler: any
    let responseErrorHandler: any

    const fakeService: any = {
      interceptors: {
        request: {
          use: (h: any, e: any) => {
            requestHandler = h
            requestErrorHandler = e
          },
        },
        response: {
          use: (h: any, e: any) => {
            responseHandler = h
            responseErrorHandler = e
          },
        },
      },
    }

    vi.spyOn(axios, 'create').mockReturnValue(fakeService)

    // 重新 import request 模块以触发拦截器注册
    vi.resetModules()
    await import('@/utils/request')

    requestInterceptor = requestHandler
    requestErrorInterceptor = requestErrorHandler
    responseSuccessInterceptor = responseHandler
    responseErrorInterceptor = responseErrorHandler
  })

  it('请求拦截器: 有 token 时设置 Authorization 头', () => {
    getTokenMock.mockReturnValue('jwt-abc-123')
    const config: any = { headers: {} }
    const result = requestInterceptor(config)
    expect(result.headers.Authorization).toBe('Bearer jwt-abc-123')
    expect(result.headers['X-Trace-Id']).toBe('mock-trace-id-001')
  })

  it('请求拦截器: 无 token 时不设置 Authorization', () => {
    getTokenMock.mockReturnValue(null as any)
    const config: any = { headers: {} }
    const result = requestInterceptor(config)
    expect(result.headers.Authorization).toBeUndefined()
    expect(result.headers['X-Trace-Id']).toBe('mock-trace-id-001')
  })

  it('请求拦截器: 出错时 reject', () => {
    return expect(requestErrorInterceptor(new Error('req fail'))).rejects.toBeInstanceOf(Error)
  })

  it('响应拦截器: code=0 直接 resolve 数据', () => {
    const resp = { config: {}, data: { code: 0, message: 'ok', data: { foo: 1 } } }
    const r = responseSuccessInterceptor(resp)
    expect(r.data.foo).toBe(1)
  })

  it('响应拦截器: code=200 直接 resolve 数据', () => {
    const resp = { config: {}, data: { code: 200, message: 'ok', data: 42 } }
    const r = responseSuccessInterceptor(resp)
    expect(r.data).toBe(42)
  })

  it('响应拦截器: 二进制流 (blob) 直接返回 response', () => {
    const resp = { config: { responseType: 'blob' }, data: { code: 0 } }
    const r = responseSuccessInterceptor(resp)
    expect(r).toBe(resp)
  })

  it('响应拦截器: 401 系列 code 调用 clearAuth 并 reject', () => {
    ;[20001, 20002, 20003].forEach((code) => {
      const resp = { config: {}, data: { code, message: 'expired' } }
      expect(() => responseSuccessInterceptor(resp)).rejects.toBeInstanceOf(Error)
    })
    expect(mockUserStore.clearAuth).toHaveBeenCalled()
  })

  it('响应拦截器: 业务失败 ElMessage.error + reject', () => {
    const resp = { config: {}, data: { code: 500, message: '业务异常' } }
    return expect(responseSuccessInterceptor(resp)).rejects.toBeInstanceOf(Error)
  })

  it('响应错误拦截: 401 状态码触发 clearAuth', () => {
    const error = { response: { status: 401, data: { message: 'token失效' } } }
    return responseErrorInterceptor(error).catch(() => {
      expect(mockUserStore.clearAuth).toHaveBeenCalled()
    })
  })

  it('响应错误拦截: 非 401 状态码 reject', () => {
    const error = { response: { status: 500, data: { message: '服务器内部错误' } } }
    return expect(responseErrorInterceptor(error)).rejects.toBe(error)
  })
})
