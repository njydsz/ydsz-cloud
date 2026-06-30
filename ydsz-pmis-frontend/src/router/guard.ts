import type { Router } from 'vue-router'
import NProgress from 'nprogress'
import { useUserStore } from '@/store/modules/user'
import { usePermissionStore } from '@/store/modules/permission'

NProgress.configure({ showSpinner: false })

const whiteList = ['/login', '/404']

/**
 * 路由守卫
 *
 * 1. 已登录：访问 /login 重定向至首页
 * 2. 未登录：访问白名单放行，其他跳转至 /login
 * 3. 已登录无用户信息：拉取用户信息
 * 4. 已登录无路由：拉取权限路由
 */
export function setupRouterGuard(router: Router): void {
  router.beforeEach(async (to, _from, next) => {
    NProgress.start()
    const userStore = useUserStore()
    const permissionStore = usePermissionStore()

    if (userStore.token) {
      if (to.path === '/login') {
        next({ path: '/' })
        NProgress.done()
        return
      }

      if (!userStore.userInfo) {
        try {
          await userStore.fetchUserInfo()
        } catch (_err) {
          userStore.logout()
          next(`/login?redirect=${to.path}`)
          NProgress.done()
          return
        }
      }

      if (permissionStore.isDynamicRouteLoaded === false) {
        try {
          await permissionStore.generateRoutes()
          next({ ...to, replace: true })
          return
        } catch (_err) {
          userStore.logout()
          next(`/login?redirect=${to.path}`)
          NProgress.done()
          return
        }
      }

      next()
    } else {
      if (whiteList.includes(to.path)) {
        next()
      } else {
        next(`/login?redirect=${to.path}`)
        NProgress.done()
      }
    }
  })

  router.afterEach((to) => {
    NProgress.done()
    const title = (to.meta.title as string) || ''
    document.title = title ? `${title} - PMIS 运营管理系统` : 'PMIS 运营管理系统'
  })

  router.onError((error) => {
    console.error('[Router Error]', error)
    NProgress.done()
  })
}
