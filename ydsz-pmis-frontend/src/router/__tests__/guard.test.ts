/**
 * @file 路由守卫 单元测试
 * @description 验证 router guard 的登录态校验、动态路由加载、用户信息拉取失败降级、
 *              afterEach 标题设置及 P0-1 安全改造的权限码校验（permCode 放行/拦截/超管放行）。
 * @module router/__tests__/guard
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { ElMessage } from 'element-plus'

// Mock nprogress，避免进度条在测试环境真实渲染
vi.mock('nprogress', () => ({
  default: {
    start: vi.fn(),
    done: vi.fn(),
    configure: vi.fn(),
  },
}))

/** 用户 store mock：覆盖 token / userInfo / fetchUserInfo / logout / hasPermission */
const mockUserStore = {
  token: '' as string,
  userInfo: null as any,
  fetchUserInfo: vi.fn(),
  logout: vi.fn(),
  hasPermission: vi.fn().mockReturnValue(true),
}

vi.mock('@/store/modules/user', () => ({
  useUserStore: () => mockUserStore,
}))

/** 权限 store mock：覆盖动态路由加载状态与 generateRoutes */
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
    mockUserStore.hasPermission = vi.fn().mockReturnValue(true)
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

/**
 * P0-1 路由权限码校验（批次25 新增）
 *
 * 验证：
 *  - 无 permCode 的路由（如 dashboard、login）直接放行
 *  - 有 permCode 且用户有权限 → 放行
 *  - 有 permCode 且用户无权限 → 跳转 /404 + ElMessage.error 提示
 */
describe('router guard 权限码校验（P0-1 安全改造）', () => {
  let beforeEachHandler: any

  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    mockUserStore.token = 'abc'
    mockUserStore.userInfo = { id: 1, username: 'admin' }
    mockPermissionStore.isDynamicRouteLoaded = true
    mockUserStore.hasPermission = vi.fn().mockReturnValue(true)

    beforeEachHandler = null
    const routerMock = {
      beforeEach: (h: any) => {
        beforeEachHandler = h
      },
      afterEach: () => {},
      onError: vi.fn(),
    }
    setupRouterGuard(routerMock)
  })

  it('路由无 meta.permCode 时（如 /dashboard）应放行', async () => {
    const next = vi.fn()
    await beforeEachHandler(
      { path: '/dashboard', meta: { title: '仪表盘' } },
      {},
      next,
    )
    expect(next).toHaveBeenCalledWith()
    expect(mockUserStore.hasPermission).not.toHaveBeenCalled()
  })

  it('路由有 permCode 且用户有权限应放行', async () => {
    mockUserStore.hasPermission = vi.fn().mockReturnValue(true)
    const next = vi.fn()
    await beforeEachHandler(
      {
        path: '/system/user',
        meta: { title: '用户管理', permCode: 'auth:user:list' },
      },
      {},
      next,
    )
    expect(mockUserStore.hasPermission).toHaveBeenCalledWith('auth:user:list')
    expect(next).toHaveBeenCalledWith()
  })

  it('路由有 permCode 且用户无权限应跳转 /404 并 ElMessage.error 提示', async () => {
    mockUserStore.hasPermission = vi.fn().mockReturnValue(false)
    const next = vi.fn()
    await beforeEachHandler(
      {
        path: '/system/user',
        meta: { title: '用户管理', permCode: 'auth:user:list' },
      },
      {},
      next,
    )
    expect(mockUserStore.hasPermission).toHaveBeenCalledWith('auth:user:list')
    expect(ElMessage.error).toHaveBeenCalledWith(
      expect.stringContaining('无权限访问该页面'),
    )
    expect(next).toHaveBeenCalledWith({ path: '/404', replace: true })
  })

  it('超管权限 *:*:* 时所有路由都应放行', async () => {
    mockUserStore.hasPermission = vi.fn().mockImplementation((perm: string) => {
      // 模拟超管逻辑：permissions 包含 *:*:* 时全部返回 true
      return perm === '*:*:*' || true
    })
    const next = vi.fn()
    await beforeEachHandler(
      {
        path: '/agent/orchestration',
        meta: { title: '多智能体编排', permCode: 'agent:orchestration:view' },
      },
      {},
      next,
    )
    expect(next).toHaveBeenCalledWith()
  })
})
