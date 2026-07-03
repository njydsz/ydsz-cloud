/**
 * @file auth.test.ts
 * @description 测试 Token 持久化工具函数
 * @vitest-environment jsdom
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'

// 模拟 import.meta.env 默认值
vi.stubEnv('VITE_TOKEN_KEY', 'test_token')
vi.stubEnv('VITE_REFRESH_TOKEN_KEY', 'test_refresh_token')

describe('auth.ts - Token 管理', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
  })

  it('getToken 应返回 localStorage 中存储的 token', async () => {
    localStorage.setItem('test_token', 'my-access-token')
    const { getToken } = await import('@/utils/auth')
    expect(getToken()).toBe('my-access-token')
  })

  it('getToken 应在无 token 时返回 null', async () => {
    const { getToken } = await import('@/utils/auth')
    expect(getToken()).toBeNull()
  })

  it('setToken 应写入 accessToken 到 localStorage', async () => {
    const { setToken, getToken } = await import('@/utils/auth')
    setToken('new-access-token')
    expect(localStorage.getItem('test_token')).toBe('new-access-token')
    expect(getToken()).toBe('new-access-token')
  })

  it('setToken 应同时写入 refreshToken', async () => {
    const { setToken, getRefreshToken } = await import('@/utils/auth')
    setToken('access', 'refresh')
    expect(localStorage.getItem('test_token')).toBe('access')
    expect(localStorage.getItem('test_refresh_token')).toBe('refresh')
    expect(getRefreshToken()).toBe('refresh')
  })

  it('getRefreshToken 应返回 refreshToken', async () => {
    localStorage.setItem('test_refresh_token', 'my-refresh-token')
    const { getRefreshToken } = await import('@/utils/auth')
    expect(getRefreshToken()).toBe('my-refresh-token')
  })

  it('getRefreshToken 应在无 refreshToken 时返回 null', async () => {
    const { getRefreshToken } = await import('@/utils/auth')
    expect(getRefreshToken()).toBeNull()
  })

  it('removeToken 应清除 accessToken 和 refreshToken', async () => {
    localStorage.setItem('test_token', 'access')
    localStorage.setItem('test_refresh_token', 'refresh')
    const { removeToken, getToken, getRefreshToken } = await import('@/utils/auth')
    removeToken()
    expect(getToken()).toBeNull()
    expect(getRefreshToken()).toBeNull()
    expect(localStorage.getItem('test_token')).toBeNull()
    expect(localStorage.getItem('test_refresh_token')).toBeNull()
  })

  it('setToken 不传 refreshToken 时不应修改 refreshToken', async () => {
    localStorage.setItem('test_refresh_token', 'existing-refresh')
    const { setToken } = await import('@/utils/auth')
    setToken('new-access')
    expect(localStorage.getItem('test_token')).toBe('new-access')
    expect(localStorage.getItem('test_refresh_token')).toBe('existing-refresh')
  })
})