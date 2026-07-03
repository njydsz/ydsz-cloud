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
import { logger } from '@/utils/logger'

/**
 * 视图组件显式映射表
 *
 * 后端菜单返回的 component 字段（如 "dashboard/index"）映射到对应的懒加载函数。
 * 使用显式映射替代 `@vite-ignore` 动态导入，确保 Vite 能正确分析 chunk 依赖。
 */
const viewModules: Record<string, () => Promise<typeof import('*.vue')>> = {
  // 仪表盘与驾驶舱
  'dashboard/index': () => import('@/views/dashboard/index.vue'),
  'cockpit/index': () => import('@/views/cockpit/index.vue'),
  // 系统管理
  'system/user/index': () => import('@/views/system/user/index.vue'),
  'system/role/index': () => import('@/views/system/role/index.vue'),
  'system/menu/index': () => import('@/views/system/menu/index.vue'),
  'system/dept/index': () => import('@/views/system/dept/index.vue'),
  'system/dict/index': () => import('@/views/system/dict/index.vue'),
  'system/config/index': () => import('@/views/system/config/index.vue'),
  'system/feature-flag/index': () => import('@/views/system/feature-flag/index.vue'),
  'system/session/index': () => import('@/views/system/session/index.vue'),
  'system/import-export/index': () => import('@/views/system/import-export/index.vue'),
  'chaos/index': () => import('@/views/chaos/index.vue'),
  // 项目管理
  'project/opportunity/index': () => import('@/views/project/opportunity/index.vue'),
  'project/initiation/index': () => import('@/views/project/initiation/index.vue'),
  'project/contract/index': () => import('@/views/project/contract/index.vue'),
  'project/contract-template/index': () => import('@/views/project/contract-template/index.vue'),
  'project/contract-change/index': () => import('@/views/project/contract-change/index.vue'),
  'change/index': () => import('@/views/change/index.vue'),
  // 执行管理
  'execution/wbs-task/index': () => import('@/views/execution/wbs-task/index.vue'),
  'execution/time-entry/index': () => import('@/views/execution/time-entry/index.vue'),
  'execution/purchase/index': () => import('@/views/execution/purchase/index.vue'),
  'execution/expense/index': () => import('@/views/execution/expense/index.vue'),
  'execution/risk/index': () => import('@/views/execution/risk/index.vue'),
  'execution/profit/index': () => import('@/views/execution/profit/index.vue'),
  'execution/evm/index': () => import('@/views/execution/evm/index.vue'),
  'execution/utilization/index': () => import('@/views/execution/utilization/index.vue'),
  'execution/rate-card/index': () => import('@/views/execution/rate-card/index.vue'),
  'execution/rate-internal/index': () => import('@/views/execution/rate-internal/index.vue'),
  'execution/profit-simulation/index': () => import('@/views/execution/profit-simulation/index.vue'),
  'execution/delivery/index': () => import('@/views/execution/delivery/index.vue'),
  'execution/closure/index': () => import('@/views/execution/closure/index.vue'),
  'execution/alert/index': () => import('@/views/execution/alert/index.vue'),
  'execution/reconcile/index': () => import('@/views/execution/reconcile/index.vue'),
  'execution/rule-engine/index': () => import('@/views/execution/rule-engine/index.vue'),
  // 售后管理
  'aftersales/warranty/index': () => import('@/views/aftersales/warranty/index.vue'),
  'aftersales/ops-ticket/index': () => import('@/views/aftersales/ops-ticket/index.vue'),
  'aftersales/satisfaction/index': () => import('@/views/aftersales/satisfaction/index.vue'),
  // 资源管理
  'resource/job-level/index': () => import('@/views/resource/job-level/index.vue'),
  'resource/pool/index': () => import('@/views/resource/pool/index.vue'),
  'resource/employee-tag/index': () => import('@/views/resource/employee-tag/index.vue'),
  'resource/assignment/index': () => import('@/views/resource/assignment/index.vue'),
  'resource/bench/index': () => import('@/views/resource/bench/index.vue'),
  // 考勤
  'attendance/index': () => import('@/views/attendance/index.vue'),
  // 报表
  'report/index': () => import('@/views/report/index.vue'),
  'report/executive/index': () => import('@/views/report/executive/index.vue'),
  // 审计
  'audit/index': () => import('@/views/audit/index.vue'),
  // AI 智能体
  'agent/orchestration/index': () => import('@/views/agent/orchestration/index.vue'),
  'agent/prediction/index': () => import('@/views/agent/prediction/index.vue'),
  // 工作流
  'workflow/approval-center/index': () => import('@/views/workflow/approval-center/index.vue'),
  'workflow/design/index': () => import('@/views/workflow/design/index.vue'),
  'workflow/instance/index': () => import('@/views/workflow/instance/index.vue'),
  'workflow/monitor/index': () => import('@/views/workflow/monitor/index.vue'),
  // 个人中心
  'profile/security': () => import('@/views/profile/security.vue'),
  // 错误页
  'error/404': () => import('@/views/error/404.vue'),
}

/**
 * 根据后端返回的 component 路径解析为懒加载函数。
 * 优先从显式映射表查找，未找到时回退到 layout 组件。
 *
 * @param componentPath - 后端菜单的 component 字段（如 "dashboard/index"）
 * @returns 懒加载函数
 */
function resolveComponent(componentPath?: string): () => Promise<unknown> {
  if (componentPath && viewModules[componentPath]) {
    return viewModules[componentPath]
  }
  return () => import('@/layout/default/index.vue')
}

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
        ? resolveComponent(m.component)
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
      logger.warn('[Permission]', '拉取菜单树失败,使用静态路由', e)
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
