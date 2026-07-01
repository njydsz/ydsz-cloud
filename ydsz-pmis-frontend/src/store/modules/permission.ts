/**
 * @file Permission Store - 动态路由与菜单权限
 * @description 拉取后端菜单树、转换为前端路由、与本地兜底路由合并后注册到 vue-router
 * @module store/modules/permission
 *
 * 路由生命周期：
 *  1. 路由守卫首次进入受保护路由时调用 generateRoutes()
 *  2. 后端菜单树 → convertMenuToRoutes 转换为 RouteRecordRaw[]
 *  3. 与本地 asyncRoutes 按 name 去重合并（后端优先，本地兜底）
 *  4. router.addRoute 注册到 vue-router
 *  5. 切换账号/登出时调用 reset() 清空已注册路由，避免菜单错乱
 */
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
 *
 * @param menus - 后端菜单树
 * @param isChild - 是否递归处理子节点（影响 path 是否以 / 开头）
 * @returns 转换后的路由数组
 */
function convertMenuToRoutes(
  menus: MenuTreeNode[],
  isChild = false,
): RouteRecordRaw[] {
  const routes: RouteRecordRaw[] = []
  for (const m of menus) {
    if (m.permType === 'BUTTON' || m.permType === 'API') {
      // 按钮/接口级权限,跳过路由
      continue
    }
    if (!m.path) {
      continue
    }
    // 顶层路由 path 必须以 / 开头 (vue-router 4 要求);
    // 子路由 path 使用相对路径 (不带 /)
    const rawPath = m.path.startsWith('/') ? m.path : `/${m.path}`
    const route: RouteRecordRaw = {
      path: isChild ? rawPath.replace(/^\//, '') : rawPath,
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
      const childRoutes = convertMenuToRoutes(m.children, true)
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
  /** 当前完整路由表（constantRoutes + 动态路由），供 sidebar 渲染 */
  const routes = ref<RouteRecordRaw[]>([])
  /** 仅动态路由（不含 constantRoutes），用于 reset 时移除 */
  const addRoutes = ref<RouteRecordRaw[]>([])
  /** 动态路由是否已加载，避免重复 generateRoutes */
  const isDynamicRouteLoaded = ref(false)

  /** 侧边栏可见路由（过滤 hidden） */
  const sidebarRoutes = computed(() => {
    return routes.value.filter((r) => !r.meta?.hidden)
  })

  /**
   * 生成路由
   *
   * 合并策略：
   *   1. 后端菜单返回的路由（权威，按 permCode 控制可见性）
   *   2. 本地 asyncRoutes（兜底，当后端菜单服务不可用或未返回某些路由时使用）
   *
   * 注册策略：
   *   - 父级路由（带 children 的 Layout 容器）：用 router.addRoute(parent) 注册整棵子树
   *   - 叶子路由：单独注册
   *   - 按 name 去重，后端菜单优先，asyncRoutes 仅在缺失时补齐
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
    // 合并：后端菜单路由优先 + asyncRoutes 兜底
    const merged: RouteRecordRaw[] = []
    const seenNames = new Set<string>()

    function collectName(r: RouteRecordRaw) {
      if (r.name) seenNames.add(String(r.name))
      if (r.children) r.children.forEach(collectName)
    }

    // 1. 先收集后端菜单返回的所有 name
    dynamicRoutes.forEach(collectName)
    merged.push(...dynamicRoutes)

    // 2. 补齐 asyncRoutes 中未被后端返回的路由
    for (const fallback of asyncRoutes) {
      const fallbackTopName = String(fallback.name || '')
      if (fallbackTopName && seenNames.has(fallbackTopName)) {
        // 后端已经返回了同名父路由，按子节点 name 去重补齐缺失的子路由
        const existingParent = merged.find(
          (m) => String(m.name || '') === fallbackTopName,
        ) as (RouteRecordRaw & { children?: RouteRecordRaw[] }) | undefined
        if (existingParent && fallback.children) {
          for (const fbChild of fallback.children) {
            const childName = String(fbChild.name || '')
            if (childName && !seenNames.has(childName)) {
              existingParent.children = existingParent.children || []
              existingParent.children.push(fbChild)
              seenNames.add(childName)
            }
          }
        }
      } else {
        // 后端未返回该父路由，整个补齐
        collectName(fallback)
        merged.push(fallback)
      }
    }

    addRoutes.value = merged
    routes.value = constantRoutes.concat(merged)

    // 关键: 把动态路由真正注册到 vue-router
    // 父级(Menu 容器)用 addRoute(parent) 注册整棵子树,叶子节点单独注册
    for (const route of merged) {
      if (route.children && route.children.length > 0) {
        // 先注册父级 Layout 容器
        if (!router.hasRoute(route.name as string)) {
          router.addRoute(route)
        } else {
          // 父级已存在，仅补齐子路由
          for (const child of route.children) {
            if (!router.hasRoute(child.name as string)) {
              router.addRoute(route.name as string, child)
            }
          }
        }
      } else if (!router.hasRoute(route.name as string)) {
        router.addRoute(route)
      }
    }

    isDynamicRouteLoaded.value = true
  }

  /**
   * 重置动态路由
   *
   * 关键: 清空已注册的动态路由,避免切换账号后菜单错乱
   */
  function reset(): void {
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
