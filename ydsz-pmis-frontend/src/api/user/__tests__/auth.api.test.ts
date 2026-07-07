/**
 * @file auth.api.test.ts
 * @description 测试用户认证 API 模块（login/logout/refresh/getUserInfo）
 * @vitest-environment jsdom
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'

// Mock request 模块（使用 vi.hoisted 避免变量提升问题）
const { mockRequest } = vi.hoisted(() => ({ mockRequest: vi.fn() }))
vi.mock('@/utils/request', () => ({
  request: mockRequest,
}))

import { loginApi, logoutApi, refreshTokenApi, getUserInfoApi, getCaptchaApi } from '@/api/user'
import type { LoginParams, LoginResult } from '@/api/user'

describe('Auth API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('getCaptchaApi', () => {
    it('应调用 GET /auth/captcha', async () => {
      mockRequest.mockResolvedValue({ captchaKey: 'key123', captchaImage: 'base64...' })

      const result = await getCaptchaApi()

      expect(mockRequest).toHaveBeenCalledWith({ url: '/auth/captcha', method: 'GET' })
      expect(result.captchaKey).toBe('key123')
      expect(result.captchaImage).toBe('base64...')
    })
  })

  describe('loginApi', () => {
    it('应调用 POST /auth/login 并返回 LoginResult', async () => {
      const params: LoginParams = { username: 'admin', password: '123456' }
      const mockResult: LoginResult = {
        accessToken: 'access-token-123',
        refreshToken: 'refresh-token-456',
        userId: 1,
        username: 'admin',
      }
      mockRequest.mockResolvedValue(mockResult)

      const result = await loginApi(params)

      expect(mockRequest).toHaveBeenCalledWith({ url: '/auth/login', method: 'POST', data: params })
      expect(result.accessToken).toBe('access-token-123')
    })

    it('登录返回 mfaRequired=true 时应提示需要 2FA', async () => {
      const params: LoginParams = { username: 'admin', password: '123456' }
      mockRequest.mockResolvedValue({ accessToken: '', mfaRequired: true })

      const result = await loginApi(params)

      expect(result.mfaRequired).toBe(true)
    })
  })

  describe('logoutApi', () => {
    it('应调用 POST /auth/logout', async () => {
      mockRequest.mockResolvedValue(undefined)

      await logoutApi()

      expect(mockRequest).toHaveBeenCalledWith({ url: '/auth/logout', method: 'POST' })
    })
  })

  describe('refreshTokenApi', () => {
    it('应调用 POST /auth/refresh 并传递 refreshToken', async () => {
      const refreshToken = 'old-refresh-token'
      mockRequest.mockResolvedValue({ accessToken: 'new-access-token' })

      await refreshTokenApi(refreshToken)

      expect(mockRequest).toHaveBeenCalledWith({
        url: '/auth/refresh',
        method: 'POST',
        params: { refreshToken },
        _isRefreshTokenRequest: true,
        silent: true,
      })
    })
  })

  describe('getUserInfoApi', () => {
    it('应调用 GET /users/me 获取用户信息', async () => {
      mockRequest.mockResolvedValue({
        id: 1,
        username: 'admin',
        realName: '管理员',
        roles: ['admin'],
        permissions: ['system:user:list'],
      })

      const result = await getUserInfoApi()

      expect(mockRequest).toHaveBeenCalledWith({ url: '/users/me', method: 'GET', skipCancel: true })
      expect(result.username).toBe('admin')
      expect(result.roles).toContain('admin')
    })
  })
})