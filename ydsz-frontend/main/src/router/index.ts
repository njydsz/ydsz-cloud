/**
 * 路由配置入口，聚合所有路由模块
 *
 * @path main\src\router\index.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import {
  createRouter,
  createWebHashHistory,
  createWebHistory,
} from 'vue-router';

import { resetStaticRoutes } from '@ydsz/utils';

import { createRouterGuard } from './guard';
import { routes } from './routes';

/**
 *  @zh_CN 创建vue-router实例
 */
const router = createRouter({
  history:
    import.meta.env.VITE_ROUTER_HISTORY === 'hash'
      ? createWebHashHistory(import.meta.env.VITE_BASE)
      : createWebHistory(import.meta.env.VITE_BASE),
  // 应该添加到路由的初始路由列表。
  routes,
  scrollBehavior: (to, _from, savedPosition) => {
    if (savedPosition) {
      return savedPosition;
    }
    return to.hash ? { behavior: 'smooth', el: to.hash } : { left: 0, top: 0 };
  },
  // 是否应该禁止尾部斜杠。
  // strict: true,
});

// 重置静态路由：清空动态注入的路由，便于权限变更后重新生成。
const resetRoutes = () => resetStaticRoutes(router, routes);

// 在 Pinia 初始化后调用此函数以创建路由守卫
let guardInitialized = false;

/**
 * 初始化路由守卫（在 Pinia 初始化后调用）
 */
function initRouterGuard() {
  if (!guardInitialized) {
    createRouterGuard(router);
    guardInitialized = true;
  }
}

export { initRouterGuard, resetRoutes, router };
