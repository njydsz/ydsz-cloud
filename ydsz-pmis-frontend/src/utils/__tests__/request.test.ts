/**
 * @file request.test.ts
 * @description 测试 axios 实例配置、拦截器注册与 Token 管理
 * @vitest-environment jsdom
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'

// ========== Mock 依赖 ==========

// 使用 vi.hoisted 确保 mock 工厂在 hoisting 时可访问变量
const { mockAxiosCreate, mockRequestUse, mockResponseUse } = vi.hoisted(() => {
  const mockRequestUse = vi.fn()
  const mockResponseUse = vi.fn()
  return {
    mockAxiosCreate: vi.fn(() => ({
      interceptors: {
        request: { use: mockRequestUse },
        response: { use: mockResponseUse },
      },
    })),
    mockRequestUse,
    mockResponseUse,
  }
})

// 模拟 import.meta.env
vi.stubEnv('VITE_API_BASE_URL', 'http://localhost:8080')
vi.stubEnv('VITE_API_PREFIX', '/api/v1')

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
    vi.clearAllMocks()
  })

  it('应使用正确的 baseURL 创建 axios 实例', async () => {
    await import('@/utils/request')
    expect(mockAxiosCreate).toHaveBeenCalledTimes(1)
    const config = mockAxiosCreate.mock.calls[0][0]
    expect(config.baseURL).toBe('http://localhost:8080/api/v1')
  })

  it('应设置 timeout 为 30000ms', async () => {
    await import('@/utils/request')
    const config = mockAxiosCreate.mock.calls[0][0]
    expect(config.timeout).toBe(30000)
  })

  it('应设置 Content-Type 请求头', async () => {
    await import('@/utils/request')
    const config = mockAxiosCreate.mock.calls[0][0]
    expect(config.headers).toEqual({
      'Content-Type': 'application/json;charset=UTF-8',
    })
  })

  it('应注册请求拦截器', async () => {
    await import('@/utils/request')
    expect(mockRequestUse).toHaveBeenCalledWith(
      expect.any(Function),
      expect.any(Function),
    )
  })

  it('应注册响应拦截器', async () => {
    await import('@/utils/request')
    expect(mockResponseUse).toHaveBeenCalledWith(
      expect.any(Function),
      expect.any(Function),
    )
  })

  it('应导出 service 和 request 函数', async () => {
    const mod = await import('@/utils/request')
    expect(mod.service).toBeDefined()
    expect(mod.request).toBeDefined()
    expect(typeof mod.request).toBe('function')
  })
})