import { createRouter, createWebHashHistory, type RouteRecordRaw } from 'vue-router'
import { setupRouterGuard } from './guard'
import { constantRoutes } from './routes'

const router = createRouter({
  history: createWebHashHistory(import.meta.env.BASE_URL),
  routes: constantRoutes as RouteRecordRaw[],
  scrollBehavior: () => ({ top: 0 }),
})

setupRouterGuard(router)

export default router
