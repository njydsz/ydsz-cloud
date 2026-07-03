/**
 * @file user.test.ts
 * @description 测试 User Store 的登录、登出、权限校验逻辑
 * @vitest-environment jsdom
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

// ========== Mock 依赖 ==========

// 模拟 auth 工具
vi.mock('@/utils/auth', () => ({
  getToken: vi.fn(() => null),
  setToken: vi.fn(),
  removeToken: vi.fn(),
  getRefreshToken: vi.fn(() => null),
}))

// 模拟 permission store
const mockPermissionReset = vi.fn()
vi.mock('@/store/modules/permission', () => ({
  usePermissionStore: vi.fn(() => ({
    reset: mockPermissionReset,
  })),
}))

// 模拟 API 模块
const mockLoginApi = vi.fn()
const mockLogoutApi = vi.fn()
const mockGetUserInfoApi = vi.fn()

vi.mock('@/api/user', () => ({
  loginApi: mockLoginApi,
  logoutApi: mockLogoutApi,
  getUserInfoApi: mockGetUserInfoApi,
}))

describe('User Store', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    setActivePinia(createPinia())
  })

  describe('login', () => {
    it('正常登录后应存储 token 并调用 setToken', async () => {
      const { useUserStore } = await import('@/store/modules/user')
      const { setToken } = await import('@/utils/auth')
      const store = useUserStore()

      mockLoginApi.mockResolvedValue({
        data: {
          accessToken: 'test-access-token',
          refreshToken: 'test-refresh-token',
          mfaRequired: false,
        },
      })

      const result = await store.login({
        username: 'admin',
        password: '123456',
      })

      expect(store.token).toBe('test-access-token')
      expect(store.refreshToken).toBe('test-refresh-token')
      expect(setToken).toHaveBeenCalledWith(
        'test-access-token',
        'test-refresh-token',
      )
      expect(result.accessToken).toBe('test-access-token')
    })

    it('2FA 未通过时不应存储 token', async () => {
      const { useUserStore } = await import('@/store/modules/user')
      const { setToken } = await import('@/utils/auth')
      const store = useUserStore()

      mockLoginApi.mockResolvedValue({
        data: {
          mfaRequired: true,
          mfaPassed: false,
          accessToken: 'should-not-store',
        },
      })

      await store.login({ username: 'admin', password: '123456' })

      expect(store.token).toBe('')
      expect(store.refreshToken).toBe('')
      expect(setToken).not.toHaveBeenCalled()
    })

    it('应兼容旧版 token 字段', async () => {
      const { useUserStore } = await import('@/store/modules/user')
      const { setToken } = await import('@/utils/auth')
      const store = useUserStore()

      mockLoginApi.mockResolvedValue({
        data: {
          token: 'legacy-token',
          mfaRequired: false,
        },
      })

      await store.login({ username: 'admin', password: '123456' })
      expect(store.token).toBe('legacy-token')
      expect(setToken).toHaveBeenCalledWith('legacy-token', '')
    })
  })

  describe('fetchUserInfo', () => {
    it('应拉取用户信息并存储到 store', async () => {
      const { useUserStore } = await import('@/store/modules/user')
      const store = useUserStore()

      const mockUserInfo = {
        id: 1,
        username: 'admin',
        realName: '管理员',
        roles: ['admin'],
        permissions: ['system:user:list', 'system:role:list'],
      }

      mockGetUserInfoApi.mockResolvedValue({ data: mockUserInfo })

      await store.fetchUserInfo()

      expect(store.userInfo).toEqual(mockUserInfo)
      expect(store.roles).toEqual(['admin'])
      expect(store.permissions).toEqual(['system:user:list', 'system:role:list'])
      expect(store.username).toBe('admin')
      expect(store.realName).toBe('管理员')
    })

    it('应在 userInfo 为 null 时 username 返回空串', async () => {
      const { useUserStore } = await import('@/store/modules/user')
      const store = useUserStore()
      expect(store.username).toBe('')
      expect(store.realName).toBe('')
    })
  })

  describe('logout', () => {
    it('登出应调用 logoutApi 并清除认证信息', async () => {
      const { useUserStore } = await import('@/store/modules/user')
      const { removeToken } = await import('@/utils/auth')
      const store = useUserStore()

      store.token = 'some-token'
      store.userInfo = {
        id: 1,
        username: 'admin',
        realName: '管理员',
        roles: [],
        permissions: [],
      }

      mockLogoutApi.mockResolvedValue({ data: null })

      await store.logout()

      expect(mockLogoutApi).toHaveBeenCalled()
      expect(store.token).toBe('')
      expect(store.userInfo).toBeNull()
      expect(store.roles).toEqual([])
      expect(store.permissions).toEqual([])
      expect(removeToken).toHaveBeenCalled()
      expect(mockPermissionReset).toHaveBeenCalled()
    })

    it('logoutApi 失败时也应清除本地认证信息', async () => {
      const { useUserStore } = await import('@/store/modules/user')
      const { removeToken } = await import('@/utils/auth')
      const store = useUserStore()

      store.token = 'some-token'
      mockLogoutApi.mockRejectedValue(new Error('Network error'))

      await store.logout()

      expect(store.token).toBe('')
      expect(removeToken).toHaveBeenCalled()
    })
  })

  describe('hasPermission', () => {
    it('用户拥有权限时应返回 true', async () => {
      const { useUserStore } = await import('@/store/modules/user')
      const store = useUserStore()
      store.permissions = ['system:user:list', 'system:role:list']

      expect(store.hasPermission('system:user:list')).toBe(true)
    })

    it('用户不拥有权限时应返回 false', async () => {
      const { useUserStore } = await import('@/store/modules/user')
      const store = useUserStore()
      store.permissions = ['system:user:list']

      expect(store.hasPermission('system:role:list')).toBe(false)
    })

    it('超管通配符 *:*:* 应直接放行', async () => {
      const { useUserStore } = await import('@/store/modules/user')
      const store = useUserStore()
      store.permissions = ['*:*:*']

      expect(store.hasPermission('any:random:permission')).toBe(true)
    })
  })

  describe('isLoggedIn', () => {
    it('token 存在时应返回 true', async () => {
      const { useUserStore } = await import('@/store/modules/user')
      const store = useUserStore()
      store.token = 'some-token'
      expect(store.isLoggedIn).toBe(true)
    })

    it('token 为空时应返回 false', async () => {
      const { useUserStore } = await import('@/store/modules/user')
      const store = useUserStore()
      store.token = ''
      expect(store.isLoggedIn).toBe(false)
    })
  })
})