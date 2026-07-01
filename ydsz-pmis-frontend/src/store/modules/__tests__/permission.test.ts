/**
 * @file permission store 单元测试
 * @description 验证权限 Pinia store 的初始状态、generateRoutes 拉取菜单并标记已加载、
 *              后端菜单拉取失败时回退静态路由、reset 清空路由状态等场景。
 * @module store/modules/__tests__/permission
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

// 提前 mock API, 避免 import 阶段触发真实网络
vi.mock('@/api/menu', () => ({
  getMenuTreeApi: vi.fn(),
}))

import { usePermissionStore } from '@/store/modules/permission'
import { getMenuTreeApi } from '@/api/menu'

describe('permission store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('初始状态为空、未加载', () => {
    const store = usePermissionStore()
    expect(store.routes.length).toBe(0)
    expect(store.isDynamicRouteLoaded).toBe(false)
  })

  it('generateRoutes 拉取菜单并标记为已加载', async () => {
    vi.mocked(getMenuTreeApi).mockResolvedValue({
      code: 0,
      message: 'ok',
      data: [
        {
          id: 1,
          permCode: 'dashboard',
          permName: '仪表盘',
          permType: 'MENU',
          path: 'dashboard',
          component: 'dashboard/index',
          visible: 1,
        },
      ],
      timestamp: Date.now(),
    } as any)

    const store = usePermissionStore()
    await store.generateRoutes()
    expect(store.isDynamicRouteLoaded).toBe(true)
    expect(store.routes.length).toBeGreaterThan(0)
  })

  it('后端菜单拉取失败时仍可继续(回退到静态路由)', async () => {
    vi.mocked(getMenuTreeApi).mockRejectedValue(new Error('网络异常'))
    const store = usePermissionStore()
    await store.generateRoutes()
    expect(store.isDynamicRouteLoaded).toBe(true)
    // 应至少有 constantRoutes + asyncRoutes, 不至于空
    expect(store.routes.length).toBeGreaterThan(0)
  })

  it('reset 应清空所有路由状态', async () => {
    vi.mocked(getMenuTreeApi).mockResolvedValue({
      code: 0,
      message: 'ok',
      data: [],
      timestamp: Date.now(),
    } as any)
    const store = usePermissionStore()
    await store.generateRoutes()
    expect(store.isDynamicRouteLoaded).toBe(true)
    store.reset()
    expect(store.isDynamicRouteLoaded).toBe(false)
  })
})
