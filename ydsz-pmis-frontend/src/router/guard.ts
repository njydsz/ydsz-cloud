/**
 * @file 路由守卫
 * @description 注册全局前置/后置/错误守卫，处理登录态校验、权限码校验、动态路由加载
 * @module router/guard
 *
 * 守卫流程：
 *  1. 已登录访问 /login → 重定向至首页
 *  2. 未登录访问白名单 → 放行；其他 → 跳转 /login?redirect=xxx
 *  3. 已登录但无 userInfo → 拉取用户信息（失败则登出）
 *  4. 已登录但动态路由未加载 → 调用 generateRoutes，重新跳转 replace
 *  5. 已登录访问受保护路由 → 校验 meta.permCode，无权限跳转 /404
 */
import type { Router } from 'vue-router'
import NProgress from 'nprogress'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/modules/user'
import { usePermissionStore } from '@/store/modules/permission'

// 关闭右上角转圈动画，仅保留顶部进度条
NProgress.configure({ showSpinner: false })

/** 白名单路径：无需登录即可访问 */
const whiteList = ['/login', '/404']

/**
 * 注册路由守卫
 * @param router - Vue Router 实例
 */
export function setupRouterGuard(router: Router): void {
  router.beforeEach(async (to, _from, next) => {
    NProgress.start()
    const userStore = useUserStore()
    const permissionStore = usePermissionStore()

    if (userStore.token) {
      // 已登录场景
      if (to.path === '/login') {
        // 已登录访问登录页，直接跳首页
        next({ path: '/' })
        NProgress.done()
        return
      }

      if (!userStore.userInfo) {
        // 首次进入：拉取用户信息
        try {
          await userStore.fetchUserInfo()
        } catch (_err) {
          // 拉取失败：token 失效或服务异常，强制登出并跳登录
          userStore.logout()
          next(`/login?redirect=${to.path}`)
          NProgress.done()
          return
        }
      }

      if (permissionStore.isDynamicRouteLoaded === false) {
        // 动态路由未加载：先 generateRoutes，再 replace 重定向以保证 addRoute 生效
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

      // 权限码校验：路由 meta.permCode 存在时，必须验证用户拥有该权限
      // 缺失权限时跳转 /404，避免 URL 直接访问绕过后端菜单授权
      const permCode = to.meta?.permCode as string | undefined
      if (permCode && !userStore.hasPermission(permCode)) {
        ElMessage.error(`无权限访问该页面：${(to.meta?.title as string) || to.path}`)
        next({ path: '/404', replace: true })
        NProgress.done()
        return
      }

      next()
    } else {
      // 未登录场景
      if (whiteList.includes(to.path)) {
        next()
      } else {
        // 记录 redirect，登录成功后跳回原路径
        next(`/login?redirect=${to.path}`)
        NProgress.done()
      }
    }
  })

  // 后置守卫：关闭进度条 + 设置文档标题
  router.afterEach((to) => {
    NProgress.done()
    const title = (to.meta.title as string) || ''
    document.title = title ? `${title} - PMIS 运营管理系统` : 'PMIS 运营管理系统'
  })

  // 错误守卫：路由异常时关闭进度条，避免卡死
  router.onError((error) => {
    console.error('[Router Error]', error)
    NProgress.done()
  })
}
