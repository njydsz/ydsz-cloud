import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

// Mock nprogress
vi.mock('nprogress', () => ({
  default: {
    start: vi.fn(),
    done: vi.fn(),
    configure: vi.fn(),
  },
}))

const mockUserStore = {
  token: '' as string,
  userInfo: null as any,
  fetchUserInfo: vi.fn(),
  logout: vi.fn(),
}

vi.mock('@/store/modules/user', () => ({
  useUserStore: () => mockUserStore,
}))

const mockPermissionStore = {
  isDynamicRouteLoaded: false as boolean,
  generateRoutes: vi.fn(),
}

vi.mock('@/store/modules/permission', () => ({
  usePermissionStore: () => mockPermissionStore,
}))

import { setupRouterGuard } from '@/router/guard'

describe('router guard 路由守卫', () => {
  let beforeEachHandler: any
  let afterEachHandler: any
  let routerMock: any

  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    mockUserStore.token = ''
    mockUserStore.userInfo = null
    mockUserStore.fetchUserInfo = vi.fn()
    mockUserStore.logout = vi.fn()
    mockPermissionStore.isDynamicRouteLoaded = false
    mockPermissionStore.generateRoutes = vi.fn()

    beforeEachHandler = null
    afterEachHandler = null

    routerMock = {
      beforeEach: (h: any) => {
        beforeEachHandler = h
      },
      afterEach: (h: any) => {
        afterEachHandler = h
      },
      onError: vi.fn(),
    }

    setupRouterGuard(routerMock)
  })

  it('未登录访问 /login 应放行', async () => {
    mockUserStore.token = ''
    const next = vi.fn()
    await beforeEachHandler({ path: '/login', meta: {} }, {}, next)
    expect(next).toHaveBeenCalledWith()
  })

  it('未登录访问 /404 应放行', async () => {
    mockUserStore.token = ''
    const next = vi.fn()
    await beforeEachHandler({ path: '/404', meta: {} }, {}, next)
    expect(next).toHaveBeenCalledWith()
  })

  it('未登录访问受保护页面应跳转 /login 带 redirect', async () => {
    mockUserStore.token = ''
    const next = vi.fn()
    await beforeEachHandler({ path: '/system/user', meta: {} }, {}, next)
    expect(next).toHaveBeenCalledWith(expect.stringContaining('/login?redirect='))
  })

  it('已登录访问 /login 应跳转 /', async () => {
    mockUserStore.token = 'abc'
    mockUserStore.userInfo = { id: 1, username: 'admin' }
    mockPermissionStore.isDynamicRouteLoaded = true
    const next = vi.fn()
    await beforeEachHandler({ path: '/login', meta: {} }, {}, next)
    expect(next).toHaveBeenCalledWith({ path: '/' })
  })

  it('已登录但 userInfo 为空应先拉取用户信息', async () => {
    mockUserStore.token = 'abc'
    mockUserStore.userInfo = null
    mockUserStore.fetchUserInfo.mockResolvedValue({})
    mockPermissionStore.isDynamicRouteLoaded = true
    const next = vi.fn()
    await beforeEachHandler({ path: '/system/user', meta: {} }, {}, next)
    expect(mockUserStore.fetchUserInfo).toHaveBeenCalled()
    expect(next).toHaveBeenCalledWith()
  })

  it('拉取用户信息失败应登出并跳转登录页', async () => {
    mockUserStore.token = 'abc'
    mockUserStore.userInfo = null
    mockUserStore.fetchUserInfo.mockRejectedValue(new Error('401'))
    const next = vi.fn()
    await beforeEachHandler({ path: '/system/user', meta: {} }, {}, next)
    expect(mockUserStore.logout).toHaveBeenCalled()
    expect(next).toHaveBeenCalledWith(expect.stringContaining('/login'))
  })

  it('已登录但未加载动态路由应触发 generateRoutes', async () => {
    mockUserStore.token = 'abc'
    mockUserStore.userInfo = { id: 1, username: 'admin' }
    mockPermissionStore.isDynamicRouteLoaded = false
    mockPermissionStore.generateRoutes.mockResolvedValue(undefined)
    const next = vi.fn()
    await beforeEachHandler({ path: '/system/user', meta: {} }, {}, next)
    expect(mockPermissionStore.generateRoutes).toHaveBeenCalled()
  })

  it('afterEach 应设置 document.title', () => {
    afterEachHandler({ meta: { title: '用户管理' } })
    expect(document.title).toContain('用户管理')
  })

  it('afterEach 无 title 时使用默认标题', () => {
    afterEachHandler({ meta: {} })
    expect(document.title).toBe('PMIS 运营管理系统')
  })
})
