/**
 * @file request.test.ts
 * @description 测试 axios 实例配置、拦截器注册与 Token 管理
 * @vitest-environment jsdom
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'

// 模拟 import.meta.env
vi.stubEnv('VITE_API_BASE_URL', 'http://localhost:8080')
vi.stubEnv('VITE_API_PREFIX', '/api/v1')

// 使用 vi.hoisted 确保 mock 工厂可访问变量
const { mockAxiosCreate, mockInterceptorUse } = vi.hoisted(() => {
  const interceptorUse = vi.fn()
  return {
    mockAxiosCreate: vi.fn(() => ({
      interceptors: {
        request: { use: interceptorUse },
        response: { use: interceptorUse },
      },
    })),
    mockInterceptorUse: interceptorUse,
  }
})

vi.mock('axios', () => ({
  default: {
    create: mockAxiosCreate,
  },
}))

vi.mock('element-plus', () => ({
  ElMessage: { error: vi.fn() },
  ElLoading: { service: vi.fn(() => ({ close: vi.fn() })) },
}))

vi.mock('@/utils/auth', () => ({
  getToken: vi.fn(() => null),
  setToken: vi.fn(),
  getRefreshToken: vi.fn(() => null),
  removeToken: vi.fn(),
}))

vi.mock('@/utils/trace', () => ({
  generateTraceId: vi.fn(() => 'test-trace-id'),
}))

vi.mock('@/utils/error', () => ({
  BizException: class extends Error {
    code: number
    handled: boolean
    constructor(message: string, code: number, handled = true) {
      super(message)
      this.name = 'BizException'
      this.code = code
      this.handled = handled
    }
  },
  HttpException: class extends Error {
    status: number
    handled: boolean
    constructor(message: string, status: number, handled = true) {
      super(message)
      this.name = 'HttpException'
      this.status = status
      this.handled = handled
    }
  },
}))

vi.mock('@/utils/logger', () => ({
  logger: { debug: vi.fn(), info: vi.fn(), warn: vi.fn(), error: vi.fn() },
}))

vi.mock('@/store/modules/user', () => ({
  useUserStore: vi.fn(() => ({
    token: '',
    refreshToken: '',
    clearAuth: vi.fn(),
  })),
}))

vi.mock('@/locales', () => ({
  default: {
    global: {
      t: vi.fn((key: string) => key),
      locale: { value: 'zh-CN' },
    },
  },
}))

describe('request.ts - axios 实例配置', () => {
  beforeEach(() => {
    // 重置模块缓存，确保每次测试都重新执行模块级 axios.create()
    vi.resetModules()
    // 重新应用 mock（vi.mock 在 resetModules 后仍有效，但需要确保变量引用正确）
    vi.doMock('axios', () => ({
      default: {
        create: mockAxiosCreate,
      },
    }))
    vi.doMock('element-plus', () => ({
      ElMessage: { error: vi.fn() },
      ElLoading: { service: vi.fn(() => ({ close: vi.fn() })) },
    }))
    vi.doMock('@/utils/auth', () => ({
      getToken: vi.fn(() => null),
      setToken: vi.fn(),
      getRefreshToken: vi.fn(() => null),
      removeToken: vi.fn(),
    }))
    vi.doMock('@/utils/trace', () => ({
      generateTraceId: vi.fn(() => 'test-trace-id'),
    }))
    vi.doMock('@/utils/error', () => ({
      BizException: class extends Error {
        code: number
        handled: boolean
        constructor(message: string, code: number, handled = true) {
          super(message)
          this.name = 'BizException'; this.code = code; this.handled = handled
        }
      },
      HttpException: class extends Error {
        status: number
        handled: boolean
        constructor(message: string, status: number, handled = true) {
          super(message)
          this.name = 'HttpException'; this.status = status; this.handled = handled
        }
      },
    }))
    vi.doMock('@/utils/logger', () => ({
      logger: { debug: vi.fn(), info: vi.fn(), warn: vi.fn(), error: vi.fn() },
    }))
    vi.doMock('@/store/modules/user', () => ({
      useUserStore: vi.fn(() => ({
        token: '', refreshToken: '', clearAuth: vi.fn(),
      })),
    }))
    vi.doMock('@/locales', () => ({
      default: {
        global: {
          t: vi.fn((key: string) => key),
          locale: { value: 'zh-CN' },
        },
      },
    }))
  })

  it('应使用正确的 baseURL 创建 axios 实例', async () => {
    // 静态 import 触发模块加载（mock 已就绪）
    const mod = await import('@/utils/request')
    expect(mockAxiosCreate).toHaveBeenCalledTimes(1)
    const config = mockAxiosCreate.mock.calls[0]?.[0]
    expect(config).toBeDefined()
    expect(config.baseURL).toBe('http://localhost:8080/api/v1')
    expect(mod.service).toBeDefined()
  })

  it('应设置 timeout 为 30000ms', async () => {
    await import('@/utils/request')
    const config = mockAxiosCreate.mock.calls[0]?.[0]
    expect(config).toBeDefined()
    expect(config.timeout).toBe(30000)
  })

  it('应设置 Content-Type 请求头', async () => {
    await import('@/utils/request')
    const config = mockAxiosCreate.mock.calls[0]?.[0]
    expect(config).toBeDefined()
    expect(config.headers).toEqual({
      'Content-Type': 'application/json;charset=UTF-8',
    })
  })

  it('应注册请求拦截器', async () => {
    await import('@/utils/request')
    expect(mockInterceptorUse).toHaveBeenCalled()
  })

  it('应注册响应拦截器', async () => {
    await import('@/utils/request')
    // 请求和响应拦截器都调用同一个 mockInterceptorUse
    expect(mockInterceptorUse.mock.calls.length).toBeGreaterThanOrEqual(2)
  })

  it('应导出 service 和 request 函数', async () => {
    const mod = await import('@/utils/request')
    expect(mod.service).toBeDefined()
    expect(mod.request).toBeDefined()
    expect(typeof mod.request).toBe('function')
  })
})