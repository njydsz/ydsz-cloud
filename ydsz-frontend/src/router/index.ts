/**
 * @file Vue Router 实例
 * @description 创建并导出应用路由实例，初始化静态路由与全局守卫
 * @module router/index
 *
 * 设计要点：
 *  - 采用 Hash 模式（createWebHashHistory），避免后端配置 fallback，便于静态部署
 *  - 初始仅注册 constantRoutes（login/404/dashboard 等），业务路由由 permission store 动态 addRoute
 *  - scrollBehavior 切换路由时自动回到顶部
 */
import { createRouter, createWebHashHistory, type RouteRecordRaw } from 'vue-router'
import { setupRouterGuard } from './guard'
import { constantRoutes } from './routes'

const router = createRouter({
  history: createWebHashHistory(import.meta.env.BASE_URL),
  routes: constantRoutes as RouteRecordRaw[],
  // 路由切换时滚动到顶部，避免长页面切换后保留滚动位置
  scrollBehavior: () => ({ top: 0 }),
})

// 注册全局前置/后置/错误守卫
setupRouterGuard(router)

export default router
