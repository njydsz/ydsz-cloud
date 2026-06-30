import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useUserStore } from '@/store/modules/user'
import * as auth from '@/utils/auth'

// Mock API
vi.mock('@/api/user', () => ({
  loginApi: vi.fn(),
  logoutApi: vi.fn(),
  getUserInfoApi: vi.fn(),
}))

import { loginApi, getUserInfoApi, logoutApi } from '@/api/user'

describe('user store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.spyOn(auth, 'setToken').mockImplementation(() => {})
    vi.spyOn(auth, 'removeToken').mockImplementation(() => {})
    vi.spyOn(auth, 'getToken').mockReturnValue('')
  })

  it('initial state should be empty', () => {
    const store = useUserStore()
    expect(store.token).toBe('')
    expect(store.isLoggedIn).toBe(false)
  })

  it('login should set token', async () => {
    vi.mocked(loginApi).mockResolvedValue({
      code: 0,
      message: 'ok',
      data: { token: 'abc', refreshToken: 'xyz', expiresIn: 3600 },
      timestamp: Date.now(),
    })

    const store = useUserStore()
    await store.login({ username: 'admin', password: 'admin123' })

    expect(store.token).toBe('abc')
    expect(store.isLoggedIn).toBe(true)
    expect(auth.setToken).toHaveBeenCalledWith('abc', 'xyz')
  })

  it('fetchUserInfo should set userInfo and permissions', async () => {
    vi.mocked(getUserInfoApi).mockResolvedValue({
      code: 0,
      message: 'ok',
      data: {
        id: 1,
        username: 'admin',
        realName: '管理员',
        roles: ['admin'],
        permissions: ['*:*:*'],
      },
      timestamp: Date.now(),
    })

    const store = useUserStore()
    await store.fetchUserInfo()

    expect(store.userInfo?.realName).toBe('管理员')
    expect(store.permissions).toContain('*:*:*')
  })

  it('hasPermission should return true for super admin', async () => {
    const store = useUserStore()
    store.permissions = ['*:*:*']
    expect(store.hasPermission('any:perm')).toBe(true)
  })

  it('logout should clear auth', async () => {
    vi.mocked(logoutApi).mockResolvedValue({
      code: 0,
      message: 'ok',
      data: null,
      timestamp: Date.now(),
    })

    const store = useUserStore()
    store.token = 'abc'
    await store.logout()

    expect(store.token).toBe('')
    expect(auth.removeToken).toHaveBeenCalled()
  })
})
