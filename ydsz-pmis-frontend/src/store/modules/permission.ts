import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { RouteRecordRaw } from 'vue-router'
import { constantRoutes, asyncRoutes } from '@/router/routes'
import { getMenuTreeApi } from '@/api/menu'

/**
 * 用于根据角色生成可访问路由
 */
function hasPermission(permissions: string[], route: RouteRecordRaw): boolean {
  if (route.meta?.permissions) {
    return (route.meta.permissions as string[]).some((p) => permissions.includes(p))
  }
  return true
}

function filterAsyncRoutes(routes: RouteRecordRaw[], permissions: string[]): RouteRecordRaw[] {
  const res: RouteRecordRaw[] = []
  routes.forEach((route) => {
    const tmp: RouteRecordRaw = { ...route }
    if (hasPermission(permissions, tmp)) {
      if (tmp.children) {
        tmp.children = filterAsyncRoutes(tmp.children, permissions)
      }
      res.push(tmp)
    }
  })
  return res
}

export const usePermissionStore = defineStore('permission', () => {
  const routes = ref<RouteRecordRaw[]>([])
  const addRoutes = ref<RouteRecordRaw[]>([])
  const isDynamicRouteLoaded = ref(false)

  const sidebarRoutes = computed(() => {
    return routes.value.filter((r) => !r.meta?.hidden)
  })

  /**
   * 生成路由
   */
  async function generateRoutes(): Promise<void> {
    // 已生成则跳过
    if (isDynamicRouteLoaded.value) return

    // 从后端拉取菜单树
    const { data } = await getMenuTreeApi()
    const dynamicRoutes = buildDynamicRoutes(data)

    addRoutes.value = dynamicRoutes.concat(asyncRoutes)
    routes.value = constantRoutes.concat(addRoutes.value)

    isDynamicRouteLoaded.value = true
  }

  /**
   * 重置
   */
  function reset(): void {
    routes.value = []
    addRoutes.value = []
    isDynamicRouteLoaded.value = false
  }

  /**
   * 根据菜单数据构造动态路由
   */
  function buildDynamicRoutes(menus: unknown[]): RouteRecordRaw[] {
    // 此处将后端菜单树转为前端路由
    // 实际实现按业务菜单元数据格式
    return []
  }

  return {
    routes,
    addRoutes,
    isDynamicRouteLoaded,
    sidebarRoutes,
    generateRoutes,
    reset,
  }
})
