import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { RouteRecordRaw } from 'vue-router'
import router from '@/router'
import { constantRoutes, asyncRoutes } from '@/router/routes'
import { getMenuTreeApi } from '@/api/menu'
import type { MenuTreeNode } from '@/api/menu/types'

/**
 * 菜单节点 → 路由转换
 *
 * 后端返回的菜单树 permType=MENU 的节点才转换为路由。
 * - permType=MENU  → 路由 (Layout 或 Leaf)
 * - permType=BUTTON → 按钮权限,仅记录到 meta.permissions,不转换为路由
 */
function convertMenuToRoutes(menus: MenuTreeNode[]): RouteRecordRaw[] {
  const routes: RouteRecordRaw[] = []
  for (const m of menus) {
    if (m.permType === 'BUTTON' || m.permType === 'API') {
      // 按钮/接口级权限,跳过路由
      continue
    }
    if (!m.path) {
      continue
    }
    const route: RouteRecordRaw = {
      path: m.path.startsWith('/') ? m.path.substring(1) : m.path,
      name: 'Menu_' + m.permCode.replace(/[:.]/g, '_'),
      component: m.component
        ? () => import(/* @vite-ignore */ `@/views/${m.component}.vue`)
        : () => import('@/layout/default/index.vue'),
      meta: {
        title: m.permName,
        icon: m.icon || '',
        permCode: m.permCode,
        hidden: m.visible === 0,
      },
    }
    if (m.children && m.children.length > 0) {
      const childRoutes = convertMenuToRoutes(m.children)
      if (childRoutes.length > 0) {
        // 父路由通常是 Layout
        if (!m.component) {
          ;(route as { redirect?: string }).redirect = childRoutes[0].path
        }
        ;(route as { children?: RouteRecordRaw[] }).children = childRoutes
      }
    }
    routes.push(route)
  }
  return routes
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
    if (isDynamicRouteLoaded.value) return

    // 从后端拉取菜单树
    let menus: MenuTreeNode[] = []
    try {
      const { data } = await getMenuTreeApi()
      menus = data || []
    } catch (e) {
      console.warn('[Permission] 拉取菜单树失败,使用静态路由', e)
      menus = []
    }

    const dynamicRoutes = convertMenuToRoutes(menus)
    addRoutes.value = dynamicRoutes.concat(asyncRoutes)
    routes.value = constantRoutes.concat(addRoutes.value)

    // 关键: 把动态路由真正注册到 vue-router
    dynamicRoutes.forEach((route) => {
      // 父级(Menu 容器)不重复注册,只注册叶子节点
      if (route.children && route.children.length > 0) {
        route.children.forEach((child) => {
          if (!router.hasRoute(child.name as string)) {
            router.addRoute(route.path || '/', child)
          }
        })
      } else if (!router.hasRoute(route.name as string)) {
        router.addRoute(route)
      }
    })

    isDynamicRouteLoaded.value = true
  }

  /**
   * 重置
   */
  function reset(): void {
    // 关键: 清空已注册的动态路由,避免切换账号后菜单错乱
    addRoutes.value.forEach((route) => {
      if (route.children && route.children.length > 0) {
        route.children.forEach((child) => {
          if (child.name) router.removeRoute(child.name as string)
        })
      } else if (route.name) {
        router.removeRoute(route.name as string)
      }
    })
    routes.value = []
    addRoutes.value = []
    isDynamicRouteLoaded.value = false
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
