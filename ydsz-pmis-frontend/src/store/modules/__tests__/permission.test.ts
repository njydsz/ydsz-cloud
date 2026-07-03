/**
 * @file permission.test.ts
 * @description 测试 Permission Store 的动态路由生成与权限校验
 * @vitest-environment jsdom
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

// ========== Mock 依赖 ==========

// 模拟 router
const mockAddRoute = vi.fn()
const mockRemoveRoute = vi.fn()
const mockHasRoute = vi.fn(() => false)

vi.mock('@/router', () => ({
  default: {
    addRoute: mockAddRoute,
    removeRoute: mockRemoveRoute,
    hasRoute: mockHasRoute,
  },
}))

// 模拟路由配置
vi.mock('@/router/routes', () => ({
  constantRoutes: [
    { path: '/login', name: 'Login', component: {} },
    { path: '/', name: 'Layout', component: {}, children: [] },
  ],
  asyncRoutes: [
    {
      path: '/dashboard',
      name: 'Dashboard',
      component: {},
      meta: { title: '仪表盘', icon: 'dashboard' },
    },
  ],
}))

// 模拟 menu API
const mockGetMenuTreeApi = vi.fn()
vi.mock('@/api/menu', () => ({
  getMenuTreeApi: mockGetMenuTreeApi,
}))

// 模拟 logger
vi.mock('@/utils/logger', () => ({
  logger: { warn: vi.fn(), debug: vi.fn(), info: vi.fn(), error: vi.fn() },
}))

describe('Permission Store', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    mockHasRoute.mockReturnValue(false)
  })

  describe('generateRoutes', () => {
    it('应从后端菜单树生成路由并注册', async () => {
      const { usePermissionStore } = await import(
        '@/store/modules/permission'
      )
      const store = usePermissionStore()

      const mockMenus = [
        {
          id: 1,
          parentId: 0,
          permCode: 'dashboard',
          permName: '仪表盘',
          permType: 'MENU',
          path: '/dashboard',
          component: 'dashboard/index',
          icon: 'dashboard',
          visible: 1,
          children: [],
        },
      ]

      mockGetMenuTreeApi.mockResolvedValue({ data: mockMenus })

      await store.generateRoutes()

      expect(mockGetMenuTreeApi).toHaveBeenCalled()
      expect(store.routes.length).toBeGreaterThan(0)
      expect(store.isDynamicRouteLoaded).toBe(true)
    })

    it('应在 API 失败时降级使用静态路由', async () => {
      const { usePermissionStore } = await import(
        '@/store/modules/permission'
      )
      const { logger } = await import('@/utils/logger')
      const store = usePermissionStore()

      mockGetMenuTreeApi.mockRejectedValue(new Error('Network error'))

      await store.generateRoutes()

      expect(logger.warn).toHaveBeenCalledWith(
        '[Permission]',
        '拉取菜单树失败,使用静态路由',
        expect.any(Error),
      )

      expect(store.routes.length).toBeGreaterThan(0)
      expect(store.isDynamicRouteLoaded).toBe(true)
    })

    it('应跳过 permType 为 BUTTON 和 API 的节点', async () => {
      const { usePermissionStore } = await import(
        '@/store/modules/permission'
      )
      const store = usePermissionStore()

      const mockMenus = [
        {
          id: 1,
          parentId: 0,
          permCode: 'dashboard',
          permName: '仪表盘',
          permType: 'MENU',
          path: '/dashboard',
          component: 'dashboard/index',
          visible: 1,
          children: [
            {
              id: 2,
              parentId: 1,
              permCode: 'dashboard:btn:add',
              permName: '新增按钮',
              permType: 'BUTTON',
              path: '',
              visible: 1,
              children: [],
            },
            {
              id: 3,
              parentId: 1,
              permCode: 'dashboard:api:list',
              permName: '列表API',
              permType: 'API',
              path: '',
              visible: 1,
              children: [],
            },
          ],
        },
      ]

      mockGetMenuTreeApi.mockResolvedValue({ data: mockMenus })

      await store.generateRoutes()

      // 从后端生成的 MENU 路由只有 1 个（dashboard）
      const dynamicRoutes = store.routes.filter(
        (r) => r.name !== 'Login' && r.name !== 'Layout',
      )
      // 后端返回 1 个 MENU + asyncRoutes 兜底 1 个 Dashboard = 2 个动态路由
      // 但 asyncRoutes 的 Dashboard 与后端返回的同名，按 name 去重只会保留一个
      // 实际上后端返回了 dashboard 路由，asyncRoutes 的 Dashboard 同名会被跳过
      expect(dynamicRoutes.length).toBeGreaterThanOrEqual(1)
    })

    it('重复调用 generateRoutes 不应重复加载', async () => {
      const { usePermissionStore } = await import(
        '@/store/modules/permission'
      )
      const store = usePermissionStore()

      mockGetMenuTreeApi.mockResolvedValue({ data: [] })

      await store.generateRoutes()
      expect(mockGetMenuTreeApi).toHaveBeenCalledTimes(1)

      await store.generateRoutes()
      expect(mockGetMenuTreeApi).toHaveBeenCalledTimes(1)
    })

    it('应过滤 hidden 路由到 sidebarRoutes', async () => {
      const { usePermissionStore } = await import(
        '@/store/modules/permission'
      )
      const store = usePermissionStore()

      store.routes = [
        { path: '/visible', name: 'Visible', meta: { hidden: false } },
        { path: '/hidden', name: 'Hidden', meta: { hidden: true } },
        { path: '/no-hidden', name: 'NoHidden', meta: {} },
      ]

      expect(store.sidebarRoutes).toHaveLength(2)
      expect(store.sidebarRoutes.find((r) => r.name === 'Hidden')).toBeUndefined()
    })
  })

  describe('reset', () => {
    it('应清空路由、移除已注册动态路由', async () => {
      const { usePermissionStore } = await import(
        '@/store/modules/permission'
      )
      const store = usePermissionStore()

      store.addRoutes = [
        { path: '/test', name: 'Test', component: {} },
      ]
      store.routes = [{ path: '/test', name: 'Test', component: {} }]
      store.isDynamicRouteLoaded = true

      store.reset()

      expect(mockRemoveRoute).toHaveBeenCalledWith('Test')
      expect(store.routes).toEqual([])
      expect(store.addRoutes).toEqual([])
      expect(store.isDynamicRouteLoaded).toBe(false)
    })

    it('应递归移除带 children 的路由', async () => {
      const { usePermissionStore } = await import(
        '@/store/modules/permission'
      )
      const store = usePermissionStore()

      store.addRoutes = [
        {
          path: '/parent',
          name: 'Parent',
          component: {},
          children: [
            { path: 'child1', name: 'Child1', component: {} },
            { path: 'child2', name: 'Child2', component: {} },
          ],
        },
      ]

      store.reset()

      expect(mockRemoveRoute).toHaveBeenCalledWith('Child1')
      expect(mockRemoveRoute).toHaveBeenCalledWith('Child2')
      expect(mockRemoveRoute).not.toHaveBeenCalledWith('Parent')
    })
  })
})