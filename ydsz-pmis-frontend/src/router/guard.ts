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
import { useAppStore } from '@/store/modules/app'
import i18n from '@/locales'
import { logger } from '@/utils/logger'
import { recordAccess } from '@/api/favorite'

// 关闭右上角转圈动画，仅保留顶部进度条
NProgress.configure({ showSpinner: false })

/** 白名单路径：无需登录即可访问 */
const whiteList = ['/login', '/404', '/500']

/**
 * 解析路由标题：如果 title 是 i18n key（以 'route.' 开头）则通过 i18n 翻译，
 * 否则直接返回原始值
 */
function resolveRouteTitle(title: string | undefined): string {
  if (!title) return ''
  return title.startsWith('route.') ? i18n.global.t(title) : title
}

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
        ElMessage.error(i18n.global.t('common.noPermission', { title: resolveRouteTitle(to.meta?.title as string) }))
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

  // 后置守卫：关闭进度条 + 设置文档标题 + 记录最近访问 + 缓存视图
  router.afterEach((to) => {
    NProgress.done()
    const title = resolveRouteTitle(to.meta.title as string)
    const appTitle = i18n.global.t('common.appTitle')
    document.title = title ? `${title} - ${appTitle}` : appTitle

    // 记录最近访问：仅登录用户 + 有标题的业务路由（排除白名单页面）
    const userStore = useUserStore()
    if (userStore.token && title && !whiteList.includes(to.path)) {
      recordAccess(to.fullPath, title).catch(() => {
        // 记录失败静默忽略，不影响正常导航
      })
    }

    // 缓存视图：将标记了 keepAlive 的路由组件名加入缓存列表
    if (to.meta.keepAlive && to.name) {
      const appStore = useAppStore()
      appStore.addCachedView(to.name as string)
    }
  })

  // 错误守卫：路由异常时关闭进度条；chunk 加载失败跳 /500，避免白屏
  router.onError((error) => {
    logger.error('[Router]', error)
    NProgress.done()
    // 路由懒加载失败（chunk 缓存失效 / 部署中文件被删）→ 跳 500 兜底
    const msg = error?.message || ''
    if (/Loading chunk|Failed to fetch dynamically imported module|ChunkLoadError/i.test(msg)) {
      router.replace({ path: '/500' })
    }
  })
}
